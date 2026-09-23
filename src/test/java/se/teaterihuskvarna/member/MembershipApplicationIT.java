package se.teaterihuskvarna.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.login.LoginKind;

/// Proves the membership application rules (P5) in `docs/projektplan.md`, through
/// both adapters, the JTE form and the REST endpoints:
///
/// - an application stores nothing in the register until its link is confirmed,
///   and the database keeps the SHA-256 of the confirmation token, not the token;
/// - confirming creates one member with one account and shows the payment
///   instruction with the configured bankgiro;
/// - a confirmation link works once, and not after it expires;
/// - an unconfirmed application is deleted 24 hours after it was sent;
/// - an address that already has an account gets a login link instead, and the
///   response is the same as for a new address;
/// - invalid input is refused before anything is stored or mailed.
///
/// The service does the work after the rate limit on a background thread, so
/// every check of the database waits for the mail first. The mail goes out only
/// after that thread's transaction commits, so the rows are there by then.
class MembershipApplicationIT extends IntegrationTestSupport {

    private static final String CONFIRMATION = "/bli-medlem/bekrafta";
    private static final String KARIN = "karin@example.test";
    private static final String ERIK = "erik@example.test";

    @Value("${teaterihuskvarna.association.bankgiro}")
    private String bankgiro;

    @Autowired
    private ExpiredApplications expiredApplications;

