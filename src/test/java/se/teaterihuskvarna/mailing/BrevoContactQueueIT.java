package se.teaterihuskvarna.mailing;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.member.MemberId;

/// The database queues a member's Brevo contact, with no Java code asking:
/// `V17__brevo_contact_queue.sql`. Every write here is SQL, so no service
/// could have queued it. `BrevoContactsIT` proves the same contacts through
/// the services.
class BrevoContactQueueIT extends IntegrationTestSupport {

    private static final long WAIT_MILLIS = 5000;

    @Autowired
    private Brevo brevo;

    @Autowired
    private BrevoContacts contacts;

    @Autowired
    private PlatformTransactionManager transactions;

    private FakeBrevo fake;

    @BeforeEach
    void fake() {
        fake = (FakeBrevo) brevo;
        fake.forgetContacts();
    }

    /// The gate for a new source. [BrevoContacts] handles a member who has
    /// everything a contact shows, and PostgreSQL's own count of scans per
    /// table, read on the same connection before and after, says which tables
    /// it read. Each of them must have a trigger that queues the contact on
    /// insert, update and delete. So a contact that starts to read a new
    /// table fails here until the migration queues it from that table too.
    @Test
    void everyTableTheContactIsReadFromQueuesIt() {
        long karin = memberWithEverything();

        Set<String> read = new TreeSet<>();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Map<String, Long> before = scans();
            contacts.handle(String.valueOf(karin));
            scans().forEach((table, count) -> {
                if (count > before.getOrDefault(table, 0L)) {
                    read.add(table);
                }
            });
        });
        Map<String, Set<String>> events = new TreeMap<>();
        jdbc.sql("""
                        SELECT event_object_table, event_manipulation FROM information_schema.triggers
                        WHERE trigger_schema = 'public' AND action_timing = 'AFTER' AND action_orientation = 'ROW'
                          AND action_statement LIKE 'EXECUTE FUNCTION brevo_contact_of_%'""")
                .query((row, number) -> events.computeIfAbsent(row.getString(1), table -> new TreeSet<>())
                        .add(row.getString(2)))
                .list();
        Set<String> queueing = events.entrySet().stream()
                .filter(table -> table.getValue().containsAll(Set.of("INSERT", "UPDATE", "DELETE")))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(read).as("the tables BrevoContacts read, so the test cannot pass by reading none")
                .contains("member", "account", "fee", "offer_registration", "volunteer_booking");
        assertThat(queueing)
                .as("tables with a brevo_contact trigger for insert, update and delete in V17__brevo_contact_queue.sql")
                .containsAll(read);
    }

    @Test
    void aMemberWrittenInSqlBecomesAContactAndFollowsEveryChange() {
        long karin = insertMemberWithAccount("Karin Holm", "karin@example.test");
        assertThat(awaitContact(karin, contact -> true).email()).isEqualTo("karin@example.test");

        jdbc.sql("UPDATE member SET full_name = 'Karin Berg' WHERE id = ?").param(karin).update();
        awaitContact(karin, contact -> contact.name().equals("Karin Berg"));

        jdbc.sql("UPDATE account SET email = 'karin.berg@example.test' WHERE member_id = ?").param(karin).update();
        awaitContact(karin, contact -> contact.email().equals("karin.berg@example.test"));

        jdbc.sql("DELETE FROM member WHERE id = ?").param(karin).update();
        await(() -> !fake.contacts().containsKey(new MemberId(karin)), () -> "no contact for the deleted member");
    }

    /// The fee names the household, so it covers Nils, who paid nothing and
    /// whose own rows did not change.
    @Test
    void aHouseholdFeeWrittenInSqlReachesEveryoneInTheHousehold() {
        long household = insertHousehold();
        long karin = insertMemberWithAccount("Karin Holm", "karin@example.test");
        long nils = insertMemberWithAccount("Nils Holm", "nils@example.test");
        jdbc.sql("UPDATE member SET household_id = ? WHERE id IN (?, ?)")
                .param(household).param(karin).param(nils).update();
        awaitContact(nils, contact -> contact.paidYear() == null);

        jdbc.sql("""
                        INSERT INTO fee (member_id, year, kind, amount_ore, paid_at, household_id)
                        VALUES (?, 2026, 'HOUSEHOLD', 10000, now(), ?)""")
                .param(karin).param(household).update();
        awaitContact(nils, contact -> Integer.valueOf(2026).equals(contact.paidYear()));

        jdbc.sql("DELETE FROM fee").update();
        awaitContact(nils, contact -> contact.paidYear() == null);
    }

    /// Nothing deletes the registration and the booking by name: the foreign
    /// keys do, when the offer and the shift go.
    @Test
    void aRowTheForeignKeyDeletesQueuesItsMember() {
        long karin = insertMemberWithAccount("Karin Holm", "karin@example.test");
        long offer = insertOffer();
        long shift = insertStartedShift();
        jdbc.sql("INSERT INTO offer_registration (offer_id, member_id, created_at) VALUES (?, ?, now())")
                .param(offer).param(karin).update();
        jdbc.sql("INSERT INTO volunteer_booking (shift_id, member_id, created_at) VALUES (?, ?, now())")
                .param(shift).param(karin).update();
        awaitContact(karin, contact -> contact.offers().equals(";" + offer + ";") && contact.lastShift() != null);

        jdbc.sql("DELETE FROM offer WHERE id = ?").param(offer).update();
        jdbc.sql("DELETE FROM volunteer_shift WHERE id = ?").param(shift).update();

        awaitContact(karin, contact -> contact.offers().isEmpty() && contact.lastShift() == null);
    }

    /// A member's LAST_SHIFT changes when a shift starts, with nothing
    /// written. `ShiftService.reportStartedShifts` then sets
    /// `start_reported`, and that write is what queues everyone booked.
    @Test
    void aShiftMarkedAsStartedQueuesEveryoneBookedOnIt() {
        long karin = insertMemberWithAccount("Karin Holm", "karin@example.test");
        long nils = insertMemberWithAccount("Nils Holm", "nils@example.test");
        long other = insertMemberWithAccount("Ingen Bokning", "ingen@example.test");
        long shift = insertStartedShift();
        for (long member : new long[] {karin, nils}) {
            jdbc.sql("INSERT INTO volunteer_booking (shift_id, member_id, created_at) VALUES (?, ?, now())")
                    .param(shift).param(member).update();
        }

        Set<String> queued = queuedBy(() ->
                jdbc.sql("UPDATE volunteer_shift SET start_reported = TRUE WHERE id = ?").param(shift).update());

        assertThat(queued).containsExactlyInAnyOrder(String.valueOf(karin), String.valueOf(nils))
                .doesNotContain(String.valueOf(other));
    }

    /// Three writes about one member in one transaction are one call to Brevo.
    @Test
    void aTransactionQueuesAMemberOnce() {
        long karin = insertMemberWithAccount("Karin Holm", "karin@example.test");
        awaitContact(karin, contact -> true);
        long offer = insertOffer();

        long queued = requireNonNull(new TransactionTemplate(transactions).execute(status -> {
            jdbc.sql("UPDATE member SET full_name = 'Karin Berg' WHERE id = ?").param(karin).update();
            jdbc.sql("UPDATE account SET email = 'karin.berg@example.test' WHERE member_id = ?").param(karin).update();
            jdbc.sql("INSERT INTO offer_registration (offer_id, member_id, created_at) VALUES (?, ?, now())")
                    .param(offer).param(karin).update();
            return jdbc.sql("SELECT COUNT(*) FROM outbox WHERE kind = 'brevo-contact' AND payload = ?")
                    .param(String.valueOf(karin))
                    .query(Long.class)
                    .single();
        }));

        assertThat(queued).isOne();
        awaitContact(karin, contact -> contact.name().equals("Karin Berg")
                && contact.email().equals("karin.berg@example.test") && contact.offers().equals(";" + offer + ";"));
    }

    /// What the writes queue, read inside their transaction, which is then
    /// rolled back, so nothing is done about the rows and nothing races the
    /// read.
    private Set<String> queuedBy(Runnable writes) {
        List<String> payloads = requireNonNull(new TransactionTemplate(transactions).execute(status -> {
            status.setRollbackOnly();
            writes.run();
            return jdbc.sql("""
                            SELECT payload FROM outbox
                            WHERE kind = 'brevo-contact' AND xmin = pg_current_xact_id()::xid""")
                    .query(String.class)
                    .list().stream().map(Objects::requireNonNull).toList();
        }));
        return new TreeSet<>(payloads);
    }

    /// How often this connection has scanned each table and has not yet
    /// reported it to the statistics all connections share, which it does
    /// between transactions. So the difference between two reads inside one
    /// transaction is what that transaction scanned in between.
    private Map<String, Long> scans() {
        Map<String, Long> scans = new TreeMap<>();
        jdbc.sql("""
                        SELECT relname, COALESCE(seq_scan, 0) + COALESCE(idx_scan, 0) FROM pg_stat_xact_user_tables
                        WHERE schemaname = 'public'""")
                .query((row, number) -> scans.put(row.getString(1), row.getLong(2)))
                .list();
        return scans;
    }

    /// A member in a household that has paid, registered for an offer and
    /// booked on a shift that has started, so that no query for the contact
    /// comes back empty before it reaches a table.
    private long memberWithEverything() {
        long household = insertHousehold();
        long karin = insertMemberWithAccount("Karin Holm", "karin@example.test");
        jdbc.sql("UPDATE member SET household_id = ? WHERE id = ?").param(household).param(karin).update();
        jdbc.sql("""
                        INSERT INTO fee (member_id, year, kind, amount_ore, paid_at, household_id)
                        VALUES (?, 2026, 'HOUSEHOLD', 10000, now(), ?)""")
                .param(karin).param(household).update();
        jdbc.sql("INSERT INTO offer_registration (offer_id, member_id, created_at) VALUES (?, ?, now())")
                .param(insertOffer()).param(karin).update();
        jdbc.sql("INSERT INTO volunteer_booking (shift_id, member_id, created_at) VALUES (?, ?, now())")
                .param(insertStartedShift()).param(karin).update();
        return karin;
    }

    private long insertMemberWithAccount(String fullName, String email) {
        long account = insertAccount(fullName, email);
        return jdbc.sql("SELECT member_id FROM account WHERE id = ?").param(account).query(Long.class).single();
    }

    private long insertHousehold() {
        return jdbc.sql("INSERT INTO household (name, created_at) VALUES ('Familjen Holm', now()) RETURNING id")
                .query(Long.class)
                .single();
    }

    private long insertOffer() {
        return jdbc.sql("""
                        INSERT INTO offer (title, description, published, created_at, updated_at)
                        VALUES ('Verkstad', '', true, now(), now()) RETURNING id""")
                .query(Long.class)
                .single();
    }

    private long insertStartedShift() {
        return jdbc.sql("""
                        INSERT INTO volunteer_shift (event_id, event_title, event_slug, task, starts_at, ends_at,
                            places, created_at)
                        VALUES ('evenemang-varshow', 'Vårshowen', 'varshowen', 'SERVERING',
                            now() - interval '2 hours', now() + interval '1 hour', 5, now())
                        RETURNING id""")
                .query(Long.class)
                .single();
    }

    private Brevo.Contact awaitContact(long member, Predicate<Brevo.Contact> until) {
        MemberId memberId = new MemberId(member);
        await(() -> {
            Brevo.Contact contact = fake.contacts().get(memberId);
            return contact != null && until.test(contact);
        }, () -> "the awaited contact of member " + memberId + ", which is " + fake.contacts().get(memberId));
        return requireNonNull(fake.contacts().get(memberId));
    }

    private static void await(BooleanSupplier done, Supplier<String> what) {
        long deadline = System.nanoTime() + WAIT_MILLIS * 1_000_000;
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        assertThat(done.getAsBoolean()).as(what).isTrue();
    }
}
