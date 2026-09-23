package se.teaterihuskvarna.administrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.SignedIn;

/// Proves the administrator rules in `docs/projektplan.md` (I2), through the REST
/// endpoints and the `/admin` forms:
///
/// - the list shows active administrators only;
/// - an address can be an active administrator once, in any case, and a removed
///   administrator's address can be added again, which reuses the row;
/// - a removal is refused while two or fewer administrators remain, including
///   when two removals run at the same moment;
/// - a removal ends the removed administrator's sessions at once, proven with a
///   real login rather than a mocked one;
/// - a member's login cannot use the administrator endpoints.
///
/// Most requests authenticate with spring-security-test's `user(...)`, which puts
/// a [SignedIn] in the security context without a login. The session test logs
/// in by link instead, because it needs a session row in PostgreSQL to delete.
class AdministratorIT extends IntegrationTestSupport {

    private static final String API = "/api/admin/administrators";
    private static final String BO = "bo@example.test";
    private static final String CILLA = "cilla@example.test";

    @Autowired
    private AdministratorService administrators;

    @Test
    void theListShowsActiveAdministratorsOnly() throws Exception {
        insertAdministrator("removed@example.test", "Rut Removed");
        jdbc.sql("UPDATE administrator SET removed_at = now(), removed_by = ? WHERE email = 'removed@example.test'")
                .param(firstAdministratorId())
                .update();

        mockMvc.perform(get(API).with(asFirstAdministrator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email", hasItem(equalToIgnoringCase(firstAdministratorEmail))))
                .andExpect(jsonPath("$[*].email", not(hasItem("removed@example.test"))));
    }

    @Test
    void addingRecordsWhoAdded() throws Exception {
        MvcResult added = mockMvc.perform(addByApi(BO, "Bo Berg"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(BO))
                .andExpect(jsonPath("$.fullName").value("Bo Berg"))
                .andReturn();

        long id = idOf(BO);
        Number returnedId = JsonPath.read(added.getResponse().getContentAsString(), "$.id");
        assertThat(returnedId.longValue()).isEqualTo(id);
        assertThat(jdbc.sql("SELECT created_by FROM administrator WHERE id = ?").param(id).query(Long.class).single())
                .isEqualTo(firstAdministratorId());
        mockMvc.perform(get(API).with(asFirstAdministrator()))
                .andExpect(jsonPath("$[*].email", hasItem(BO)));
    }

    @Test
    void anActiveAddressCannotBeAddedTwiceInAnyCase() throws Exception {
        mockMvc.perform(addByApi(firstAdministratorEmail.toUpperCase(Locale.ROOT), "Ada Again"))
                .andExpect(status().isConflict());

        assertThat(rowsIn("administrator")).isEqualTo(1);
    }

    @Test
    void addingNeedsAName() throws Exception {
        mockMvc.perform(addByApi(BO, " ")).andExpect(status().isBadRequest());

        assertThat(rowsIn("administrator")).isEqualTo(1);
    }

    @Test
    void removalIsRefusedWithTwoAdministrators() throws Exception {
        long bo = insertAdministrator(BO, "Bo Berg");

        mockMvc.perform(removeByApi(bo)).andExpect(status().isConflict());

        assertThat(activeAdministrators()).isEqualTo(2);
    }

    @Test
    void removalIsAllowedWithThreeAdministrators() throws Exception {
        long bo = insertAdministrator(BO, "Bo Berg");
        insertAdministrator(CILLA, "Cilla Carlsson");

        mockMvc.perform(removeByApi(bo)).andExpect(status().isNoContent());

        assertThat(jdbc.sql("SELECT removed_by FROM administrator WHERE id = ? AND removed_at IS NOT NULL")
                        .param(bo)
                        .query(Long.class)
                        .list())
                .containsExactly(firstAdministratorId());
        mockMvc.perform(get(API).with(asFirstAdministrator()))
                .andExpect(jsonPath("$[*].email", not(hasItem(BO))));
    }

    @Test
    void removingAnUnknownAdministratorAnswers404() throws Exception {
        insertAdministrator(BO, "Bo Berg");
        insertAdministrator(CILLA, "Cilla Carlsson");

        mockMvc.perform(removeByApi(Long.MAX_VALUE)).andExpect(status().isNotFound());

        assertThat(activeAdministrators()).isEqualTo(3);
    }

    /// `administrator_email_key` spans removed rows, so adding the address again
    /// has to reuse the row rather than insert a second one.
    @Test
    void aRemovedAdministratorCanBeAddedAgainInAnotherCase() throws Exception {
        long bo = insertAdministrator(BO, "Bo Berg");
        insertAdministrator(CILLA, "Cilla Carlsson");
        mockMvc.perform(removeByApi(bo)).andExpect(status().isNoContent());

        mockMvc.perform(addByApi("Bo@Example.TEST", "Bo Berg"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bo));

        assertThat(rowsIn("administrator")).isEqualTo(3);
        assertThat(activeAdministrators()).isEqualTo(3);
        mockMvc.perform(get(API).with(asFirstAdministrator()))
                .andExpect(jsonPath("$[*].email", hasItem(equalToIgnoringCase(BO))));
    }

    /// Without this a removed administrator keeps a working session until it
    /// expires. The first request proves the session worked before the removal,
    /// so the last one fails because of the removal and not for another reason.
    @Test
    void removalEndsTheRemovedAdministratorsSessions() throws Exception {
        long bo = insertAdministrator(BO, "Bo Berg");
        insertAdministrator(CILLA, "Cilla Carlsson");
        MvcResult login = logInByLink(LoginKind.ADMINISTRATOR, BO);
        mockMvc.perform(get("/admin").with(sessionOf(login))).andExpect(status().isOk());
        assertThat(sessionsOf(bo)).isEqualTo(1);

        mockMvc.perform(removeByApi(bo)).andExpect(status().isNoContent());

        assertThat(sessionsOf(bo)).isZero();
        assertRedirect(mockMvc.perform(get("/admin").with(sessionOf(login))).andReturn(), "/admin/logga-in");
    }

    @Test
    void aMemberCannotUseTheAdministratorApi() throws Exception {
        RequestPostProcessor member = user(new SignedIn(LoginKind.MEMBER, 1, "karin@example.test", "Karin"));

        mockMvc.perform(get(API).with(member)).andExpect(status().isForbidden());
        mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(BO, "Bo Berg"))
                        .with(member)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(rowsIn("administrator")).isEqualTo(1);
    }

    @Test
    void theApiNeedsTheCsrfToken() throws Exception {
        mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(BO, "Bo Berg"))
                        .with(asFirstAdministrator()))
                .andExpect(status().isForbidden());

        assertThat(rowsIn("administrator")).isEqualTo(1);
    }

