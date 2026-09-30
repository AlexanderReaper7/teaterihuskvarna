package se.teaterihuskvarna.mailing;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import se.teaterihuskvarna.member.MemberChanged;
import se.teaterihuskvarna.outbox.Outbox;

/// Queues a [BrevoContacts] update for each [MemberChanged]. A plain listener
/// runs on the publisher's thread, in its transaction, so the outbox row
/// commits with the change or not at all. A class of its own, because the
/// [Outbox] needs every handler, [BrevoContacts] included, to exist first.
@Component
class BrevoContactChanges {

    private final Outbox outbox;

    BrevoContactChanges(Outbox outbox) {
        this.outbox = outbox;
    }

    /// @param changed the member whose contact to send again
    @EventListener
    public void changed(MemberChanged changed) {
        outbox.add(BrevoContacts.KIND, String.valueOf(changed.memberId()));
    }
}
