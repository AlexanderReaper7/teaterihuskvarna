package se.teaterihuskvarna.member;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.jspecify.annotations.Nullable;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.SignedIn;

/// Rows and logins the member register tests share. Everything goes in through
/// SQL, so a test of one rule does not depend on the service it is not testing.
abstract class MemberRegisterSupport extends IntegrationTestSupport {

    /// @return this year in Sweden, the year the fee pages show
    protected static int thisYear() {
        return ZonedDateTime.now(ZoneId.of("Europe/Stockholm")).getYear();
    }

    /// @param name the household's name
    /// @return the new household's id
    protected long insertHousehold(String name) {
        return jdbc.sql("INSERT INTO household (name, created_at) VALUES (?, now()) RETURNING id")
                .param(name)
                .query(Long.class)
                .single();
    }

    /// @param memberId    the member to move
    /// @param householdId the household, or null for none
    protected void moveTo(long memberId, @Nullable Long householdId) {
        jdbc.sql("UPDATE member SET household_id = ? WHERE id = ?").param(householdId).param(memberId).update();
    }

    /// @param accountId an account
    /// @return the member the account belongs to
    protected long memberOf(long accountId) {
        return jdbc.sql("SELECT member_id FROM account WHERE id = ?").param(accountId).query(Long.class).single();
    }

    /// @param memberId the member who paid
    /// @param kind     `INDIVIDUAL` or `HOUSEHOLD`
    protected void insertFee(long memberId, String kind) {
        jdbc.sql("""
                INSERT INTO fee (member_id, year, kind, amount_ore, paid_at, household_id)
                SELECT id, ?, ?, 5000, now(), CASE WHEN ? = 'HOUSEHOLD' THEN household_id END
                FROM member WHERE id = ?
                """)
                .params(thisYear(), kind, kind, memberId)
                .update();
    }

    /// @param accountId a real account id, which the member chain's active login check looks up
    /// @param email     the account's address
    /// @return a logged-in member
    protected static RequestPostProcessor asMember(long accountId, String email) {
        return user(new SignedIn(LoginKind.MEMBER, accountId, email, "Medlem"));
    }

    /// @return the administrator the application seeded, logged in
    protected RequestPostProcessor asAdministrator() {
        return user(new SignedIn(LoginKind.ADMINISTRATOR, firstAdministratorId(), firstAdministratorEmail,
                "Ada Admin"));
    }
}
