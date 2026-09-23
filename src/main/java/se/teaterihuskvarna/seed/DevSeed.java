package se.teaterihuskvarna.seed;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// Fills an empty development database with invented members, accounts and
/// administrators, so every page has something to show and every login path
/// has an address to try.
///
/// Invented data only. `docs/projektplan.md` says development and test
/// environments use invented data, so no name, address or phone number here
/// belongs to a real person. Every address is under `.test`, a top-level
/// domain RFC 2606 reserves for testing, so none of them can reach anyone.
///
/// Profile `dev` only. Runs after `FirstAdministrator` in the administrator
/// package has created the first administrator (order 0), so the others can
/// name that one as the administrator who added them. Does nothing once the
/// member table has a row, so restarting keeps whatever was changed by hand.
///
/// What the data covers:
///
/// - two households, one with a child member who has no account;
/// - members with and without an account, and one member with no household;
/// - Karin Holmberg, who has both an account and an administrator account on
///   the same address, which is the case the two login pages exist for;
/// - three administrators in total, so removing one works and removing a second
///   is refused.
///
/// Plain SQL through `JdbcClient` rather than the entities, because this package
/// is not the member package and the repositories are package private there.
@Component
@Profile("dev")
@Order(1)
class DevSeed implements ApplicationRunner {

    private final JdbcClient jdbc;

    DevSeed(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existing = jdbc.sql("SELECT count(*) FROM member").query(Long.class).single();
        if (existing > 0) {
            return;
        }

        long lindqvist = household("Familjen Lindqvist");
        long bergstrom = household("Familjen Bergström");

        long erik = member(lindqvist, "Erik Lindqvist", "070-174 06 01", "Exempelvägen 4", "561 31");
        long maria = member(lindqvist, "Maria Lindqvist", "070-174 06 02", "Exempelvägen 4", "561 31");
        // A child added to the household: a member without an account.
        member(lindqvist, "Olle Lindqvist", null, "Exempelvägen 4", "561 31");

        long johan = member(bergstrom, "Johan Bergström", "070-174 06 03", "Provgatan 12", "561 32");
        long sara = member(bergstrom, "Sara Bergström", null, "Provgatan 12", "561 32");

        long karin = member(null, "Karin Holmberg", "070-174 06 04", "Påhittade gränd 7", "561 33");
        long anders = member(null, "Anders Sjöberg", "070-174 06 05", "Låtsasvägen 2", "561 34");
        // An adult without an account.
        member(null, "Ingrid Nyström", "070-174 06 06", "Exempelvägen 9", "561 31");

        account(erik, "erik.lindqvist@example.test");
        account(maria, "maria.lindqvist@example.test");
        account(johan, "johan.bergstrom@example.test");
        account(sara, "sara.bergstrom@example.test");
        account(karin, "karin.holmberg@example.test");
        account(anders, "anders.sjoberg@example.test");

        Long first = jdbc.sql("SELECT id FROM administrator ORDER BY id LIMIT 1").query(Long.class).optional()
                .orElse(null);
        administrator("karin.holmberg@example.test", "Karin Holmberg", first);
        administrator("gunnar.wik@example.test", "Gunnar Wik", first);
    }

    private long household(String name) {
        return jdbc.sql("INSERT INTO household (name, created_at) VALUES (:name, now()) RETURNING id")
                .param("name", name)
                .query(Long.class)
                .single();
    }

    private long member(@Nullable Long household, String fullName, @Nullable String phone, String address,
            String postalCode) {
        return jdbc.sql("""
                INSERT INTO member (household_id, full_name, phone, address, postal_code, city, created_at)
                VALUES (:household, :fullName, :phone, :address, :postalCode, 'Huskvarna', now())
                RETURNING id
                """)
                .param("household", household)
                .param("fullName", fullName)
                .param("phone", phone)
                .param("address", address)
                .param("postalCode", postalCode)
                .query(Long.class)
                .single();
    }

    private void account(long member, String email) {
        jdbc.sql("INSERT INTO account (member_id, email, created_at) VALUES (:member, :email, now())")
                .param("member", member)
                .param("email", email)
                .update();
    }

    /// `ON CONFLICT DO NOTHING` because the first administrator comes from local
    /// configuration, and may already have one of these addresses.
    private void administrator(String email, String fullName, @Nullable Long createdBy) {
        jdbc.sql("""
                INSERT INTO administrator (email, full_name, created_at, created_by)
                VALUES (:email, :fullName, now(), :createdBy)
                ON CONFLICT DO NOTHING
                """)
                .param("email", email)
                .param("fullName", fullName)
                .param("createdBy", createdBy)
                .update();
    }
}
