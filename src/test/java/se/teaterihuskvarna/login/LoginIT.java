package se.teaterihuskvarna.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import se.teaterihuskvarna.IntegrationTestSupport;

/// Proves the login rules in `docs/projektplan.md`, "Member login must not reveal
/// membership", through HTTP against the real filter chains, with sessions in
/// PostgreSQL and the mail sender mocked:
///
/// - a known and an unknown address get the same response, and only the known
///   one gets a mail, on both login pages;
/// - each login page looks an address up among its own kind only, and a link
///   works only on the page of its own kind;
/// - the database holds the SHA-256 of a token, never the token;
/// - a link expires after the configured lifetime, one hour by default;
/// - opening a link logs nobody in, pressing its button does, and only once;
/// - a link and its code work only in the browser that asked for them, and a
///   code allows five tries;
/// - the rate limits per address and per client stop the mail but not the
///   response, which stays the same, and hold for requests sent at once;
/// - expired links, and client addresses older than the window, are deleted;
/// - member and administrator routes deny access unless a rule grants it, and
///   the REST adapter answers 401 rather than redirecting.
///
/// What this cannot prove is that the response time is the same for a known
/// and an unknown address. `Mailer` sends on a background thread for that
/// reason; a test of it would be a timing measurement, which is too noisy to
/// fail for the right reason.
class LoginIT extends IntegrationTestSupport {

    private static final String KARIN = "karin@example.test";
    private static final String NOBODY = "nobody@example.test";

    @Autowired
    private LoginSettings settings;

    @Autowired
    private LinkRequestLimiter limiter;

    @Autowired
    private LoginCleanup cleanup;

    @Test
    void knownAndUnknownAddressesGetTheSameResponse() throws Exception {
        insertAccount("Karin Karlsson", KARIN);

        MvcResult unknown = requestLink(LoginKind.MEMBER, NOBODY);
        assertNoMail();
        assertThat(rowsIn("one_time_token")).isZero();

        MvcResult known = requestLink(LoginKind.MEMBER, KARIN);
        SimpleMailMessage mail = awaitMail();

        assertRedirect(known, "/logga-in/skickat");
        assertThat(Visible.of(known)).isEqualTo(Visible.of(unknown));
        assertThat(mail.getTo()).containsExactly(KARIN);
        tokenIn(mail, linkPath(LoginKind.MEMBER));
    }

    @Test
    void anAddressMatchesInAnyCase() throws Exception {
        insertAccount("Karin Karlsson", KARIN);

        requestLink(LoginKind.MEMBER, "  Karin@Example.TEST ");

        assertThat(awaitMail().getTo()).containsExactly(KARIN);
    }

    /// The link must point at the configured site. Built from the request's Host
    /// header instead, a forged header would put another host in a real login link.
    @Test
    void theLinkIgnoresTheHostHeader() throws Exception {
        insertAccount("Karin Karlsson", KARIN);

        mockMvc.perform(post("/logga-in").param("email", KARIN).header("Host", "attacker.example").with(csrf()));

        SimpleMailMessage mail = awaitMail();
        assertThat(mail.getText()).doesNotContain("attacker.example");
        tokenIn(mail, linkPath(LoginKind.MEMBER));
    }

    /// The code is six digits, too short to search the row for, so the test
    /// checks the column holds its hash instead.
    @Test
    void theDatabaseKeepsOnlyTheHashOfALoginLink() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        requestLink(LoginKind.MEMBER, KARIN);
        SimpleMailMessage mail = awaitMail();
        String token = tokenIn(mail, linkPath(LoginKind.MEMBER));
        String browser = browserCookie(LoginKind.MEMBER).getValue();

