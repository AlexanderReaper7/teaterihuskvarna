package se.teaterihuskvarna.member;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.login.LoginBrowser;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.LoginSettings;

/// Proves the membership application rules (R005) in `docs/projektplan.md`, through
/// both adapters, the JTE form and the REST endpoints:
///
/// - an application stores nothing in the register until its link is confirmed,
///   and the database keeps the SHA-256 of the confirmation token, not the token;
/// - confirming creates one member with one account and shows the payment
///   instruction with the configured bankgiro;
/// - a confirmation link works once, and not after it expires, and the mail
///   that carries it is not kept or sent after that;
/// - a link that would confirm nothing says so when it is opened, with no
///   button, and looking at a link uses nothing up;
/// - an unconfirmed application is deleted 24 hours after it was sent;
/// - the welcome page's login works once, without a mail, and only in the
///   browser that sent the form;
/// - an address that already has an account gets a mail pointing to the login
///   page instead, and the response is the same as for a new address;
/// - the form counts against the login page's rate limit, per address and per
///   client, and answers the same way over it, with no mail;
/// - invalid input is refused before anything is stored or mailed.
///
/// The service does the work after the rate limit on a background thread, so
/// every check of the database waits for the mail first. The mail goes out only
/// after that thread's transaction commits, so the rows are there by then.
class MembershipApplicationIT extends IntegrationTestSupport {

    private static final String CONFIRMATION = "/bli-medlem/bekrafta";
    private static final String CONFIRMABLE = "/api/membership-applications/confirmation";
    private static final String KARIN = "karin@example.test";
    private static final String ERIK = "erik@example.test";
    private static final Pattern WELCOME_LOGIN = Pattern.compile("href=\"/logga-in/lank\\?token=([A-Za-z0-9_-]{43})\"");

    @Value("${teaterihuskvarna.association.bankgiro}")
    private String bankgiro;

    @Autowired
    private ExpiredApplications expiredApplications;

    @Autowired
    private MemberRepository members;

    @Autowired
    private LoginSettings login;