    @Test
    void anApplicationMailsAConfirmationLinkAndStoresNoMember() throws Exception {
        MvcResult applied = apply("Karin Karlsson", KARIN);

        assertRedirect(applied, "/bli-medlem/skickat");
        SimpleMailMessage mail = awaitMail();
        assertThat(mail.getTo()).containsExactly(KARIN);
        String token = tokenIn(mail, CONFIRMATION);
        assertThat(rowsIn("member")).isZero();
        assertThat(rowsIn("account")).isZero();

        List<Map<String, Object>> rows = jdbc.sql("SELECT * FROM membership_application").query().listOfRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("token_hash")).isEqualTo(sha256Hex(token));
        assertThat(rows.getFirst().values())
                .allSatisfy(value -> assertThat(String.valueOf(value)).doesNotContain(token));
    }

    /// A mail scanner that fetches the link must not confirm the application.
    @Test
    void openingAConfirmationLinkConfirmsNothing() throws Exception {
        apply("Karin Karlsson", KARIN);
        String token = tokenIn(awaitMail(), CONFIRMATION);

        mockMvc.perform(get(CONFIRMATION).param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(token)));

        assertThat(rowsIn("member")).isZero();
        assertThat(rowsIn("membership_application")).isEqualTo(1);
    }

    @Test
    void confirmingCreatesAMemberWithAnAccountAndShowsTheBankgiro() throws Exception {
        apply("Karin Karlsson", KARIN);
        String token = tokenIn(awaitMail(), CONFIRMATION);

        mockMvc.perform(post(CONFIRMATION).param("token", token).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(bankgiro)));

        List<Map<String, Object>> members = jdbc.sql("""
                SELECT m.full_name, m.phone, m.address, m.postal_code, m.city, a.email
                FROM member m JOIN account a ON a.member_id = m.id
                """).query().listOfRows();
        assertThat(members).containsExactly(Map.of(
                "full_name", "Karin Karlsson",
                "phone", "070-123 45 67",
                "address", "Storgatan 1",
                "postal_code", "561 31",
                "city", "Huskvarna",
                "email", KARIN));
        assertThat(rowsIn("membership_application")).isZero();
    }

    /// The account a confirmation creates is a real one: its address can log in.
    @Test
    void theNewMemberCanLogIn() throws Exception {
        apply("Karin Karlsson", KARIN);
        String token = tokenIn(awaitMail(), CONFIRMATION);
        mockMvc.perform(post(CONFIRMATION).param("token", token).with(csrf())).andExpect(status().isOk());

        MvcResult login = logInByLink(LoginKind.MEMBER, KARIN);

        mockMvc.perform(get("/api/member").with(sessionOf(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Karin Karlsson"))
                .andExpect(jsonPath("$.email").value(KARIN));
    }

    @Test
    void aConfirmationLinkWorksOnce() throws Exception {
        apply("Karin Karlsson", KARIN);
        String token = tokenIn(awaitMail(), CONFIRMATION);
        mockMvc.perform(post(CONFIRMATION).param("token", token).with(csrf())).andExpect(status().isOk());

        MvcResult second = mockMvc.perform(post(CONFIRMATION).param("token", token).with(csrf()))
                .andExpect(content().string(not(containsString(bankgiro))))
                .andReturn();

        assertThat(second.getResponse().getStatus()).isLessThan(500);
        assertThat(rowsIn("member")).isEqualTo(1);
    }

    @Test
    void anExpiredConfirmationLinkCreatesNoMember() throws Exception {
        apply("Karin Karlsson", KARIN);
        String token = tokenIn(awaitMail(), CONFIRMATION);
        jdbc.sql("UPDATE membership_application SET expires_at = now() - INTERVAL '1 minute'").update();

        MvcResult confirmed = mockMvc.perform(post(CONFIRMATION).param("token", token).with(csrf()))
                .andExpect(content().string(not(containsString(bankgiro))))
                .andReturn();

        assertThat(confirmed.getResponse().getStatus()).isLessThan(500);
        assertThat(rowsIn("member")).isZero();
        assertThat(rowsIn("account")).isZero();
    }

    /// `docs/projektplan.md`: "An application nobody confirms is deleted after 24
    /// hours." The row expires then, and [ExpiredApplications] deletes what has
    /// expired and nothing else.
    @Test
    void anUnconfirmedApplicationIsDeletedAfter24Hours() throws Exception {
        apply("Karin Karlsson", KARIN);
        awaitMail();
        forgetMails();
        apply("Erik Eriksson", ERIK);
        awaitMail();
        long keptSeconds = jdbc.sql("""
                SELECT CAST(EXTRACT(EPOCH FROM expires_at - created_at) AS BIGINT)
                FROM membership_application WHERE email = ?""")
                .param(KARIN)
                .query(Long.class)
                .single();
        jdbc.sql("UPDATE membership_application SET expires_at = now() - INTERVAL '1 second' WHERE email = ?")
                .param(KARIN)
                .update();

        expiredApplications.delete();

        assertThat(Duration.ofSeconds(keptSeconds)).isEqualTo(Duration.ofHours(24));
        assertThat(jdbc.sql("SELECT email FROM membership_application").query(String.class).list())
                .containsExactly(ERIK);
    }

    /// The form must not reveal that an address is already a member's, so the
    /// response matches a new address's, and the mail is a login link rather than
    /// a second account.
    @Test
    void anAddressWithAnAccountGetsALoginLinkInstead() throws Exception {
        insertAccount("Karin Karlsson", KARIN);

        MvcResult fresh = apply("Nils Nilsson", "nils@example.test");
        awaitMail();
        forgetMails();
        MvcResult existing = apply("Karin K", "Karin@Example.test");
        SimpleMailMessage mail = awaitMail();

        assertRedirect(existing, "/bli-medlem/skickat");
        assertThat(Visible.of(existing)).isEqualTo(Visible.of(fresh));
        assertThat(mail.getTo()).containsExactly(KARIN);
        assertThat(mail.getText()).doesNotContain(CONFIRMATION);
        String token = tokenIn(mail, linkPath(LoginKind.MEMBER));
        assertThat(jdbc.sql("SELECT email FROM membership_application").query(String.class).list())
                .containsExactly("nils@example.test");
        assertThat(rowsIn("account")).isEqualTo(1);
        assertRedirect(followLink(LoginKind.MEMBER, token), "/medlem");
    }

    @Test
    void aBlankNameRerendersTheFormAndSendsNothing() throws Exception {
        mockMvc.perform(post("/bli-medlem").param("fullName", " ").param("email", KARIN).with(csrf()))
                .andExpect(status().isOk());

        assertNoMail();
        assertThat(rowsIn("membership_application")).isZero();
        assertThat(rowsIn("link_request")).isZero();
    }

    @Test
    void theApiAcceptsAndConfirmsAnApplication() throws Exception {
        mockMvc.perform(post("/api/membership-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Karin Karlsson\", \"email\": \"" + KARIN + "\"}")
                        .with(csrf()))
                .andExpect(status().isAccepted());
        String token = tokenIn(awaitMail(), CONFIRMATION);

        mockMvc.perform(confirmByApi(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Karin Karlsson"))
                .andExpect(jsonPath("$.email").value(KARIN))
                .andExpect(jsonPath("$.bankgiro").value(bankgiro));
        mockMvc.perform(confirmByApi(token)).andExpect(status().isNotFound());

        assertThat(rowsIn("member")).isEqualTo(1);
        assertThat(rowsIn("account")).isEqualTo(1);
    }

    @Test
    void theApiAnswers404ForAnUnknownToken() throws Exception {
        mockMvc.perform(confirmByApi("not-a-token")).andExpect(status().isNotFound());
    }

    @Test
    void theApiRefusesABlankName() throws Exception {
        mockMvc.perform(post("/api/membership-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"\", \"email\": \"" + KARIN + "\"}")
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        assertNoMail();
        assertThat(rowsIn("membership_application")).isZero();
    }

    @Test
    void theApiNeedsTheCsrfToken() throws Exception {
        mockMvc.perform(post("/api/membership-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Karin Karlsson\", \"email\": \"" + KARIN + "\"}"))
                .andExpect(status().isForbidden());

        assertNoMail();
    }

    private MvcResult apply(String fullName, String email) throws Exception {
        return mockMvc.perform(post("/bli-medlem")
                        .param("fullName", fullName)
                        .param("email", email)
                        .param("phone", "070-123 45 67")
                        .param("address", "Storgatan 1")
                        .param("postalCode", "561 31")
                        .param("city", "Huskvarna")
                        .with(csrf()))
                .andReturn();
    }

    private static MockHttpServletRequestBuilder confirmByApi(String token) {
        return post("/api/membership-applications/confirmation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}")
                .with(csrf());
    }
}