        List<Map<String, Object>> rows = jdbc.sql("SELECT * FROM one_time_token").query().listOfRows();

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("token_hash")).isEqualTo(sha256Hex(token));
        assertThat(rows.getFirst().get("kind")).isEqualTo("member");
        assertThat(rows.getFirst().get("browser_hash")).isEqualTo(sha256Hex(browser));
        assertThat(rows.getFirst().get("code_hash")).isEqualTo(sha256Hex(codeIn(mail)));
        assertThat(rows.getFirst().values()).allSatisfy(value -> assertThat(String.valueOf(value))
                .doesNotContain(token)
                .doesNotContain(browser));
    }

    /// Every request for a link gets the cookie, known address or not, so the
    /// response does not tell them apart. A browser that asks again keeps its
    /// value, so its older links keep working.
    @Test
    void askingForALinkSetsTheBrowserCookie() throws Exception {
        MvcResult first = requestLink(LoginKind.MEMBER, NOBODY);
        String setCookie = first.getResponse().getHeader("Set-Cookie");
        MvcResult again = requestLink(LoginKind.MEMBER, NOBODY);

        assertThat(setCookie)
                .startsWith(LoginBrowser.COOKIE + "=")
                .contains("Path=/logga-in")
                .contains("Max-Age=" + settings.linkLifetime().toSeconds())
                .contains("HttpOnly")
                .contains("SameSite=Lax");
        assertThat(again.getResponse().getCookie(LoginBrowser.COOKIE).getValue())
                .isEqualTo(first.getResponse().getCookie(LoginBrowser.COOKIE).getValue());
        assertThat(requestLink(LoginKind.ADMINISTRATOR, NOBODY).getResponse().getHeader("Set-Cookie"))
                .contains("Path=/admin/logga-in");
    }

    /// The attack this stops: someone asks for a link to their own address and
    /// sends it to someone else, who would then be logged in as the sender
    /// without noticing. The refused attempt leaves the link alone, so it still
    /// works where it was asked for.
    @Test
    void aLinkWorksOnlyInTheBrowserThatAskedForIt() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        requestLink(LoginKind.MEMBER, KARIN);
        String token = tokenIn(awaitMail(), linkPath(LoginKind.MEMBER));

        MvcResult noCookie = mockMvc.perform(post("/logga-in/lank").param("token", token).with(csrf())).andReturn();
        MvcResult otherBrowser = mockMvc.perform(post("/logga-in/lank").param("token", token).with(csrf())
                .cookie(new Cookie(LoginBrowser.COOKIE, Tokens.newToken()))).andReturn();

        assertRedirect(noCookie, "/logga-in?fel");
        assertRedirect(otherBrowser, "/logga-in?fel");
        assertThat(signedInSessions()).isEmpty();
        assertRedirect(followLink(LoginKind.MEMBER, token), "/medlem");
    }

    /// Opened in another browser, the link page offers no button, which would
    /// only fail, and says to type the code where the link was asked for.
    @Test
    void aLinkOpenedInAnotherBrowserSaysToUseTheCode() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        requestLink(LoginKind.MEMBER, KARIN);
        String token = tokenIn(awaitMail(), linkPath(LoginKind.MEMBER));

        mockMvc.perform(get("/logga-in/lank").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Länken fungerar bara i webbläsaren där du bad om den")))
                .andExpect(content().string(containsString("href=\"/logga-in\"")))
                .andExpect(content().string(not(containsString(token))))
                .andExpect(content().string(not(containsString("data-auto-submit"))));
        assertRedirect(mockMvc.perform(get("/logga-in/lank").param("token", "not-a-token")).andReturn(),
                "/logga-in?fel");
        assertThat(rowsIn("one_time_token")).isEqualTo(1);
    }

    /// The API answers what the link page decides, and uses nothing up.
    @Test
    void theApiSaysWhereALinkWorks() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        requestLink(LoginKind.MEMBER, KARIN);
        String token = tokenIn(awaitMail(), linkPath(LoginKind.MEMBER));
        Cookie here = browserCookie(LoginKind.MEMBER);
        assertThat(here).isNotNull();

        mockMvc.perform(get("/api/login-links").param("kind", "MEMBER").param("token", token).cookie(here))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opening").value("HERE"));
        mockMvc.perform(get("/api/login-links").param("kind", "MEMBER").param("token", token))
                .andExpect(jsonPath("$.opening").value("ELSEWHERE"));
        mockMvc.perform(get("/api/login-links").param("kind", "ADMINISTRATOR").param("token", token).cookie(here))
                .andExpect(jsonPath("$.opening").value("UNUSABLE"));
        mockMvc.perform(get("/api/login-links").param("kind", "MEMBER").param("token", "not-a-token"))
                .andExpect(jsonPath("$.opening").value("UNUSABLE"));
        assertThat(rowsIn("one_time_token")).isEqualTo(1);
    }

    @Test
    void theMailCarriesACodeThatLogsIn() throws Exception {
        long account = insertAccount("Karin Karlsson", KARIN);
        requestLink(LoginKind.MEMBER, KARIN);
        SimpleMailMessage mail = awaitMail();
        String code = codeIn(mail);

        MvcResult login = enterCode(LoginKind.MEMBER, code.substring(0, 3) + " " + code.substring(3));

        assertThat(mail.getText()).contains("Ge aldrig koden till någon. Föreningen frågar aldrig efter den.");
        assertRedirect(login, "/medlem");
        mockMvc.perform(get("/medlem").with(sessionOf(login))).andExpect(status().isOk());
        assertThat(signedInSessions()).containsExactly(LoginKind.MEMBER.principalName(account));
        assertThat(rowsIn("one_time_token")).isZero();
    }

    @Test
    void aCodeWorksOnlyInTheBrowserThatAskedForIt() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        requestLink(LoginKind.MEMBER, KARIN);
        String code = codeIn(awaitMail());

        MvcResult otherBrowser = mockMvc.perform(post("/logga-in/lank").param("code", code).with(csrf())
                .cookie(new Cookie(LoginBrowser.COOKIE, Tokens.newToken()))).andReturn();

        assertRedirect(otherBrowser, "/logga-in/skickat?fel");
        assertThat(signedInSessions()).isEmpty();
        assertRedirect(enterCode(LoginKind.MEMBER, code), "/medlem");
    }

    /// Five wrong codes stop the code, and the link carries on working: someone
    /// guessing codes from their own browser cannot spend a link they do not hold.
    @Test
    void aCodeAllowsFiveTriesAndTheLinkOutlastsThem() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        requestLink(LoginKind.MEMBER, KARIN);
        SimpleMailMessage mail = awaitMail();
        String code = codeIn(mail);
        String wrong = String.format(Locale.ROOT, "%06d", (Integer.parseInt(code) + 1) % 1_000_000);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertRedirect(enterCode(LoginKind.MEMBER, wrong), "/logga-in/skickat?fel");
        }
        MvcResult sixth = enterCode(LoginKind.MEMBER, code);

        assertRedirect(sixth, "/logga-in/skickat?fel");
        mockMvc.perform(get("/logga-in/skickat").param("fel", ""))
                .andExpect(content().string(containsString("Koden fungerade inte")));
        mockMvc.perform(get("/logga-in/skickat"))
                .andExpect(content().string(not(containsString("Koden fungerade inte"))));
        assertRedirect(followLink(LoginKind.MEMBER, tokenIn(mail, linkPath(LoginKind.MEMBER))), "/medlem");
    }

    /// Guesses sent at the same moment count one another, as link requests do.
    @Test
    void codeGuessesSentAtOnceStillStopAtFive() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        requestLink(LoginKind.MEMBER, KARIN);
        String code = codeIn(awaitMail());
        String wrong = String.format(Locale.ROOT, "%06d", (Integer.parseInt(code) + 1) % 1_000_000);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> results = new ArrayList<>();
        try (ExecutorService threads = Executors.newFixedThreadPool(20)) {
            for (int guess = 0; guess < 20; guess++) {
                results.add(threads.submit(() -> {
                    start.await();
                    return enterCode(LoginKind.MEMBER, wrong);
                }));
            }
            start.countDown();
            for (Future<MvcResult> result : results) {
                assertRedirect(result.get(), "/logga-in/skickat?fel");
            }
        }

        assertThat(jdbc.sql("SELECT code_tries FROM one_time_token").query(Integer.class).single()).isEqualTo(5);
        assertRedirect(enterCode(LoginKind.MEMBER, code), "/logga-in/skickat?fel");
    }

    @Test
    void aLinkLastsTheConfiguredLifetimeWhichIsOneHourByDefault() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        requestLink(LoginKind.MEMBER, KARIN);
        awaitMail();

        double secondsLeft = jdbc.sql("SELECT EXTRACT(EPOCH FROM expires_at - now()) FROM one_time_token")
                .query(Double.class)
                .single();

        assertThat(settings.linkLifetime()).isEqualTo(Duration.ofHours(1));
        long lifetime = settings.linkLifetime().toSeconds();
        assertThat(secondsLeft).isBetween(lifetime - 60.0, lifetime + 5.0);
    }

    /// A mail scanner that fetches every link must not use up or redeem the
    /// token. Only the POST from the page's form logs in, which a script on the
    /// page sends at once in a browser.
    @Test
    void openingALinkDoesNotLogIn() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        requestLink(LoginKind.MEMBER, KARIN);
        String token = tokenIn(awaitMail(), linkPath(LoginKind.MEMBER));

        MvcResult page = mockMvc.perform(get("/logga-in/lank").param("token", token)
                        .cookie(browserCookie(LoginKind.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(token)))
                .andExpect(content().string(containsString("data-auto-submit")))
                .andExpect(content().string(containsString("src=\"/js/login-link.js\"")))
                .andReturn();

        assertRedirect(mockMvc.perform(get("/medlem").with(sessionOf(page))).andReturn(), "/logga-in");
        assertThat(rowsIn("one_time_token")).isEqualTo(1);
        assertRedirect(followLink(LoginKind.MEMBER, token), "/medlem");
    }

    @Test
    void aLinkLogsInOnceOnly() throws Exception {
        long account = insertAccount("Karin Karlsson", KARIN);
        requestLink(LoginKind.MEMBER, KARIN);
        String token = tokenIn(awaitMail(), linkPath(LoginKind.MEMBER));

        MvcResult first = followLink(LoginKind.MEMBER, token);
        MvcResult second = followLink(LoginKind.MEMBER, token);

        assertRedirect(first, "/medlem");
        mockMvc.perform(get("/medlem").with(sessionOf(first))).andExpect(status().isOk());
        mockMvc.perform(get("/api/member").with(sessionOf(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(KARIN))
                .andExpect(jsonPath("$.fullName").value("Karin Karlsson"));
        assertThat(signedInSessions())
                .containsExactly(LoginKind.MEMBER.principalName(account));

        assertRedirect(second, "/logga-in?fel");
        assertRedirect(mockMvc.perform(get("/medlem").with(sessionOf(second))).andReturn(), "/logga-in");
        assertThat(rowsIn("one_time_token")).isZero();
    }

    @Test
    void anExpiredLinkDoesNotLogIn() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        requestLink(LoginKind.MEMBER, KARIN);
        String token = tokenIn(awaitMail(), linkPath(LoginKind.MEMBER));
        jdbc.sql("UPDATE one_time_token SET expires_at = now() - INTERVAL '1 minute'").update();

        MvcResult login = followLink(LoginKind.MEMBER, token);

        assertRedirect(login, "/logga-in?fel");
        assertRedirect(mockMvc.perform(get("/medlem").with(sessionOf(login))).andReturn(), "/logga-in");
    }

    @Test
    void anUnknownTokenDoesNotLogIn() throws Exception {
        assertRedirect(followLink(LoginKind.MEMBER, "not-a-token"), "/logga-in?fel");
        assertRedirect(followLink(LoginKind.ADMINISTRATOR, "not-a-token"), "/admin/logga-in?fel");
    }

    @Test
    void loggingOutEndsTheSession() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult login = logInByLink(LoginKind.MEMBER, KARIN);

        MvcResult logout = mockMvc.perform(post("/logga-ut").with(sessionOf(login)).with(csrf())).andReturn();

        assertRedirect(logout, "/");
        assertRedirect(mockMvc.perform(get("/medlem").with(sessionOf(login))).andReturn(), "/logga-in");
    }

    @Test
    void theMemberPageIgnoresAdministratorAddresses() throws Exception {
        MvcResult administrator = requestLink(LoginKind.MEMBER, firstAdministratorEmail);
        MvcResult nobody = requestLink(LoginKind.MEMBER, NOBODY);

        assertRedirect(administrator, "/logga-in/skickat");
        assertThat(Visible.of(administrator)).isEqualTo(Visible.of(nobody));
        assertNoMail();
    }

    @Test
    void theAdministratorPageIgnoresMemberAddresses() throws Exception {
        insertAccount("Karin Karlsson", KARIN);

        MvcResult member = requestLink(LoginKind.ADMINISTRATOR, KARIN);
        MvcResult nobody = requestLink(LoginKind.ADMINISTRATOR, NOBODY);

        assertRedirect(member, "/admin/logga-in/skickat");
        assertThat(Visible.of(member)).isEqualTo(Visible.of(nobody));
        assertNoMail();
    }

    @Test
    void anAdministratorLinkLogsInToTheAdministration() throws Exception {
        long administrator = insertAdministrator("bo@example.test", "Bo Berg");

        requestLink(LoginKind.ADMINISTRATOR, "bo@example.test");
        SimpleMailMessage mail = awaitMail();
        MvcResult login = followLink(LoginKind.ADMINISTRATOR, tokenIn(mail, linkPath(LoginKind.ADMINISTRATOR)));

        assertThat(mail.getTo()).containsExactly("bo@example.test");
        assertRedirect(login, "/admin");
        mockMvc.perform(get("/admin").with(sessionOf(login))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/administrators").with(sessionOf(login))).andExpect(status().isOk());
        assertThat(signedInSessions())
                .containsExactly(LoginKind.ADMINISTRATOR.principalName(administrator));
    }

    /// One address may be both an account and an administrator account. Each page
    /// sends its own kind of link, and a link does nothing on the other page. The
    /// failed attempt leaves the token alone, so it still works where it belongs.
    @Test
    void aLinkWorksOnlyOnThePageOfItsKind() throws Exception {
        String dana = "dana@example.test";
        insertAccount("Dana Dahl", dana);
        insertAdministrator(dana, "Dana Dahl");

        requestLink(LoginKind.MEMBER, dana);
        String memberToken = tokenIn(awaitMail(), linkPath(LoginKind.MEMBER));
        forgetMails();
        requestLink(LoginKind.ADMINISTRATOR, dana);
        String administratorToken = tokenIn(awaitMail(), linkPath(LoginKind.ADMINISTRATOR));

        assertRedirect(followLink(LoginKind.ADMINISTRATOR, memberToken), "/admin/logga-in?fel");
        assertRedirect(followLink(LoginKind.MEMBER, administratorToken), "/logga-in?fel");
        assertRedirect(followLink(LoginKind.MEMBER, memberToken), "/medlem");
        assertRedirect(followLink(LoginKind.ADMINISTRATOR, administratorToken), "/admin");
    }

    @Test
    void aMemberCannotReachTheAdministration() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult login = logInByLink(LoginKind.MEMBER, KARIN);

        assertRedirect(mockMvc.perform(get("/admin").with(sessionOf(login))).andReturn(), "/admin/logga-in");
        mockMvc.perform(get("/api/admin/administrators").with(sessionOf(login))).andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorIsNotAMember() throws Exception {
        MvcResult login = logInByLink(LoginKind.ADMINISTRATOR, firstAdministratorEmail);

        assertRedirect(mockMvc.perform(get("/medlem").with(sessionOf(login))).andReturn(), "/logga-in");
        mockMvc.perform(get("/api/member").with(sessionOf(login))).andExpect(status().isForbidden());
    }

    @Test
    void requestsOverTheAddressLimitGetTheSameResponseButNoMail() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        int limit = settings.requestsPerAddress();

        MvcResult first = requestLink(LoginKind.MEMBER, KARIN);
        for (int request = 1; request < limit + 2; request++) {
            MvcResult next = requestLink(LoginKind.MEMBER, KARIN);
            assertThat(next.getResponse().getStatus()).isEqualTo(first.getResponse().getStatus());
            assertThat(locationOf(next)).isEqualTo(locationOf(first));
        }

        assertRedirect(first, "/logga-in/skickat");
        assertThat(awaitMails(limit)).allSatisfy(mail -> assertThat(mail.getTo()).containsExactly(KARIN));
    }

    /// Requests sent at the same moment count one another. Each would otherwise
    /// count before any of them had recorded itself, and all would pass.
    @Test
    void requestsSentAtOnceStillStopAtTheAddressLimit() throws Exception {
        int requests = settings.requestsPerAddress() * 4;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService threads = Executors.newFixedThreadPool(requests)) {
            for (int request = 0; request < requests; request++) {
                String client = "10.0.0." + request;
                results.add(threads.submit(() -> {
                    start.await();
                    return limiter.tryAcquire(KARIN, client);
                }));
            }
            start.countDown();
            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    allowed++;
                }
            }

            assertThat(allowed).isEqualTo(settings.requestsPerAddress());
        }
    }

    /// `docs/projektplan.md`: "the IP addresses are deleted along with expired
    /// tokens". [LoginCleanup] deletes links past their expiry, and requests
    /// older than the rate limit's window with their client addresses, and
    /// keeps the rest.
    @Test
    void theCleanupDeletesExpiredLinksAndOldClientAddresses() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime windowStart = now.minus(settings.requestWindow());
        jdbc.sql("INSERT INTO one_time_token (token_hash, kind, email, expires_at, browser_hash, code_hash) VALUES "
                + "('expired', 'member', ?, ?, 'b', 'c'), ('live', 'member', ?, ?, 'b', 'c')")
                .params(KARIN, now.minusSeconds(1), KARIN, now.plusHours(1))
                .update();
        jdbc.sql("INSERT INTO link_request (email, client_address, requested_at) VALUES (?, ?, ?), (?, ?, ?)")
                .params(KARIN, "10.0.0.1", windowStart.minusSeconds(1), KARIN, "10.0.0.2", windowStart.plusMinutes(1))
                .update();

        cleanup.deleteExpired();

        assertThat(jdbc.sql("SELECT token_hash FROM one_time_token").query(String.class).list())
                .containsExactly("live");
        assertThat(jdbc.sql("SELECT client_address FROM link_request").query(String.class).list())
                .containsExactly("10.0.0.2");
    }

    @Test
    void requestsOverTheClientLimitGetTheSameResponseButNoMail() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        for (int request = 0; request < settings.requestsPerClient(); request++) {
            requestLink(LoginKind.MEMBER, "nobody" + request + "@example.test");
        }

        MvcResult known = requestLink(LoginKind.MEMBER, KARIN);

        assertRedirect(known, "/logga-in/skickat");
        assertNoMail();
    }

    /// Without a token, as from a page left open until its session was gone,
    /// the form goes back to the page it came from, marked `gammal` so the page
    /// asks for it again. Without a `Referer` naming this host, the login page
    /// stands in.
    @Test
    void askingForALinkNeedsTheCsrfToken() throws Exception {
        insertAccount("Karin Karlsson", KARIN);

        MvcResult bare = mockMvc.perform(post("/logga-in").param("email", KARIN)).andReturn();
        MvcResult fromPage = mockMvc.perform(post("/logga-in").param("email", KARIN)
                .header("Referer", "http://localhost/logga-in?fel")).andReturn();
        MvcResult fromElsewhere = mockMvc.perform(post("/logga-in").param("email", KARIN)
                .header("Referer", "http://elsewhere.test/logga-in")).andReturn();
        MvcResult twoSlashes = mockMvc.perform(post("/logga-in").param("email", KARIN)
                .header("Referer", "http://localhost//elsewhere.test/")).andReturn();

        assertRedirect(bare, "/logga-in?gammal");
        assertRedirect(fromPage, "/logga-in?fel&gammal");
        assertRedirect(fromElsewhere, "/logga-in?gammal");
        // A browser reads a Location starting with // as another host.
        assertThat(twoSlashes.getResponse().getRedirectedUrl()).startsWith("/").doesNotStartWith("//");
        mockMvc.perform(get("/logga-in").param("gammal", ""))
                .andExpect(content().string(containsString("Sidan hade stått öppen för länge")));
        mockMvc.perform(get("/logga-in"))
                .andExpect(content().string(not(containsString("Sidan hade stått öppen för länge"))));
        assertNoMail();
    }

    /// A login moves the session to a new row rather than renaming the old one.
    /// A request that loaded the session before the login and saves it after,
    /// such as a font on the link page, then finds no row to write the old id
    /// back into, and the login holds.
    @Test
    void loggingInMovesTheSessionToANewRow() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        requestLink(LoginKind.MEMBER, KARIN);
        String token = tokenIn(awaitMail(), linkPath(LoginKind.MEMBER));
        jdbc.sql("DELETE FROM spring_session").update();
        MvcResult page = mockMvc.perform(get("/logga-in/lank").param("token", token)
                .cookie(browserCookie(LoginKind.MEMBER))).andReturn();
        List<String> before = jdbc.sql("SELECT primary_id FROM spring_session").query(String.class).list();

        MvcResult login = mockMvc.perform(post("/logga-in/lank").param("token", token)
                .cookie(browserCookie(LoginKind.MEMBER)).with(sessionOf(page)).with(csrf())).andReturn();

        assertRedirect(login, "/medlem");
        assertThat(before).hasSize(1);
        assertThat(jdbc.sql("SELECT primary_id FROM spring_session").query(String.class).list())
                .hasSize(1)
                .doesNotContainAnyElementsOf(before);
        mockMvc.perform(get("/medlem").with(sessionOf(login))).andExpect(status().isOk());
    }

    @Test
    void thePublicPagesArePublic() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/logga-in")).andExpect(status().isOk());
        mockMvc.perform(get("/logga-in/skickat")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/logga-in")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/logga-in/skickat")).andExpect(status().isOk());
        mockMvc.perform(get("/bli-medlem")).andExpect(status().isOk());
        mockMvc.perform(get("/bli-medlem/skickat")).andExpect(status().isOk());
    }

    /// Security permits `/dev` and `/api/development/**` in every profile, so this is what
    /// keeps the route list and the login addresses out of production: without
    /// the dev profile nothing is mapped there, and `/` is the start page.
    @Test
    void theDevelopmentIndexExistsOnlyUnderTheDevProfile() throws Exception {
        mockMvc.perform(get("/dev")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/development/routes")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/development/login-accounts")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/development/environment")).andExpect(status().isNotFound());
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nästa evenemang")));
    }

    /// The files every page links to load without a session, and only those: the
    /// provenance README beside them in `static/` stays denied, which answers 404.
    @Test
    void theStaticFilesArePublic() throws Exception {
        mockMvc.perform(get("/css/site.css")).andExpect(status().isOk());
        mockMvc.perform(get("/fonts/montserrat-latin-normal.woff2")).andExpect(status().isOk());
        mockMvc.perform(get("/img/logo-negative.png")).andExpect(status().isOk());
        mockMvc.perform(get("/img/favicon.png")).andExpect(status().isOk());
        mockMvc.perform(get("/js/login-link.js")).andExpect(status().isOk());
        mockMvc.perform(get("/README.md")).andExpect(status().isNotFound());
    }

    @Test
    void theMemberAndAdministratorRoutesNeedALogin() throws Exception {
        mockMvc.perform(get("/api/member")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/administrators")).andExpect(status().isUnauthorized());
        assertRedirect(mockMvc.perform(get("/medlem")).andReturn(), "/logga-in");
        assertRedirect(mockMvc.perform(get("/admin")).andReturn(), "/admin/logga-in");
    }

    /// Fails closed: a path no rule mentions is denied, not passed on to a
    /// controller that might one day exist there, and the refusal is a 404
    /// whoever asks. `static/README.md` is a real file no rule grants, so a
    /// request that got past the rules would get it with 200. The body of the
    /// 404, the error page or nothing under `/api/`, is `ErrorPageIT`'s and the
    /// e2e suite's, because MockMvc does not follow `sendError`.
    ///
    /// `/admin/**` is a rule, so an unknown path there asks an anonymous visitor
    /// to log in, and an administrator gets Spring MVC's own 404.
    @Test
    void aPathNoRuleMentionsIsNotFound() throws Exception {
        for (String path : List.of("/finns-inte", "/README.md", "/api/finns-inte")) {
            assertThat(mockMvc.perform(get(path)).andReturn().getResponse().getStatus()).as(path).isEqualTo(404);
        }
        assertRedirect(mockMvc.perform(get("/admin/finns-inte")).andReturn(), "/admin/logga-in");

        insertAccount("Karin Karlsson", KARIN);
        RequestPostProcessor member = sessionOf(logInByLink(LoginKind.MEMBER, KARIN));
        RequestPostProcessor administrator = sessionOf(logInByLink(LoginKind.ADMINISTRATOR, firstAdministratorEmail));
        for (RequestPostProcessor session : List.of(member, administrator)) {
            for (String path : List.of("/finns-inte", "/README.md", "/api/finns-inte")) {
                assertThat(mockMvc.perform(get(path).with(session)).andReturn().getResponse().getStatus())
                        .as(path).isEqualTo(404);
            }
        }
        assertThat(mockMvc.perform(get("/admin/finns-inte").with(administrator)).andReturn().getResponse()
                .getStatus()).isEqualTo(404);
    }

    /// A refused request from someone without a session leaves no session
    /// behind. Spring's request cache saved each one in a new session, to go
    /// back to after login, and nothing read it.
    @Test
    void aRefusedVisitorGetsNoSession() throws Exception {
        for (String path : List.of("/finns-inte", "/medlem", "/admin", "/api/member")) {
            mockMvc.perform(get(path).accept(MediaType.TEXT_HTML));
        }

        assertThat(rowsIn("spring_session")).isZero();
    }

    /// A failed login stores its error in a new, anonymous session, so only
    /// sessions with a principal count.
    private List<String> signedInSessions() {
        return jdbc.sql("SELECT principal_name FROM spring_session WHERE principal_name IS NOT NULL")
                .query(String.class)
                .list();
    }
}
