package se.teaterihuskvarna.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import se.teaterihuskvarna.member.Member;
import se.teaterihuskvarna.member.MemberRepository;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/// Runs V1 against the same PostgreSQL image production runs, which is the point
/// of pinning one image in `docs/decisions/0007-postgres-in-a-container.md`.
///
/// This class starting at all is itself the check that `ddl-auto: validate`
/// passed: a missing table or column stops the context before any test method
/// runs.
///
/// Needs a docker daemon, so it runs through compose or CI rather than inside the
/// image build: `docs/decisions/0011-maven-and-the-build-in-a-container.md`.
@Testcontainers
@SpringBootTest
class MemberRegisterSchemaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.2-alpine");

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MemberRepository members;

    @Test
    void migrationCreatesTheMemberRegister() {
        List<String> tables = jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
                .query(String.class)
                .list();

        assertThat(tables).contains("member", "household", "flyway_schema_history");
    }

    @Test
    void aMemberSurvivesARoundTripThroughJpa() {
        Member saved = members.save(new Member("Karin Karlsson", "karin@example.test"));

        Optional<Member> found = members.findByEmailIgnoreCase("KARIN@EXAMPLE.TEST");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getFullName()).isEqualTo("Karin Karlsson");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void twoMembersCannotShareAnEmailAddress() {
        // Raw SQL on purpose: this asserts the database constraint itself, which
        // has to hold against anything that writes, not only against JPA.
        // created_at has no default; the entity supplies it, so SQL must too.
        jdbc.sql("INSERT INTO member (full_name, email, created_at)"
                        + " VALUES ('Anna Andersson', 'anna@example.test', now())")
                .update();

        assertThatThrownBy(() -> jdbc
                .sql("INSERT INTO member (full_name, email, created_at) VALUES ('Anna A', 'ANNA@example.test', now())")
                .update())
                .hasMessageContaining("member_email_key");
    }
}
