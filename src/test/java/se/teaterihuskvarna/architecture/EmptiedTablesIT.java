package se.teaterihuskvarna.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import se.teaterihuskvarna.IntegrationTestSupport;

/// Every table in the database is emptied before each test.
///
/// The integration tests share one database, and [IntegrationTestSupport]
/// empties it from a list. A table left off the list keeps its rows, and a
/// test then passes or fails by which test ran before it.
class EmptiedTablesIT extends IntegrationTestSupport {

    /// Flyway's own table, the administrators, of which the first is kept on
    /// purpose, and the table whose rows the database deletes with their
    /// session.
    private static final List<String> KEPT = List.of(
            "flyway_schema_history", "administrator", "spring_session_attributes");

    @Test
    void theListOfEmptiedTablesIsEveryTable() {
        List<String> tables = jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_type = 'BASE TABLE'""")
                .query(String.class)
                .list().stream().map(Objects::requireNonNull).toList();
        List<String> known = new ArrayList<>(EMPTIED);
        known.addAll(KEPT);

        assertThat(tables).containsExactlyInAnyOrderElementsOf(known);
    }

    /// What [IntegrationTestSupport#idsApart] is for: an account id that is
    /// also its member's id lets code that mixes the two up pass its tests.
    @Test
    void anAccountAndItsMemberHaveIdsAMillionApart() {
        long account = insertAccount("Erik Lindqvist", "erik@example.test");
        long member = jdbc.sql("SELECT member_id FROM account WHERE id = ?").param(account).query(Long.class)
                .single();

        assertThat(Math.abs(account - member)).isGreaterThan(900_000);
    }
}
