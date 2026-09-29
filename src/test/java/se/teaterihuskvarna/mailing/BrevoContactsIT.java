package se.teaterihuskvarna.mailing;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.member.ContactForm;
import se.teaterihuskvarna.member.FeeKind;
import se.teaterihuskvarna.member.FeeMark;
import se.teaterihuskvarna.member.FeeService;
import se.teaterihuskvarna.member.MemberForm;
import se.teaterihuskvarna.member.MemberService;
import se.teaterihuskvarna.offer.OfferService;
import se.teaterihuskvarna.outbox.Outbox;
import se.teaterihuskvarna.volunteer.ShiftService;

/// Proves that the Brevo contacts follow the register on every change
/// (docs/decisions/0026-outbox-and-brevo-contacts.md), against [FakeBrevo].
///
/// - A member with an account becomes a contact on the list, keyed by the
///   member's id, and a member without one does not.
/// - A changed address moves the same contact, and a member's own name change
///   reaches it too.
/// - A household fee sets PAID_YEAR on everyone in the household, and undoing
///   it clears it.
/// - Registering for an offer sets OFFERS, and a shift sets LAST_SHIFT once it
///   has started.
/// - Deleting a member deletes the contact.
class BrevoContactsIT extends IntegrationTestSupport {

    private static final ZoneId SWEDEN = ZoneId.of("Europe/Stockholm");
    private static final long WAIT_MILLIS = 5000;

    @Autowired
    private Brevo brevo;

    @Autowired
    private MemberService members;

    @Autowired
    private FeeService fees;

    @Autowired
    private OfferService offers;

    @Autowired
    private ShiftService shifts;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private Outbox outbox;

    private FakeBrevo fake;

    @BeforeEach
    void fake() {
        fake = (FakeBrevo) brevo;
        fake.forgetContacts();
    }

    @Test
    void aMemberWithAnAccountBecomesAContactAndFollowsTheirAddress() {
        long karin = members.add(form("Karin Holm", "karin@example.test", null)).id();
        long without = members.add(form("Utan Konto", null, null)).id();

        Brevo.Contact contact = awaitContact(karin, c -> true);
        assertThat(contact.email()).isEqualTo("karin@example.test");
        assertThat(contact.name()).isEqualTo("Karin Holm");
        assertThat(contact.paidYear()).isNull();
        assertThat(contact.offers()).isEmpty();

        members.update(karin, form("Karin Holm", "karin.holm@example.test", null));
        awaitContact(karin, c -> c.email().equals("karin.holm@example.test"));

        long account = jdbc.sql("SELECT id FROM account WHERE member_id = ?").param(karin).query(Long.class).single();
        members.updateContact(account, new ContactForm("Karin Holm-Berg", null, null, null, null));
        awaitContact(karin, c -> c.name().equals("Karin Holm-Berg"));
        assertThat(fake.contacts()).doesNotContainKey(without);
    }

    @Test
    void aHouseholdFeeSetsThePaidYearOnTheWholeHousehold() {
        long household = jdbc.sql("INSERT INTO household (name, created_at) VALUES ('Familjen Holm', now()) "
                + "RETURNING id").query(Long.class).single();
        long karin = members.add(form("Karin Holm", "karin@example.test", household)).id();
        long nils = members.add(form("Nils Holm", "nils@example.test", household)).id();
        int year = LocalDate.now(SWEDEN).getYear();

        fees.markPaid(karin, new FeeMark(FeeKind.HOUSEHOLD, null), firstAdministratorId());
        awaitContact(karin, c -> Integer.valueOf(year).equals(c.paidYear()));
        awaitContact(nils, c -> Integer.valueOf(year).equals(c.paidYear()));

        fees.undo(karin);
        awaitContact(nils, c -> c.paidYear() == null);
    }

