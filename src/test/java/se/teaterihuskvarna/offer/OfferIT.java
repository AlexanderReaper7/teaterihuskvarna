package se.teaterihuskvarna.offer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.web.Copy;

/// Proves the offer rules (R014) and the registration export (R020) through
/// both adapters: the `/medlem` and `/admin` pages and the REST API.
///
/// - Only a member reaches the member side and only an administrator the
///   administrator side; an anonymous page request goes to a login page, an
///   anonymous API request gets 401.
/// - A member never sees an unpublished offer: not in the list, not by id, and
///   cannot register for it.
/// - Registration is refused when the offer is full or closed, is idempotent,
///   and cancellation works until registration closes.
/// - Places left never go below 0, even when the capacity is lowered below the
///   number registered.
/// - Many members registering for the last place at once leaves exactly one of
///   them registered.
/// - The CSV file starts with a byte order mark and defuses a cell that a
///   spreadsheet would read as a formula.
class OfferIT extends IntegrationTestSupport {

    private static final Instant IN_A_MONTH = Instant.now().plus(Duration.ofDays(30));
    private static final Instant IN_A_WEEK = Instant.now().plus(Duration.ofDays(7));
    private static final Instant YESTERDAY = Instant.now().minus(Duration.ofDays(1));

    @Autowired
    private OfferService offers;

    @Autowired
    private Copy copy;

    // Who reaches what

    @Test
    void anonymousRequestsAreSentToLogInOrRefused() throws Exception {
        long id = insertOffer("Verkstad", true, null, IN_A_MONTH, null);

        assertRedirect(mockMvc.perform(get("/medlem/erbjudanden")).andReturn(), "/logga-in");
        assertRedirect(mockMvc.perform(get("/medlem/erbjudanden/" + id)).andReturn(), "/logga-in");
        assertRedirect(mockMvc.perform(get("/admin/erbjudanden")).andReturn(), "/admin/logga-in");
        assertRedirect(mockMvc.perform(get("/admin/erbjudanden/" + id + "/anmalningar.csv")).andReturn(),
                "/admin/logga-in");
        mockMvc.perform(get("/api/member/offers")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/member/offers/" + id + "/registration").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/offers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/offers/" + id + "/registrations.csv")).andExpect(status().isUnauthorized());

