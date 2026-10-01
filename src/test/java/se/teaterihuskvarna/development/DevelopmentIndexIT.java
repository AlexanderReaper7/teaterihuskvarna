package se.teaterihuskvarna.development;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.login.LoginKind;
import tools.jackson.databind.json.JsonMapper;

/// The development index under the `dev` profile. `LoginIT` checks the other
/// half: without the profile, none of this is mapped.
///
/// The profile is extra context configuration, so this class gets a second
/// application context and a second PostgreSQL container, which
/// [IntegrationTestSupport] otherwise avoids. The index exists only under the
/// profile, so there is no way to test it in the shared context.
@ActiveProfiles("dev")
class DevelopmentIndexIT extends IntegrationTestSupport {

    @Test
    void theIndexLinksThePagesAndOffersALinkForEveryAccount() throws Exception {
        insertAccount("Tove Testsson", "tove@example.test");

        mockMvc.perform(get("/dev"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<a href=\"/bli-medlem\">")))
                .andExpect(content().string(containsString("tove@example.test")))
                .andExpect(content().string(containsString(firstAdministratorEmail)))
                .andExpect(content().string(containsString("action=\"/logga-in\"")))
                .andExpect(content().string(containsString("action=\"/admin/logga-in\"")))
                .andExpect(content().string(containsString("action=\"/dev/login\"")))
                .andExpect(content().string(containsString("Log in now")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @ParameterizedTest
    @EnumSource(LoginKind.class)
    void immediateLoginUsesTheSelectedKindAndCreatesANormalSessionWithoutMail(LoginKind kind) throws Exception {
        long accountId = insertAccount("Tove Testsson", firstAdministratorEmail);
        MvcResult index = mockMvc.perform(get("/dev")).andReturn();
        Instant before = Instant.now();
        MvcResult login = mockMvc.perform(post("/dev/login").param("kind", kind.name())
                        .param("email", firstAdministratorEmail).header("User-Agent", "Firefox/150.0 Windows")
                        .with(sessionOf(index)).with(csrf()))
                .andReturn();
        assertRedirect(login, landing(kind));
        assertThat(login.getResponse().getCookie("SESSION")).isNotNull();
        assertThat(login.getResponse().getCookie("SESSION").getValue())
                .isNotEqualTo(index.getResponse().getCookie("SESSION").getValue());
        Duration lifetime = kind == LoginKind.MEMBER ? Duration.ofDays(30) : Duration.ofHours(8);
        assertThat(login.getResponse().getCookie("SESSION").getMaxAge()).isPositive();
        long id = kind == LoginKind.MEMBER ? accountId : firstAdministratorId();
        assertThat(jdbc.sql("SELECT max_inactive_interval FROM spring_session WHERE principal_name = ?")
                .param(kind.principalName(id)).query(Integer.class).single()).isEqualTo((int) lifetime.toSeconds());

        String api = kind == LoginKind.MEMBER ? "/api/member" : "/api/admin";
        mockMvc.perform(get(landing(kind)).with(sessionOf(login))).andExpect(status().isOk());
        mockMvc.perform(get(api + "/devices").with(sessionOf(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Firefox på Windows"));
        MvcResult session = mockMvc.perform(get(api + "/session").with(sessionOf(login)))
                .andExpect(status().isOk()).andReturn();
        Instant endsAt = Instant.parse(JsonMapper.shared().readTree(session.getResponse().getContentAsString())
                .get("endsAt").asString());
        assertThat(endsAt).isBetween(before.plus(lifetime), Instant.now().plus(lifetime));
        assertThat(rowsIn("one_time_token")).isZero();
        assertThat(rowsIn("link_request")).isZero();
        assertNoMail();
    }

    @Test
    void immediateLoginThroughTheApiPersistsTheSameSession() throws Exception {
        insertAccount("Tove Testsson", "tove@example.test");
        MvcResult login = mockMvc.perform(post("/api/development/login").param("kind", "MEMBER")
                        .param("email", "tove@example.test").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.redirectUrl").value("/medlem"))
                .andReturn();
        mockMvc.perform(get("/api/member").with(sessionOf(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("tove@example.test"));
        assertNoMail();
    }

    @Test
    void immediateLoginRequiresCsrfAndRefusesMissingOrRemovedAccounts() throws Exception {
        mockMvc.perform(post("/api/development/login").param("kind", "ADMINISTRATOR")
                        .param("email", firstAdministratorEmail))
                .andExpect(status().isForbidden());
        for (String kind : new String[] {"MEMBER", "ADMINISTRATOR", "UNKNOWN", ""}) {
            mockMvc.perform(post("/api/development/login").param("kind", kind)
                            .param("email", "missing@example.test").with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
        long removed = insertAdministrator("removed@example.test", "Borttagen administratör");
        jdbc.sql("UPDATE administrator SET removed_at = now() WHERE id = ?").param(removed).update();
        mockMvc.perform(post("/api/development/login").param("kind", "ADMINISTRATOR")
                        .param("email", "removed@example.test").with(csrf()))
                .andExpect(status().isUnauthorized());
        MvcResult failure = mockMvc.perform(post("/dev/login").param("kind", "MEMBER")
                        .param("email", "missing@example.test").with(csrf()))
                .andReturn();
        assertRedirect(failure, "/dev?login-failed");
        mockMvc.perform(get("/dev").param("login-failed", ""))
                .andExpect(content().string(containsString("That account can no longer log in.")));
        assertNoMail();
    }

    @Test
    void thePublicStartPageDoesNotShowDeveloperTools() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nästa evenemang")))
                .andExpect(content().string(not(containsString("Development index"))));
    }

    @Test
    void theRoutesIncludeTheLoginFiltersNoControllerShows() throws Exception {
        mockMvc.perform(get("/api/development/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.kind == 'LOGIN_FILTER')].path", hasItem("/logga-in")))
                .andExpect(jsonPath("$[?(@.kind == 'LOGIN_FILTER')].path", hasItem("/admin/logga-ut")))
                .andExpect(jsonPath("$[?(@.kind == 'LOGIN_FILTER')].path", hasItem("/medlem/passkeys")))
                .andExpect(jsonPath("$[?(@.kind == 'LOGIN_FILTER')].path", hasItem("/dev/login")))
                .andExpect(jsonPath("$[?(@.kind == 'LOGIN_FILTER')].path", hasItem("/api/development/login")))
                .andExpect(jsonPath("$[?(@.kind == 'PAGE')].path", hasItem("/")))
                .andExpect(jsonPath("$[?(@.kind == 'PAGE')].path", hasItem("/dev")))
                .andExpect(jsonPath("$[?(@.kind == 'ENDPOINT')].path", hasItem("/api/development/routes")));
    }

    @Test
    void theEnvironmentNamesTheProfileAndTheSchemaVersion() throws Exception {
        mockMvc.perform(get("/api/development/environment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles", hasItem("dev")))
                .andExpect(jsonPath("$.schemaVersion").value(newestMigration()));
    }

    /// The highest `V<n>__` among the migration files, read from the classpath
    /// rather than from Flyway, which is where the endpoint gets its answer.
    private static String newestMigration() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql");
        int newest = Arrays.stream(files)
                .map(file -> Objects.requireNonNull(file.getFilename()))
                .mapToInt(name -> Integer.parseInt(name.substring(1, name.indexOf("__"))))
                .max()
                .orElseThrow();
        return Integer.toString(newest);
    }
}
