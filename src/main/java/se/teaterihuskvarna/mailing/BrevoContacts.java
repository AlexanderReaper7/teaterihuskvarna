package se.teaterihuskvarna.mailing;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import se.teaterihuskvarna.member.FeeService;
import se.teaterihuskvarna.member.MemberService;
import se.teaterihuskvarna.member.Recipient;
import se.teaterihuskvarna.offer.OfferService;
import se.teaterihuskvarna.outbox.OutboxHandler;
import se.teaterihuskvarna.volunteer.ShiftService;

/// Keeps each member's Brevo contact in step with the register, on every
/// change: `docs/decisions/0026-outbox-and-brevo-contacts.md`.
///
/// [BrevoContactChanges] turns a `MemberChanged` into an outbox row carrying
/// only the member's id. The row is done here, later, by reading the
/// member as they are then, so several changes in a row send the latest state
/// each time, and a retry never sends something stale. A member who is gone,
/// or has no account and so no address, has their contact deleted.
///
/// Two rows for the same member could otherwise run at once, one reading the
/// member before a change and finishing after the other, and leave the older
/// state in Brevo. So each row first takes a lock on the member that lasts
/// until the outbox's transaction ends, and only then reads: the second row
/// waits, then reads what the first could not see.
@Component
class BrevoContacts implements OutboxHandler {

    static final String KIND = "brevo-contact";

    /// The first key of the two-key advisory lock, which keeps these locks
    /// apart from any other use of advisory locks.
    static final int LOCK_SPACE = 0x42_72_65_76;

    private final Brevo brevo;
    private final JdbcClient jdbc;
    private final MemberService members;
    private final FeeService fees;
    private final ShiftService shifts;
    private final OfferService offers;

    BrevoContacts(Brevo brevo, JdbcClient jdbc, MemberService members, FeeService fees, ShiftService shifts,
            OfferService offers) {
        this.brevo = brevo;
        this.jdbc = jdbc;
        this.members = members;
        this.fees = fees;
        this.shifts = shifts;
        this.offers = offers;
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public void handle(String payload) {
        long memberId = Long.parseLong(payload);
        jdbc.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(?, hashtext(?))) AS locked")
                .param(LOCK_SPACE)
                .param(payload)
                .query(Integer.class)
                .single();
        Optional<Recipient> found = members.recipient(memberId);
        if (found.isEmpty()) {
            brevo.deleteContact(memberId);
            return;
        }
        Recipient member = found.get();
        brevo.saveContact(new Brevo.Contact(memberId, member.email(), member.fullName(),
                fees.latestPaidYear(memberId), shifts.lastShift(memberId), offers.offerIdsOf(memberId)));
    }
}
