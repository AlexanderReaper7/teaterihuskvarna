package se.teaterihuskvarna.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import se.teaterihuskvarna.IntegrationTestSupport;

/// Runs V1 to V3 against the same PostgreSQL image production runs, which is the
/// point of pinning one image in `docs/decisions/0007-postgres-in-a-container.md`.
///
/// This class starting at all is itself the check that `ddl-auto: validate`
/// passed: a missing table or column stops the context before any test method
/// runs.
///
/// The constraint tests use raw SQL on purpose. They assert the database
/// constraints themselves, which have to hold against anything that writes, not
/// only against JPA.
///
/// Needs a docker daemon, so it runs through compose or CI rather than inside the
/// image build: `docs/decisions/0011-maven-and-the-build-in-a-container.md`.
class MemberRegisterSchemaIT extends IntegrationTestSupport {

    @Autowired
    private MemberRepository members;

    @Test
    void migrationsCreateEveryTable() {
        List<@Nullable String> tables = jdbc.sql(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
                .query(String.class)
                .list();

        assertThat(tables).contains(
                "flyway_schema_history",
                "household",
                "member",
                "account",
                "administrator",
                "membership_application",
                "one_time_token",
                "link_request",
                "spring_session",
                "spring_session_attributes");
    }

    @Test
    void aMemberSurvivesARoundTripThroughJpa() {
        Member saved = members.save(new Member("Karin Karlsson"));

        Optional<Member> found = members.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Karin Karlsson");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void twoAccountsCannotShareAnAddressInAnyCase() {
        long anna = insertMember("Anna Andersson");
        long other = insertMember("Anna A");
        insertAccountRow(anna, "anna@example.test");

        assertThatThrownBy(() -> insertAccountRow(other, "ANNA@example.test"))
                .hasMessageContaining("account_email_key");
    }

    @Test
    void aMemberHasAtMostOneAccount() {
        long anna = insertMember("Anna Andersson");
        insertAccountRow(anna, "anna@example.test");

        assertThatThrownBy(() -> insertAccountRow(anna, "anna.andersson@example.test"))
                .hasMessageContaining("account_member_id_key");
    }

    @Test
    void twoAdministratorAccountsCannotShareAnAddressInAnyCase() {
        insertAdministrator("bo@example.test", "Bo Berg");

        assertThatThrownBy(() -> insertAdministrator("Bo@Example.test", "Bo B"))
                .hasMessageContaining("administrator_email_key");
    }

    /// `created_at` has no default. The entity supplies it, so SQL must too.
    private void insertAccountRow(long memberId, String email) {
        jdbc.sql("INSERT INTO account (member_id, email, created_at) VALUES (?, ?, now())")
                .param(memberId)
                .param(email)
                .update();
    }
}
