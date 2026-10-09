package se.teaterihuskvarna.member;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import se.teaterihuskvarna.IntegrationTestSupport;

/// Runs V14 over populated V13 tables in a separate PostgreSQL schema.
class HouseholdOwnerMigrationIT extends IntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Test
    void addingOwnershipPreservesExistingHouseholdsMembersAccountsAndFees() {
        try {
            migrate("13");
            long household = jdbc.sql("INSERT INTO household_owner_upgrade.household (name, created_at) "
                    + "VALUES ('Familjen', now()) RETURNING id").query(Long.class).single();
            long member = jdbc.sql("INSERT INTO household_owner_upgrade.member (full_name, household_id, created_at) "
                    + "VALUES ('Erik', ?, now()) RETURNING id").param(household).query(Long.class).single();
            jdbc.sql("INSERT INTO household_owner_upgrade.account (member_id, email, created_at) "
                    + "VALUES (?, 'erik@example.test', now())").param(member).update();
            jdbc.sql("INSERT INTO household_owner_upgrade.fee "
                    + "(member_id, household_id, year, kind, amount_ore, paid_at) "
                    + "VALUES (?, ?, 2026, 'HOUSEHOLD', 10000, now())").params(member, household).update();
            migrate("14");
            assertThat(jdbc.sql("SELECT h.name, h.owner_member_id, m.full_name, m.household_id, a.email, "
                    + "f.amount_ore FROM household_owner_upgrade.household h "
                    + "JOIN household_owner_upgrade.member m ON m.household_id = h.id "
                    + "JOIN household_owner_upgrade.account a ON a.member_id = m.id "
                    + "JOIN household_owner_upgrade.fee f ON f.household_id = h.id").query().singleRow())
                    .containsEntry("name", "Familjen").containsEntry("owner_member_id", null)
                    .containsEntry("full_name", "Erik").containsEntry("household_id", household)
                    .containsEntry("email", "erik@example.test").containsEntry("amount_ore", 10000);
            // The old application's insert still works with the expanded schema.
            jdbc.sql("INSERT INTO household_owner_upgrade.household (name, created_at) "
                    + "VALUES ('Tidigare programversion', now())").update();
        } finally {
            jdbc.sql("DROP SCHEMA IF EXISTS household_owner_upgrade CASCADE").update();
        }
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(dataSource).schemas("household_owner_upgrade")
                .defaultSchema("household_owner_upgrade").target(target).load().migrate();
    }
}