    @Test
    void anOfferAndAStartedShiftShowOnTheContact() {
        long karin = members.add(form("Karin Holm", "karin@example.test", null)).id();
        long offer = jdbc.sql("""
                INSERT INTO offer (title, description, published, created_at, updated_at)
                VALUES ('Verkstad', '', true, now(), now()) RETURNING id
                """).query(Long.class).single();

        offers.register(offer, karin);
        awaitContact(karin, c -> c.offers().equals(";" + offer + ";"));

        Instant started = Instant.now().minus(Duration.ofHours(2));
        long shift = jdbc.sql("""
                INSERT INTO volunteer_shift (event_id, event_title, event_slug, task, starts_at, ends_at, places,
                    created_at)
                VALUES ('evenemang-varshow', 'Vårshowen', 'varshowen', 'SERVERING', ?, ?, 5, now())
                RETURNING id
                """)
                .param(Timestamp.from(started))
                .param(Timestamp.from(started.plus(Duration.ofHours(3))))
                .query(Long.class)
                .single();
        jdbc.sql("INSERT INTO volunteer_booking (shift_id, member_id, created_at) VALUES (?, ?, now())")
                .param(shift).param(karin).update();

        assertThat(shifts.reportStartedShifts()).isOne();
        awaitContact(karin, c -> LocalDate.ofInstant(started, SWEDEN).equals(c.lastShift()));
        assertThat(shifts.reportStartedShifts()).isZero();
    }

    @Test
    void deletingAMemberDeletesTheContact() {
        long karin = members.add(form("Karin Holm", "karin@example.test", null)).id();
        awaitContact(karin, c -> true);

        members.delete(karin);
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (fake.contacts().containsKey(karin) && System.currentTimeMillis() < deadline) {
            pause();
        }
        assertThat(fake.contacts()).doesNotContainKey(karin);
    }

    /// While another transaction holds the member's lock, the row waits, and
    /// then sends the state as it is after the wait. Mail goes out meanwhile.
    @Test
    void anUpdateWaitsForTheMembersLock() throws Exception {
        long karin = members.add(form("Karin Holm", "karin@example.test", null)).id();
        awaitContact(karin, c -> true);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(?, hashtext(?))) AS locked")
                    .param(BrevoContacts.LOCK_SPACE).param(String.valueOf(karin)).query(Integer.class).single();
            locked.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        members.update(karin, form("Karin Berg", "karin@example.test", null));
        Thread.sleep(1000);
        assertThat(fake.contacts().get(karin).name()).isEqualTo("Karin Holm");

        // Mail has a thread of its own, so a stuck contact does not hold it back,
        // and neither do more stuck contacts than the retry job's batch, when
        // the job is what hands the mail over.
        for (int i = 0; i < 60; i++) {
            jdbc.sql("INSERT INTO outbox (kind, payload) VALUES ('brevo-contact', ?)")
                    .param(String.valueOf(karin)).update();
        }
        outbox.retry();
        jdbc.sql("""
                INSERT INTO outbox (kind, payload) VALUES ('mail',
                    '{"to":"nils@example.test","subject":"Hej","body":"Text"}')""").update();
        outbox.retry();
        assertThat(awaitMail().getTo()).containsExactly("nils@example.test");

        release.countDown();
        holder.join();
        awaitContact(karin, c -> c.name().equals("Karin Berg"));
    }

    private Brevo.Contact awaitContact(long memberId, Predicate<Brevo.Contact> until) {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        Brevo.Contact contact = fake.contacts().get(memberId);
        while ((contact == null || !until.test(contact)) && System.currentTimeMillis() < deadline) {
            pause();
            contact = fake.contacts().get(memberId);
        }
        assertThat(contact).as("the contact of member %d", memberId).isNotNull().matches(until::test);
        return contact;
    }

    private static void pause() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static MemberForm form(String name, @Nullable String email, @Nullable Long household) {
        return new MemberForm(name, email, null, null, null, null, household);
    }
}
