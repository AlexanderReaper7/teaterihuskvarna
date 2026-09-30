package se.teaterihuskvarna.development;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.ActiveProfiles;
import se.teaterihuskvarna.IntegrationTestSupport;

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
                .andExpect(content().string(containsString("name=\"_csrf\"")));
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