    @Test
    void anApplicationMailsAConfirmationLinkAndStoresNoMember() throws Exception {
        MvcResult applied = apply("Karin Karlsson", KARIN);

        assertRedirect(applied, "/bli-medlem/skickat");
        SimpleMailMessage mail = awaitMail();
        assertThat(mail.getTo()).containsExactly(KARIN);
        String token = tokenIn(mail, CONFIRMATION);
        assertThat(rowsIn("member")).isZero();
        assertThat(rowsIn("account")).isZero();

        List<Map<String, @Nullable Object>> rows = jdbc.sql("SELECT * FROM membership_application")
                .query().listOfRows();
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

    /// The page used to show its button for any token at all, so a link that
    /// was used, expired or cut short asked the person to press a button that
    /// could only answer that the link does not work.
    @Test
    void aLinkThatWouldConfirmNothingSaysSoWhenItIsOpened() throws Exception {
        assertOpensExpired("not-a-token");

        apply("Karin Karlsson", KARIN);
        String expired = tokenIn(awaitMail(), CONFIRMATION);
        jdbc.sql("UPDATE membership_application SET expires_at = now() - INTERVAL '1 minute'").update();
        assertOpensExpired(expired);
        assertThat(rowsIn("membership_application")).as("a look deletes nothing").isEqualTo(1);

        forgetMails();
        apply("Karin Karlsson", KARIN);
        String used = tokenIn(awaitMail(), CONFIRMATION);
        mockMvc.perform(post(CONFIRMATION).param("token", used).with(csrf()))
                .andExpect(content().string(containsString(bankgiro)));
        assertOpensExpired(used);

        forgetMails();
        apply("Erik Eriksson", ERIK);
        String taken = tokenIn(awaitMail(), CONFIRMATION);
        insertAccount("Erik Eriksson", ERIK);
        assertOpensExpired(taken);
    }

    @Test
    void theApiTellsWhetherALinkWouldConfirmAndUsesNothingUp() throws Exception {
        mockMvc.perform(applyByApi("Karin Karlsson", KARIN)).andExpect(status().isAccepted());
        String token = tokenIn(awaitMail(), CONFIRMATION);

        mockMvc.perform(get(CONFIRMABLE).param("token", token)).andExpect(status().isNoContent());
        mockMvc.perform(get(CONFIRMABLE).param("token", token)).andExpect(status().isNoContent());
        mockMvc.perform(get(CONFIRMABLE).param("token", "not-a-token")).andExpect(status().isNotFound());
        mockMvc.perform(get(CONFIRMABLE)).andExpect(status().isNotFound());
        assertThat(rowsIn("member")).isZero();

        mockMvc.perform(confirmByApi(token)).andExpect(status().isOk());
        mockMvc.perform(get(CONFIRMABLE).param("token", token)).andExpect(status().isNotFound());
    }

    @Test
    void confirmingCreatesAMemberWithAnAccountAndShowsTheBankgiro() throws Exception {
        apply("Karin Karlsson", KARIN);
        String token = tokenIn(awaitMail(), CONFIRMATION);

        mockMvc.perform(post(CONFIRMATION).param("token", token).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Medlemsavgift " + Year.now(FeeLedger.SWEDEN).getValue())))
                .andExpect(content().string(containsString("<span class=\"badge badge-unpaid\">Ej betald</span>")))
                .andExpect(content().string(containsString(bankgiro)))
                .andExpect(content().string(containsString("Årsavgiften är 50 kr för en person eller 100 kr för ett "
                        + "hushåll. Betala till bankgiro " + bankgiro + " och skriv " + KARIN + " i meddelandet.")));
        mockMvc.perform(get("/bli-medlem"))
                .andExpect(content().string(containsString("kostar 50 kr per år för en person")));

        List<Map<String, @Nullable Object>> members = jdbc.sql("""
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

    /// Hibernate reads an embeddable whose columns are all null as null, and the
    /// member gives [ContactDetails#NONE] instead.
    @Test
    void aMemberWhoGaveOnlyNameAndEmailHasNoContactDetails() throws Exception {
        mockMvc.perform(post("/bli-medlem")
                        .param("fullName", "Karin Karlsson")
                        .param("email", KARIN)
                        .param("phone", " ")
                        .with(csrf()))
                .andReturn();
        String token = tokenIn(awaitMail(), CONFIRMATION);
        mockMvc.perform(post(CONFIRMATION).param("token", token).with(csrf())).andExpect(status().isOk());

        Map<String, @Nullable Object> row = jdbc.sql("SELECT id, phone, address, postal_code, city FROM member")
                .query().singleRow();
        assertThat(row).containsEntry("phone", null).containsEntry("address", null)
                .containsEntry("postal_code", null).containsEntry("city", null);
        long id = ((Number) requireNonNull(row.get("id"))).longValue();
        assertThat(members.findById(id).orElseThrow().getContact()).isEqualTo(ContactDetails.NONE);
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

    /// #47. The form sets the member login cookie, and the welcome page links
    /// to a login link bound to it, which no mail carries. Anywhere else the
    /// link page sends the person to the address form and leaves the link
    /// alone: `docs/decisions/0015`, "Logging in from the welcome page".
    @Test
    void theWelcomePageLogsInOnlyTheBrowserThatApplied() throws Exception {
        Cookie applicant = requireNonNull(apply("Karin Karlsson", KARIN).getResponse()
                .getCookie(LoginBrowser.COOKIE));
        assertThat(applicant.getPath()).isEqualTo("/logga-in");
        String token = tokenIn(awaitMail(), CONFIRMATION);
        forgetMails();
        String login = loginTokenIn(mockMvc.perform(post(CONFIRMATION).param("token", token).with(csrf()))
                .andReturn());

        assertRedirect(mockMvc.perform(get("/logga-in/lank").param("token", login)).andReturn(), "/logga-in");
        requestLink(LoginKind.MEMBER, ERIK);
        assertRedirect(mockMvc.perform(get("/logga-in/lank").param("token", login)
                .cookie(requireNonNull(browserCookie(LoginKind.MEMBER)))).andReturn(), "/logga-in");
        assertRedirect(followLink(LoginKind.MEMBER, login), "/logga-in?fel");
        mockMvc.perform(get("/api/login-links").param("kind", "MEMBER").param("token", login))
                .andExpect(jsonPath("$.opening").value("ELSEWHERE_WITHOUT_CODE"));

        mockMvc.perform(get("/logga-in/lank").param("token", login).cookie(applicant))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-auto-submit")));
        MvcResult loggedIn = mockMvc.perform(post("/logga-in/lank").param("token", login).cookie(applicant)
                .with(csrf())).andReturn();
        assertRedirect(loggedIn, "/medlem");
        mockMvc.perform(get("/api/member").with(sessionOf(loggedIn)))
                .andExpect(jsonPath("$.email").value(KARIN));
        assertRedirect(mockMvc.perform(post("/logga-in/lank").param("token", login).cookie(applicant)
                .with(csrf())).andReturn(), "/logga-in?fel");
        assertNoMail();
    }

    /// The link has no code, so a code typed in the applying browser finds
    /// nothing to try against, rather than failing on the missing code, and
    /// the link still works afterwards.
    @Test
    void aCodeTypedBesideTheWelcomePagesLoginLinkRedeemsNothing() throws Exception {
        Cookie applicant = requireNonNull(apply("Karin Karlsson", KARIN).getResponse()
                .getCookie(LoginBrowser.COOKIE));
        String token = tokenIn(awaitMail(), CONFIRMATION);
        String login = loginTokenIn(mockMvc.perform(post(CONFIRMATION).param("token", token).with(csrf()))
                .andReturn());

        assertRedirect(mockMvc.perform(post("/logga-in/lank").param("code", "000000").cookie(applicant)
                .with(csrf())).andReturn(), "/logga-in/skickat?fel");
        assertThat(jdbc.sql("SELECT code_tries FROM one_time_token").query(Integer.class).single()).isZero();
        assertRedirect(mockMvc.perform(post("/logga-in/lank").param("token", login).cookie(applicant)
                .with(csrf())).andReturn(), "/medlem");
    }

    /// An application sent before the form set a login cookie has no browser to
    /// bind a login to, so the welcome page links to the address form as it did.
    @Test
    void anApplicationWithoutALoginCookieConfirmsWithoutALogin() throws Exception {
        apply("Karin Karlsson", KARIN);
        String token = tokenIn(awaitMail(), CONFIRMATION);
        jdbc.sql("UPDATE membership_application SET browser_hash = NULL").update();

        mockMvc.perform(post(CONFIRMATION).param("token", token).with(csrf()))
                .andExpect(content().string(containsString("href=\"/logga-in\"")))
                .andExpect(content().string(not(containsString("/logga-in/lank"))));
        assertThat(rowsIn("one_time_token")).isZero();
        assertThat(rowsIn("account")).isEqualTo(1);
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

    @Test
    void theConfirmationMailIsNotSentAfterItsLinkExpires() throws Exception {
        refuseEveryMail();

        apply("Karin Karlsson", KARIN);
        awaitMails(1);

        assertThat(notAfterOfTheMailInTheOutbox())
                .isEqualTo(instantFrom("SELECT expires_at FROM membership_application"));
    }

    /// It carries no link that expires, only the login page's address, so it
    /// is retried until it is sent.
    @Test
    void theMailToAnAddressWithAnAccountIsRetriedUntilItIsSent() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        refuseEveryMail();

        apply("Karin Karlsson", KARIN);
        awaitMails(1);

        assertThat(notAfterOfTheMailInTheOutbox()).isNull();
    }

    /// The form must not reveal that an address is already a member's, so the
    /// response matches a new address's, login cookie included. The mail points
    /// to the login page and carries no login link: `docs/decisions/0015`,
    /// "Bli medlem from an address that already has an account".
    @Test
    void anAddressWithAnAccountIsPointedToTheLoginPage() throws Exception {
        insertAccount("Karin Karlsson", KARIN);

        MvcResult fresh = apply("Nils Nilsson", "nils@example.test");
        awaitMail();
        forgetMails();
        MvcResult existing = apply("Karin K", "Karin@Example.test");
        SimpleMailMessage mail = awaitMail();

        assertRedirect(existing, "/bli-medlem/skickat");
        assertThat(Visible.of(existing)).isEqualTo(Visible.of(fresh));
        assertThat(mail.getTo()).containsExactly(KARIN);
        assertThat(mail.getText())
                .doesNotContain(CONFIRMATION)
                .doesNotContain("token=")
                .contains(siteUrl + "/logga-in\n");
        assertThat(rowsIn("one_time_token")).isZero();
        assertThat(jdbc.sql("SELECT email FROM membership_application").query(String.class).list())
                .containsExactly("nils@example.test");
        assertThat(rowsIn("account")).isEqualTo(1);
    }

    /// Without the limit the form would mail anyone as often as it was posted,
    /// and would answer "is this address a member?" without end.
    @Test
    void applicationsOverTheAddressLimitGetTheSameResponseButNoMail() throws Exception {
        int limit = login.requestsPerAddressAndClient();

        MvcResult first = apply("Karin Karlsson", KARIN);
        for (int application = 1; application < limit + 2; application++) {
            assertThat(Visible.of(apply("Karin Karlsson", KARIN))).isEqualTo(Visible.of(first));
        }

        assertRedirect(first, "/bli-medlem/skickat");
        assertThat(awaitMails(limit)).allSatisfy(mail -> assertThat(mail.getTo()).containsExactly(KARIN));
    }

    /// One client, a new address each time, through the REST adapter.
    @Test
    void applicationsOverTheClientLimitAreAcceptedButSendNoMail() throws Exception {
        int limit = login.requestsPerClient();

        for (int application = 0; application < limit + 2; application++) {
            mockMvc.perform(applyByApi("Karin Karlsson", "karin" + application + "@example.test"))
                    .andExpect(status().isAccepted());
        }

        assertThat(awaitMails(limit)).hasSize(limit);
        assertThat(rowsIn("membership_application")).isEqualTo(limit);
    }

    /// The limit is the login page's own, not a second one beside it: requests
    /// for a login link use up what the form allows the same address.
    @Test
    void theFormSharesTheLoginPagesLimit() throws Exception {
        for (int request = 0; request < login.requestsPerAddressAndClient(); request++) {
            requestLink(LoginKind.MEMBER, KARIN);
        }

        MvcResult applied = apply("Karin Karlsson", KARIN);

        assertRedirect(applied, "/bli-medlem/skickat");
        assertNoMail();
        assertThat(rowsIn("membership_application")).isZero();
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
        Cookie client = requireNonNull(mockMvc.perform(applyByApi("Karin Karlsson", KARIN))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getCookie(LoginBrowser.COOKIE));
        String token = tokenIn(awaitMail(), CONFIRMATION);

        MvcResult confirmed = mockMvc.perform(confirmByApi(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Karin Karlsson"))
                .andExpect(jsonPath("$.email").value(KARIN))
                .andExpect(jsonPath("$.bankgiro").value(bankgiro))
                .andExpect(jsonPath("$.fee.year").value(Year.now(FeeLedger.SWEDEN).getValue()))
                .andExpect(jsonPath("$.fee.paidAt").doesNotExist())
                .andExpect(jsonPath("$.fee.payment.message").value(KARIN))
                .andReturn();
        mockMvc.perform(confirmByApi(token)).andExpect(status().isNotFound());

        assertThat(rowsIn("member")).isEqualTo(1);
        assertThat(rowsIn("account")).isEqualTo(1);
        String login = JsonPath.read(bodyOf(confirmed), "$.loginToken");
        assertRedirect(mockMvc.perform(post("/logga-in/lank").param("token", login).cookie(client).with(csrf()))
                .andReturn(), "/medlem");
    }

    @Test
    void theApiAnswers404ForAnUnknownToken() throws Exception {
        mockMvc.perform(confirmByApi("not-a-token")).andExpect(status().isNotFound());
    }

    /// The adapter checks this body itself, and answers as a service's constraints are answered.
    @Test
    void aConfirmationWithoutATokenIsRefusedInSwedish() throws Exception {
        mockMvc.perform(post("/api/membership-applications/confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"\"}")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.token")
                        .value("Länken kan ha gått ut eller redan använts. Fyll i ansökan igen."));
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

    /// @return the token of the login link on a welcome page
    private static String loginTokenIn(MvcResult welcome) throws Exception {
        Matcher found = WELCOME_LOGIN.matcher(bodyOf(welcome));
        assertThat(found.find()).as("a login link on the welcome page").isTrue();
        return found.group(1);
    }

    /// The expired page, and no form with the token in it to press.
    private void assertOpensExpired(String token) throws Exception {
        mockMvc.perform(get(CONFIRMATION).param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Länken fungerar inte")))
                .andExpect(content().string(not(containsString(token))));
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

    private static MockHttpServletRequestBuilder applyByApi(String fullName, String email) {
        return post("/api/membership-applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\": \"" + fullName + "\", \"email\": \"" + email + "\"}")
                .with(csrf());
    }

    private static MockHttpServletRequestBuilder confirmByApi(String token) {
        return post("/api/membership-applications/confirmation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}")
                .with(csrf());
    }
}
