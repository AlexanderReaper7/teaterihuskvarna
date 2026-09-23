package se.teaterihuskvarna.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.web.servlet.MvcResult;
import se.teaterihuskvarna.IntegrationTestSupport;

/// A login lasts a fixed time from when it starts, can be extended from the
/// page, and is listed with the person's other logins, each of which they can
/// end. Through HTTP against the real filter chains, with sessions in
/// PostgreSQL. What the page's script does with the answers is in
/// `e2e/tests/devices.spec.ts`.
class DeviceIT extends IntegrationTestSupport {

    private static final String KARIN = "karin@example.test";
    private static final String ERIK = "erik@example.test";
    private static final String FIREFOX_ON_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0";
    private static final String SAFARI_ON_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1";

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessions;

    @Autowired
    private LoginSettings settings;

    @Test
    void aLoginEndsAFixedTimeAfterItStartsHoweverOftenItIsUsed() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        Instant before = Instant.now();
        MvcResult login = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);
        Instant after = Instant.now();
        Instant endsAt = endsAt(sessionId(login));

        assertThat(endsAt).isBetween(before.plus(Duration.ofDays(30)), after.plus(Duration.ofDays(30)));
        mockMvc.perform(get("/medlem").with(sessionOf(login))).andExpect(status().isOk());
        mockMvc.perform(get("/api/member").with(sessionOf(login))).andExpect(status().isOk());
        assertThat(endsAt(sessionId(login))).isEqualTo(endsAt);
    }

    @Test
    void anAdministratorLoginLastsEightHours() throws Exception {
        Instant before = Instant.now();
        MvcResult login = logIn(LoginKind.ADMINISTRATOR, firstAdministratorEmail, FIREFOX_ON_WINDOWS);

        assertThat(endsAt(sessionId(login)))
                .isBetween(before.plus(Duration.ofHours(8)), Instant.now().plus(Duration.ofHours(8)));
    }

    @Test
    void aLoginPastItsEndIsLoggedOutAndItsSessionDeleted() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult login = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);
        setEnd(sessionId(login), Instant.now().minusSeconds(1));

        assertRedirect(mockMvc.perform(get("/medlem").with(sessionOf(login))).andReturn(), "/logga-in");
        assertThat(rowsIn("spring_session")).isZero();
    }

    @Test
    void theRestAdapterAnswers401ForALoginPastItsEnd() throws Exception {
        MvcResult login = logIn(LoginKind.ADMINISTRATOR, firstAdministratorEmail, FIREFOX_ON_WINDOWS);
        setEnd(sessionId(login), Instant.now().minusSeconds(1));

        mockMvc.perform(get("/api/admin/session").with(sessionOf(login))).andExpect(status().isUnauthorized());
    }

    @Test
    void aLoggedInSessionWithoutAnEndIsLoggedOut() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult login = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);
        setEnd(sessionId(login), null);

        assertRedirect(mockMvc.perform(get("/medlem").with(sessionOf(login))).andReturn(), "/logga-in");
    }

    @Test
    void theStatusSaysHowLongIsLeft() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult login = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);
        setEnd(sessionId(login), Instant.now().plusSeconds(100));

        String body = mockMvc.perform(get("/api/member/session").with(sessionOf(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(secondsLeft(body)).isBetween(98L, 100L);
    }

    @Test
    void extendingStartsTheFullLifetimeAgain() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult login = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);
        setEnd(sessionId(login), Instant.now().plusSeconds(100));
        Instant before = Instant.now();

        String body = mockMvc.perform(post("/api/member/session/extend").with(sessionOf(login)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long thirtyDays = settings.memberSession().toSeconds();
        assertThat(secondsLeft(body)).isBetween(thirtyDays - 2, thirtyDays);
        assertThat(endsAt(sessionId(login)))
                .isBetween(before.plus(settings.memberSession()), Instant.now().plus(settings.memberSession()));
    }

    @Test
    void extendingNeedsTheCsrfToken() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult login = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);
        Instant endsAt = endsAt(sessionId(login));

        mockMvc.perform(post("/api/member/session/extend").with(sessionOf(login))).andExpect(status().isForbidden());
        assertThat(endsAt(sessionId(login))).isEqualTo(endsAt);
    }

    @Test
    void theListShowsEveryLoginWithThisOneFirstAndNoSessionId() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult phone = logIn(LoginKind.MEMBER, KARIN, SAFARI_ON_IPHONE);
        MvcResult computer = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);

        String body = mockMvc.perform(get("/api/member/devices").with(sessionOf(computer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Firefox på Windows"))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].id").value(sha256Hex(sessionId(computer))))
                .andExpect(jsonPath("$[1].name").value("Safari på iOS"))
                .andExpect(jsonPath("$[1].current").value(false))
                .andExpect(jsonPath("$[1].id").value(sha256Hex(sessionId(phone))))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(sessionId(phone)).doesNotContain(sessionId(computer));
    }

    @Test
    void theListPutsThisDeviceFirstThenTheMostRecentlyUsed() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult first = logIn(LoginKind.MEMBER, KARIN, SAFARI_ON_IPHONE);
        MvcResult second = logIn(LoginKind.MEMBER, KARIN, SAFARI_ON_IPHONE);
        MvcResult current = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);
        mockMvc.perform(get("/api/member").with(sessionOf(first))).andExpect(status().isOk());

        mockMvc.perform(get("/api/member/devices").with(sessionOf(current)))
                .andExpect(jsonPath("$[0].id").value(sha256Hex(sessionId(current))))
                .andExpect(jsonPath("$[1].id").value(sha256Hex(sessionId(first))))
                .andExpect(jsonPath("$[2].id").value(sha256Hex(sessionId(second))));
    }

    @Test
    void theListLeavesOutLoginsPastTheirEndAndOtherPeoples() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        insertAccount("Erik Lindqvist", ERIK);
        MvcResult old = logIn(LoginKind.MEMBER, KARIN, SAFARI_ON_IPHONE);
        setEnd(sessionId(old), Instant.now().minusSeconds(1));
        logIn(LoginKind.MEMBER, ERIK, SAFARI_ON_IPHONE);
        logIn(LoginKind.ADMINISTRATOR, firstAdministratorEmail, SAFARI_ON_IPHONE);
        MvcResult login = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);

        mockMvc.perform(get("/api/member/devices").with(sessionOf(login)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void endingADeviceLogsItOutAndLeavesThisOne() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult phone = logIn(LoginKind.MEMBER, KARIN, SAFARI_ON_IPHONE);
        MvcResult computer = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);

        mockMvc.perform(delete("/api/member/devices/" + sha256Hex(sessionId(phone)))
                        .with(sessionOf(computer)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/member").with(sessionOf(phone))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/member").with(sessionOf(computer))).andExpect(status().isOk());
    }

    @Test
    void endingThisDeviceLogsItOut() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult login = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);

        mockMvc.perform(delete("/api/member/devices/" + sha256Hex(sessionId(login)))
                        .with(sessionOf(login)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/member").with(sessionOf(login))).andExpect(status().isUnauthorized());
    }

    /// The page stores a message for the next request after ending the device,
    /// which needs a session that is still there to store it in.
    @Test
    void endingThisDeviceFromThePageLogsItOut() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult login = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);

        MvcResult ended = mockMvc.perform(post("/medlem/enheter/" + sha256Hex(sessionId(login)) + "/logga-ut")
                        .with(sessionOf(login)).with(csrf()))
                .andReturn();

        assertRedirect(ended, "/medlem");
        mockMvc.perform(get("/api/member").with(sessionOf(login))).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherPersonsDeviceIsNotFound() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        insertAccount("Erik Lindqvist", ERIK);
        MvcResult erik = logIn(LoginKind.MEMBER, ERIK, SAFARI_ON_IPHONE);
        MvcResult karin = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);

        mockMvc.perform(delete("/api/member/devices/" + sha256Hex(sessionId(erik)))
                        .with(sessionOf(karin)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/member/devices/" + sessionId(erik)).with(sessionOf(karin)).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/member").with(sessionOf(erik))).andExpect(status().isOk());
    }

    @Test
    void endingTheOthersLeavesOnlyThisOne() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult phone = logIn(LoginKind.MEMBER, KARIN, SAFARI_ON_IPHONE);
        MvcResult tablet = logIn(LoginKind.MEMBER, KARIN, SAFARI_ON_IPHONE);
        MvcResult computer = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);

        mockMvc.perform(delete("/api/member/devices/others").with(sessionOf(computer)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/member").with(sessionOf(phone))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/member").with(sessionOf(tablet))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/member").with(sessionOf(computer))).andExpect(status().isOk());
    }

    @Test
    void thePageListsTheDevicesAndLogsOneOut() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult phone = logIn(LoginKind.MEMBER, KARIN, SAFARI_ON_IPHONE);
        MvcResult computer = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);
        String phoneId = sha256Hex(sessionId(phone));

        mockMvc.perform(get("/medlem").with(sessionOf(computer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Safari på iOS")))
                .andExpect(content().string(containsString("/medlem/enheter/" + phoneId + "/logga-ut")))
                .andExpect(content().string(containsString("data-seconds-left=\"")));

        MvcResult ended = mockMvc.perform(post("/medlem/enheter/" + phoneId + "/logga-ut")
                        .with(sessionOf(computer)).with(csrf()))
                .andExpect(flash().attribute("notice", "Enheten är utloggad."))
                .andReturn();

        assertRedirect(ended, "/medlem");
        mockMvc.perform(get("/api/member").with(sessionOf(phone))).andExpect(status().isUnauthorized());
    }

    @Test
    void theAdministratorPageLogsTheOthersOut() throws Exception {
        MvcResult phone = logIn(LoginKind.ADMINISTRATOR, firstAdministratorEmail, SAFARI_ON_IPHONE);
        MvcResult computer = logIn(LoginKind.ADMINISTRATOR, firstAdministratorEmail, FIREFOX_ON_WINDOWS);

        mockMvc.perform(get("/admin").with(sessionOf(computer)))
                .andExpect(content().string(containsString("/admin/enheter/andra/logga-ut")));
        assertRedirect(mockMvc.perform(post("/admin/enheter/andra/logga-ut").with(sessionOf(computer)).with(csrf()))
                .andReturn(), "/admin");

        mockMvc.perform(get("/api/admin/devices").with(sessionOf(phone))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/devices").with(sessionOf(computer)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anUnknownDeviceOnThePageSaysItIsAlreadyLoggedOut() throws Exception {
        insertAccount("Karin Karlsson", KARIN);
        MvcResult login = logIn(LoginKind.MEMBER, KARIN, FIREFOX_ON_WINDOWS);

        mockMvc.perform(post("/medlem/enheter/" + sha256Hex("gone") + "/logga-ut").with(sessionOf(login)).with(csrf()))
                .andExpect(flash().attribute("error", "Enheten är redan utloggad."));
    }

    /// Logs in by link, as [#logInByLink] does, from a browser that names itself.
    private MvcResult logIn(LoginKind kind, String email, String userAgent) throws Exception {
        forgetMails();
        requestLink(kind, email);
        String token = tokenIn(awaitMail(), linkPath(kind));
        Cookie browser = browserCookie(kind);
        MvcResult login = mockMvc.perform(post(linkPath(kind)).param("token", token).with(csrf())
                        .header(HttpHeaders.USER_AGENT, userAgent)
                        .cookie(browser == null ? new Cookie[0] : new Cookie[] {browser}))
                .andReturn();
        assertRedirect(login, landing(kind));
        return login;
    }

    /// Spring Session's cookie holds the session id in base64.
    private static String sessionId(MvcResult login) {
        Cookie cookie = login.getResponse().getCookie("SESSION");
        assertThat(cookie).as("session cookie").isNotNull();
        return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
    }

    private @Nullable Instant endsAt(String sessionId) {
        Session session = sessions.findById(sessionId);
        assertThat(session).as("session %s", sessionId).isNotNull();
        return session.getAttribute(LoginSession.ENDS_AT);
    }

    private void setEnd(String sessionId, @Nullable Instant endsAt) {
        setEnd(sessions, sessionId, endsAt);
    }

    private static <S extends Session> void setEnd(FindByIndexNameSessionRepository<S> repository, String sessionId,
            @Nullable Instant endsAt) {
        S session = repository.findById(sessionId);
        if (endsAt == null) {
            session.removeAttribute(LoginSession.ENDS_AT);
        } else {
            session.setAttribute(LoginSession.ENDS_AT, endsAt);
        }
        repository.save(session);
    }

    private static long secondsLeft(String json) {
        return Long.parseLong(json.replaceAll(".*\"secondsLeft\":(\\d+).*", "$1"));
    }
}