        assertThat(rowsIn("offer_registration")).isZero();
    }

    @Test
    void aMemberCannotReachTheAdministratorSide() throws Exception {
        long id = insertOffer("Verkstad", false, null, IN_A_MONTH, null);
        RequestPostProcessor member = asMember(insertAccount("Karin Holm", "karin@example.test"));

        assertRedirect(mockMvc.perform(get("/admin/erbjudanden").with(member)).andReturn(), "/admin/logga-in");
        assertRedirect(mockMvc.perform(post("/admin/erbjudanden/" + id + "/publicera").with(member).with(csrf()))
                .andReturn(), "/admin/logga-in");
        assertRedirect(mockMvc.perform(get("/admin/erbjudanden/" + id + "/anmalningar.csv").with(member))
                .andReturn(), "/admin/logga-in");
        mockMvc.perform(get("/api/admin/offers").with(member)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/offers/" + id + "/publish").with(member).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/offers/" + id + "/registrations.csv").with(member))
                .andExpect(status().isForbidden());

        assertThat(jdbc.sql("SELECT published FROM offer WHERE id = ?").param(id).query(Boolean.class).single())
                .isFalse();
    }

    @Test
    void aMemberNeverSeesAnUnpublishedOffer() throws Exception {
        long draft = insertOffer("Hemligt utkast", false, null, IN_A_MONTH, null);
        long published = insertOffer("Öppen verkstad", true, null, IN_A_MONTH, null);
        RequestPostProcessor member = asMember(insertAccount("Karin Holm", "karin@example.test"));

        mockMvc.perform(get("/api/member/offers").with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) published)))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) draft))));
        mockMvc.perform(get("/medlem/erbjudanden").with(member))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Öppen verkstad")))
                .andExpect(content().string(not(containsString("Hemligt utkast"))));
        mockMvc.perform(get("/api/member/offers/" + draft).with(member)).andExpect(status().isNotFound());
        mockMvc.perform(get("/medlem/erbjudanden/" + draft).with(member)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/member/offers/" + draft + "/registration").with(member).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/medlem/erbjudanden/" + draft + "/anmal").with(member).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(rowsIn("offer_registration")).isZero();
    }

    // Registering and cancelling

    @Test
    void registeringThroughTheApiIsIdempotentAndCancellable() throws Exception {
        long id = insertOffer("Verkstad", true, 3, IN_A_MONTH, null);
        RequestPostProcessor member = asMember(insertAccount("Karin Holm", "karin@example.test"));

        mockMvc.perform(post("/api/member/offers/" + id + "/registration").with(member).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.placesLeft").value(2));
        mockMvc.perform(post("/api/member/offers/" + id + "/registration").with(member).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placesLeft").value(2));
        assertThat(rowsIn("offer_registration")).isEqualTo(1);

        mockMvc.perform(delete("/api/member/offers/" + id + "/registration").with(member).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/member/offers/" + id).with(member))
                .andExpect(jsonPath("$.registered").value(false))
                .andExpect(jsonPath("$.placesLeft").value(3));
        assertThat(rowsIn("offer_registration")).isZero();
    }

    @Test
    void registeringThroughThePageSaysWhatHappened() throws Exception {
        long id = insertOffer("Verkstad", true, null, IN_A_MONTH, null);
        RequestPostProcessor member = asMember(insertAccount("Karin Holm", "karin@example.test"));
        String page = "/medlem/erbjudanden/" + id;

        MvcResult first = mockMvc.perform(post(page + "/anmal").with(member).with(csrf()))
                .andExpect(flash().attribute("notice", copy.text("offers.notice.registered")))
                .andReturn();
        assertRedirect(first, page);
        mockMvc.perform(post(page + "/anmal").with(member).with(csrf()))
                .andExpect(flash().attribute("notice", copy.text("offers.notice.alreadyRegistered")));
        mockMvc.perform(get(page).with(member))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(copy.text("offers.registered"))))
                .andExpect(content().string(containsString("/avanmal")));
        assertThat(rowsIn("offer_registration")).isEqualTo(1);

        mockMvc.perform(post(page + "/avanmal").with(member).with(csrf()))
                .andExpect(flash().attribute("notice", copy.text("offers.notice.cancelled")));
        assertThat(rowsIn("offer_registration")).isZero();
    }

    @Test
    void aFullOfferRefusesAnotherMember() throws Exception {
        long id = insertOffer("Verkstad", true, 1, IN_A_MONTH, null);
        register(id, insertAccount("Anna Först", "anna@example.test"));
        RequestPostProcessor late = asMember(insertAccount("Bo Sen", "bo@example.test"));

        mockMvc.perform(post("/api/member/offers/" + id + "/registration").with(late).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/medlem/erbjudanden/" + id + "/anmal").with(late).with(csrf()))
                .andExpect(flash().attribute("error", copy.text("offers.error.full")));
        mockMvc.perform(get("/api/member/offers/" + id).with(late))
                .andExpect(jsonPath("$.placesLeft").value(0));
        mockMvc.perform(get("/medlem/erbjudanden/" + id).with(late))
                .andExpect(content().string(containsString(copy.text("offers.full"))))
                .andExpect(content().string(not(containsString("/anmal\""))));

        assertThat(rowsIn("offer_registration")).isEqualTo(1);
    }

    @Test
    void aClosedOfferRefusesRegisteringAndCancelling() throws Exception {
        long id = insertOffer("Verkstad", true, null, IN_A_MONTH, YESTERDAY);
        long registeredAccount = insertAccount("Anna Först", "anna@example.test");
        jdbc.sql("INSERT INTO offer_registration (offer_id, member_id, created_at) VALUES (?, ?, now())")
                .param(id)
                .param(memberOf(registeredAccount))
                .update();
        RequestPostProcessor registered = asMember(registeredAccount);
        RequestPostProcessor other = asMember(insertAccount("Bo Sen", "bo@example.test"));

        mockMvc.perform(post("/api/member/offers/" + id + "/registration").with(other).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/medlem/erbjudanden/" + id + "/anmal").with(other).with(csrf()))
                .andExpect(flash().attribute("error", copy.text("offers.error.closed")));
        mockMvc.perform(delete("/api/member/offers/" + id + "/registration").with(registered).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/medlem/erbjudanden/" + id + "/avanmal").with(registered).with(csrf()))
                .andExpect(flash().attribute("error", copy.text("offers.error.closed")));
        mockMvc.perform(get("/api/member/offers").with(other))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) id))));
        mockMvc.perform(get("/api/member/offers/" + id).with(registered))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));

        assertThat(rowsIn("offer_registration")).isEqualTo(1);
    }

    @Test
    void registrationClosesAtTheStartWhenNoClosingTimeIsSet() throws Exception {
        long started = insertOffer("Redan börjat", true, null, YESTERDAY, null);
        RequestPostProcessor member = asMember(insertAccount("Karin Holm", "karin@example.test"));

        mockMvc.perform(post("/api/member/offers/" + started + "/registration").with(member).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void placesLeftNeverGoBelowZero() throws Exception {
        long id = insertOffer("Verkstad", true, 3, IN_A_MONTH, null);
        register(id, insertAccount("Anna Först", "anna@example.test"));
        long bo = insertAccount("Bo Sen", "bo@example.test");
        register(id, bo);

        mockMvc.perform(put("/api/admin/offers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Verkstad\",\"capacity\":1}")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(2))
                .andExpect(jsonPath("$.placesLeft").value(0));
        mockMvc.perform(get("/api/member/offers/" + id).with(asMember(bo)))
                .andExpect(jsonPath("$.placesLeft").value(0));

        assertThat(rowsIn("offer_registration")).isEqualTo(2);
    }

    /// Every thread waits on one latch and then registers a different member
    /// for the one place left. Without the row lock, several threads count the
    /// same free place and all of them take it.
    @RepeatedTest(3)
    void manyMembersAtOnceTakeTheLastPlaceOnce() throws Exception {
        int threads = 8;
        long id = insertOffer("Sista platsen", true, 2, IN_A_MONTH, null);
        register(id, insertAccount("Anna Först", "anna@example.test"));
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            memberIds.add(insertMember("Medlem " + i));
        }
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<String>> running = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (long memberId : memberIds) {
                running.add(pool.submit(registration(id, memberId, ready, start)));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).as("every thread started").isTrue();
            start.countDown();
        }
        List<String> outcomes = new ArrayList<>();
        for (Future<String> outcome : running) {
            outcomes.add(outcome.get(10, TimeUnit.SECONDS));
        }

        assertThat(outcomes).filteredOn("REGISTERED"::equals).hasSize(1);
        assertThat(outcomes).filteredOn("FULL"::equals).hasSize(threads - 1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM offer_registration WHERE offer_id = ?").param(id)
                .query(Long.class).single()).isEqualTo(2);
    }

    // Administration

    @Test
    void theCreateFormShowsWhatIsWrong() throws Exception {
        mockMvc.perform(post("/admin/erbjudanden")
                        .param("title", " ")
                        .param("description", "")
                        .param("startsAt", "")
                        .param("registrationClosesAt", "")
                        .param("capacity", "-1")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(copy.text("offer.title.required"))))
                .andExpect(content().string(containsString(copy.text("offer.capacity.min"))));
        mockMvc.perform(post("/api/admin/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"capacity\":-1}")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value(copy.text("offer.title.required")))
                .andExpect(jsonPath("$.errors.capacity").value(copy.text("offer.capacity.min")));

        assertThat(rowsIn("offer")).isZero();
    }

    @Test
    void anOfferIsCreatedAsADraftAndPublishedLater() throws Exception {
        MvcResult created = mockMvc.perform(post("/admin/erbjudanden")
                        .param("title", "Sminkkurs")
                        .param("description", "Rad ett\nRad två")
                        .param("startsAt", "2030-05-01T18:30")
                        .param("registrationClosesAt", "")
                        .param("capacity", "12")
                        .with(asAdministrator())
                        .with(csrf()))
                .andReturn();
        long id = jdbc.sql("SELECT id FROM offer").query(Long.class).single();
        assertRedirect(created, "/admin/erbjudanden/" + id);
        assertThat(jdbc.sql("SELECT starts_at FROM offer").query(Timestamp.class).single().toInstant())
                .as("18:30 Swedish summer time")
                .isEqualTo(Instant.parse("2030-05-01T16:30:00Z"));
        RequestPostProcessor member = asMember(insertAccount("Karin Holm", "karin@example.test"));
        mockMvc.perform(get("/api/member/offers/" + id).with(member)).andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/erbjudanden/" + id + "/publicera").with(asAdministrator()).with(csrf()));
        mockMvc.perform(get("/medlem/erbjudanden/" + id).with(member))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sminkkurs")))
                .andExpect(content().string(containsString("1 maj 2030 kl. 18:30")));

        mockMvc.perform(post("/api/admin/offers/" + id + "/unpublish").with(asAdministrator()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));
        mockMvc.perform(get("/api/member/offers/" + id).with(member)).andExpect(status().isNotFound());
    }

    @Test
    void theApiCreatesAndChangesAnOffer() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/admin/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Scenskräck","description":"Kväll","startsAt":"2030-01-10T19:00",
                                 "registrationClosesAt":"2030-01-09T12:00","capacity":5}
                                """)
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.startsAt").value("2030-01-10T18:00:00Z"))
                .andExpect(jsonPath("$.closesAt").value("2030-01-09T11:00:00Z"))
                .andReturn();
        long id = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

        mockMvc.perform(put("/api/admin/offers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Scenskräck 2\"}")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Scenskräck 2"))
                .andExpect(jsonPath("$.capacity").doesNotExist());
        mockMvc.perform(get("/api/admin/offers").with(asAdministrator()))
                .andExpect(jsonPath("$[*].title", hasItem("Scenskräck 2")));
        mockMvc.perform(put("/api/admin/offers/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\"}")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theAdministratorPageListsWhoRegistered() throws Exception {
        long id = insertOffer("Verkstad", true, null, IN_A_MONTH, null);
        long anna = insertAccount("Anna Först", "anna@example.test");
        jdbc.sql("UPDATE member SET phone = '070-111 22 33' WHERE id = ?").param(memberOf(anna)).update();
        register(id, anna);

        mockMvc.perform(get("/admin/erbjudanden/" + id).with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Anna Först")))
                .andExpect(content().string(containsString("anna@example.test")))
                .andExpect(content().string(containsString("070-111 22 33")))
                .andExpect(content().string(containsString("/admin/erbjudanden/" + id + "/anmalningar.csv")));
        mockMvc.perform(get("/api/admin/offers/" + id + "/registrations").with(asAdministrator()))
                .andExpect(jsonPath("$[0].fullName").value("Anna Först"))
                .andExpect(jsonPath("$[0].phone").value("070-111 22 33"));
    }

    @Test
    void deletingAsksFirstAndTakesTheRegistrations() throws Exception {
        long id = insertOffer("Verkstad", true, null, IN_A_MONTH, null);
        register(id, insertAccount("Anna Först", "anna@example.test"));

        mockMvc.perform(get("/admin/erbjudanden/" + id + "/ta-bort").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(copy.text("adminOffers.delete.question", "Verkstad"))));
        assertThat(rowsIn("offer")).isEqualTo(1);

        assertRedirect(mockMvc.perform(post("/admin/erbjudanden/" + id + "/ta-bort").with(asAdministrator())
                .with(csrf())).andReturn(), "/admin/erbjudanden");
        assertThat(rowsIn("offer")).isZero();
        assertThat(rowsIn("offer_registration")).isZero();

        long other = insertOffer("Annan", false, null, IN_A_MONTH, null);
        mockMvc.perform(delete("/api/admin/offers/" + other).with(asAdministrator()).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/offers/" + other).with(asAdministrator()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/erbjudanden/" + other).with(asAdministrator()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAMemberRemovesTheirRegistrations() {
        long id = insertOffer("Verkstad", true, null, IN_A_MONTH, null);
        long anna = insertAccount("Anna Först", "anna@example.test");
        register(id, anna);
        long member = memberOf(anna);

        jdbc.sql("DELETE FROM account WHERE member_id = ?").param(member).update();
        jdbc.sql("DELETE FROM member WHERE id = ?").param(member).update();

        assertThat(rowsIn("offer_registration")).isZero();
        assertThat(rowsIn("offer")).isEqualTo(1);
    }

    // Export and mailing

    @Test
    void theCsvFileIsTheSameFromBothAdaptersAndDefusesFormulas() throws Exception {
        long id = insertOffer("Verkstad", true, null, IN_A_MONTH, null);
        long mallory = insertAccount("=HYPERLINK(\"http://x.test\";\"klicka\")", "mallory@example.test");
        jdbc.sql("UPDATE member SET phone = '+46 70 111' WHERE id = ?").param(memberOf(mallory)).update();
        register(id, mallory);
        register(id, insertAccount("Åsa Öberg; junior", "asa@example.test"));

        MvcResult page = mockMvc.perform(get("/admin/erbjudanden/" + id + "/anmalningar.csv")
                        .with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"anmalningar-erbjudande-" + id + ".csv\""))
                .andReturn();
        MvcResult api = mockMvc.perform(get("/api/admin/offers/" + id + "/registrations.csv")
                        .with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andReturn();

        byte[] bytes = page.getResponse().getContentAsByteArray();
        assertThat(api.getResponse().getContentAsByteArray()).isEqualTo(bytes);
        assertThat(bytes).startsWith(0xEF, 0xBB, 0xBF);
        String text = new String(bytes, StandardCharsets.UTF_8).substring(1);
        String[] lines = text.split("\r\n", -1);
        assertThat(lines[0]).isEqualTo(String.join(";", copy.text("offer.csv.name"), copy.text("offer.csv.email"),
                copy.text("offer.csv.phone"), copy.text("offer.csv.registeredAt")));
        assertThat(lines[1])
                .startsWith("\"'=HYPERLINK(\"\"http://x.test\"\";\"\"klicka\"\")\";mallory@example.test;'+46 70 111;");
        assertThat(lines[2]).startsWith("\"Åsa Öberg; junior\";asa@example.test;;");
        assertThat(lines[1]).matches(".*;\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
        assertThat(lines[3]).isEmpty();
        assertThat(lines).hasSize(4);
    }

    @Test
    void recipientsAreTheRegisteredMembersWithAnAccount() {
        long id = insertOffer("Verkstad", true, null, IN_A_MONTH, null);
        long anna = insertAccount("Anna Först", "anna@example.test");
        register(id, anna);
        long child = insertMember("Olle Barn");
        offers.register(id, child);

        assertThat(offers.recipients(id))
                .containsExactly(new Recipient(memberOf(anna), "Anna Först", "anna@example.test"));
        assertThat(offers.registrations(id)).hasSize(2);
    }

    // Helpers

    private long insertOffer(String title, boolean published, @Nullable Integer capacity, Instant startsAt,
            @Nullable Instant closesAt) {
        return jdbc.sql("""
                INSERT INTO offer (title, description, starts_at, registration_closes_at, capacity, published,
                    created_at, updated_at)
                VALUES (?, '', ?, ?, ?, ?, now(), now())
                RETURNING id
                """)
                .param(title)
                .param(Timestamp.from(startsAt))
                .param(closesAt == null ? null : Timestamp.from(closesAt))
                .param(capacity)
                .param(published)
                .query(Long.class)
                .single();
    }

    private long memberOf(long accountId) {
        return jdbc.sql("SELECT member_id FROM account WHERE id = ?").param(accountId).query(Long.class).single();
    }

    private void register(long offerId, long accountId) {
        assertThat(offers.register(offerId, memberOf(accountId))).isEqualTo(RegistrationOutcome.REGISTERED);
    }

    private RequestPostProcessor asMember(long accountId) {
        String email = jdbc.sql("SELECT email FROM account WHERE id = ?").param(accountId).query(String.class)
                .single();
        return user(new SignedIn(LoginKind.MEMBER, accountId, email, "Medlem"));
    }

    private RequestPostProcessor asAdministrator() {
        return user(new SignedIn(LoginKind.ADMINISTRATOR, firstAdministratorId(), firstAdministratorEmail,
                "Ada Admin"));
    }

    private Callable<String> registration(long offerId, long memberId, CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                return "NOT STARTED";
            }
            try {
                return offers.register(offerId, memberId).name();
            } catch (OfferFull e) {
                return "FULL";
            }
        };
    }
}