    @Test
    void thePageFormsAddAndRemove() throws Exception {
        insertAdministrator(CILLA, "Cilla Carlsson");

        MvcResult added = mockMvc.perform(post("/admin/administratorer")
                        .param("email", BO)
                        .param("fullName", "Bo Berg")
                        .with(asFirstAdministrator())
                        .with(csrf()))
                .andReturn();
        assertRedirect(added, "/admin");
        long bo = idOf(BO);

        MvcResult removed = mockMvc.perform(post("/admin/administratorer/" + bo + "/ta-bort")
                        .with(asFirstAdministrator())
                        .with(csrf()))
                .andReturn();
        assertRedirect(removed, "/admin");

        assertThat(jdbc.sql("SELECT removed_by FROM administrator WHERE id = ?").param(bo).query(Long.class).single())
                .isEqualTo(firstAdministratorId());
        assertThat(activeAdministrators()).isEqualTo(2);
    }

    @Test
    void thePageRefusesARemovalWithTwoAdministrators() throws Exception {
        long bo = insertAdministrator(BO, "Bo Berg");

        MvcResult removed = mockMvc.perform(post("/admin/administratorer/" + bo + "/ta-bort")
                        .with(asFirstAdministrator())
                        .with(csrf()))
                .andReturn();

        assertRedirect(removed, "/admin");
        assertThat(activeAdministrators()).isEqualTo(2);
    }

    /// Two administrators each remove a different third one at the same moment.
    /// Each sees three active administrators, so without the row lock in
    /// `remove` both removals would pass and leave one.
    ///
    /// The latch lines the two calls up, but it cannot make them overlap inside
    /// the database: a run where one commits before the other reads also passes.
    /// So a pass here is evidence, not proof, and repeating it makes an overlap
    /// likely in at least one run. A failure is always a real bug.
    @RepeatedTest(5)
    void twoRemovalsAtOnceLeaveTwoAdministrators() throws Exception {
        long first = firstAdministratorId();
        long bo = insertAdministrator(BO, "Bo Berg");
        long cilla = insertAdministrator(CILLA, "Cilla Carlsson");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Optional<RuntimeException>>> running = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            running.add(pool.submit(removal(bo, first, ready, start)));
            running.add(pool.submit(removal(cilla, first, ready, start)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).as("both threads started").isTrue();
            start.countDown();
        }
        List<Optional<RuntimeException>> outcomes = new ArrayList<>();
        for (Future<Optional<RuntimeException>> outcome : running) {
            outcomes.add(outcome.get(10, TimeUnit.SECONDS));
        }

        assertThat(outcomes).filteredOn(Optional::isEmpty).hasSize(1);
        assertThat(outcomes).filteredOn(Optional::isPresent).singleElement()
                .satisfies(outcome -> assertThat(outcome.orElseThrow()).isInstanceOf(TooFewAdministrators.class));
        assertThat(activeAdministrators()).isEqualTo(2);
    }

    private Callable<Optional<RuntimeException>> removal(long id, long by, CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the start signal never came");
            }
            try {
                administrators.remove(id, by);
                return Optional.empty();
            } catch (RuntimeException e) {
                return Optional.of(e);
            }
        };
    }

    private RequestPostProcessor asFirstAdministrator() {
        long id = firstAdministratorId();
        return user(new SignedIn(LoginKind.ADMINISTRATOR, id, firstAdministratorEmail, "Ada Admin"));
    }

    private MockHttpServletRequestBuilder addByApi(String email, String fullName) {
        return post(API)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(email, fullName))
                .with(asFirstAdministrator())
                .with(csrf());
    }

    private MockHttpServletRequestBuilder removeByApi(long id) {
        return delete(API + "/" + id).with(asFirstAdministrator()).with(csrf());
    }

    private static String json(String email, String fullName) {
        return "{\"email\": \"" + email + "\", \"fullName\": \"" + fullName + "\"}";
    }

    private long idOf(String email) {
        return jdbc.sql("SELECT id FROM administrator WHERE LOWER(email) = LOWER(?)")
                .param(email)
                .query(Long.class)
                .single();
    }

    private long activeAdministrators() {
        return jdbc.sql("SELECT COUNT(*) FROM administrator WHERE removed_at IS NULL").query(Long.class).single();
    }

    private long sessionsOf(long administratorId) {
        return jdbc.sql("SELECT COUNT(*) FROM spring_session WHERE principal_name = ?")
                .param(LoginKind.ADMINISTRATOR.principalName(administratorId))
                .query(Long.class)
                .single();
    }
}
