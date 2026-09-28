package se.teaterihuskvarna.mailing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.SignedIn;

/// Proves mailings (R022 to R025) through both adapters, against [FakeBrevo].
///
/// - Only an administrator reaches them.
/// - The preview is the mail with the chosen content and Brevo's unsubscribe
///   placeholder, and touches nothing in Brevo.
/// - Preparing makes one list with each audience address once and a draft to
///   that list, and logs it.
/// - Each audience reaches the members it names, and only those with an account.
/// - A test goes to the signed-in administrator.
/// - The log shows what Brevo last reported.
class MailingIT extends IntegrationTestSupport {

    @Autowired
    private Brevo brevo;

    private FakeBrevo fake;

    @BeforeEach
    void fake() {
        fake = (FakeBrevo) brevo;
    }

    @Test
    void onlyAnAdministratorReachesMailings() throws Exception {
        RequestPostProcessor member = asMember(insertAccount("Karin Holm", "karin@example.test"));

        assertRedirect(mockMvc.perform(get("/admin/utskick")).andReturn(), "/admin/logga-in");
        assertRedirect(mockMvc.perform(get("/admin/utskick").with(member)).andReturn(), "/admin/logga-in");
        mockMvc.perform(get("/api/admin/mailings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/mailings").with(member)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/mailings").with(member).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\": \"Hej\", \"intro\": \"Hej\", \"audience\": \"ALL\"}"))
                .andExpect(status().isForbidden());

        assertThat(rowsIn("mailing")).isZero();
    }

    @Test
    void thePreviewShowsTheChosenContentAndTouchesNothing() throws Exception {
        mockMvc.perform(post("/admin/utskick/forhandsgranska").with(asAdministrator()).with(csrf())
                        .param("audience", "ALL")
                        .param("subject", "Höstens program")
                        .param("intro", "Hej alla!\n\nNu drar vi igång.")
                        .param("events", "kulturnatten")
                        .param("news", "ny-webbplats"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Höstens program")))
                .andExpect(content().string(containsString("Kulturnatten på torget")))
                .andExpect(content().string(containsString("Ny webbplats")))
                .andExpect(content().string(containsString("{{ unsubscribe }}")))
                .andExpect(content().string(not(containsString("Mitt i veckan"))));

        mockMvc.perform(post("/api/admin/mailings/preview").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\": \"Hej\", \"audience\": \"ALL\", \"events\": [\"kulturnatten\"]}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("http://localhost/evenemang/kulturnatten")));

        assertThat(rowsIn("mailing")).isZero();
    }

    @Test
    void aMailingNeedsSomethingToSayAndOnlyPublishedContent() throws Exception {
        insertAccount("Karin Holm", "karin@example.test");

        mockMvc.perform(post("/api/admin/mailings").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\": \"Hej\", \"audience\": \"ALL\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/mailings").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\": \"Hej\", \"audience\": \"ALL\", \"news\": [\"framtida\"]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/mailings").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\": \"Hej\", \"intro\": \"Hej\", \"audience\": \"NOBODY\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/utskick").with(asAdministrator()).with(csrf())
                        .param("audience", "ALL")
                        .param("subject", "Hej"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Skriv en egen text eller välj minst ett evenemang eller en nyhet.")));

        assertThat(rowsIn("mailing")).isZero();
    }

    @Test
    void preparingMakesAListAndADraftAndLogsIt() throws Exception {
        insertAccount("Karin Holm", "karin@example.test");
        insertAccount("Olle Berg", "OLLE@example.test");
        insertMember("Utan Konto");

        MvcResult result = mockMvc.perform(post("/admin/utskick").with(asAdministrator()).with(csrf())
                        .param("audience", "ALL")
                        .param("subject", "Höstens program")
                        .param("intro", "Hej alla!")
                        .param("events", "kulturnatten"))
                .andExpect(flash().attribute("notice", "Utkastet finns i Brevo, med 2 mottagare."))
                .andReturn();
        assertThat(locationOf(result)).startsWith("/admin/utskick/");

        long listId = jdbc.sql("SELECT brevo_list_id FROM mailing").query(Long.class).single();
        long campaignId = jdbc.sql("SELECT brevo_campaign_id FROM mailing").query(Long.class).single();
        assertThat(fake.contacts(listId)).containsExactlyInAnyOrder("karin@example.test", "OLLE@example.test");
        Brevo.Campaign campaign = fake.campaign(campaignId);
        assertThat(campaign).isNotNull();
        assertThat(campaign.listId()).isEqualTo(listId);
        assertThat(campaign.subject()).isEqualTo("Höstens program");
        assertThat(campaign.html()).contains("Kulturnatten på torget").contains("{{ unsubscribe }}");
        assertThat(jdbc.sql("SELECT created_by FROM mailing").query(Long.class).single())
                .isEqualTo(firstAdministratorId());

        mockMvc.perform(get(locationOf(result)).with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Utkast")))
                .andExpect(content().string(containsString("Alla medlemmar")));
    }

    @Test
    void anAudienceWithNobodyToReachIsRefused() throws Exception {
        insertMember("Utan Konto");

        mockMvc.perform(post("/api/admin/mailings").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\": \"Hej\", \"intro\": \"Hej\", \"audience\": \"ALL\"}"))
                .andExpect(status().isConflict());

        assertThat(rowsIn("mailing")).isZero();
    }

    @Test
    void volunteersAreMembersWithAShiftInTheLastYear() throws Exception {
        long karin = memberOf(insertAccount("Karin Holm", "karin@example.test"));
        long olle = memberOf(insertAccount("Olle Berg", "olle@example.test"));
        insertAccount("Aldrig Volontär", "aldrig@example.test");
        book(insertShift(Instant.now().minus(Duration.ofDays(100))), karin);
        book(insertShift(Instant.now().minus(Duration.ofDays(400))), olle);

        long listId = prepare("VOLUNTEERS");

        assertThat(fake.contacts(listId)).containsExactly("karin@example.test");
    }

    @Test
    void anOfferAudienceIsItsRegisteredMembers() throws Exception {
        long karin = memberOf(insertAccount("Karin Holm", "karin@example.test"));
        insertAccount("Olle Berg", "olle@example.test");
        long offer = jdbc.sql("""
                INSERT INTO offer (title, description, published, created_at, updated_at)
                VALUES ('Verkstad', '', true, now(), now()) RETURNING id
                """).query(Long.class).single();
        jdbc.sql("INSERT INTO offer_registration (offer_id, member_id, created_at) VALUES (?, ?, now())")
                .param(offer).param(karin).update();

        mockMvc.perform(get("/api/admin/mailings/audiences").with(asAdministrator()))
                .andExpect(jsonPath("$[?(@.value == 'OFFER:" + offer + "')].name").value("Anmälda till Verkstad"));
        long listId = prepare("OFFER:" + offer);

        assertThat(fake.contacts(listId)).containsExactly("karin@example.test");
    }

    @Test
    void aTestGoesToTheSignedInAdministrator() throws Exception {
        insertAccount("Karin Holm", "karin@example.test");
        prepare("ALL");
        long id = jdbc.sql("SELECT id FROM mailing").query(Long.class).single();
        long campaignId = jdbc.sql("SELECT brevo_campaign_id FROM mailing").query(Long.class).single();

        mockMvc.perform(post("/admin/utskick/" + id + "/testa").with(asAdministrator()).with(csrf()))
                .andExpect(flash().attributeExists("notice"));
        mockMvc.perform(post("/api/admin/mailings/" + id + "/test").with(asAdministrator()).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(fake.tests()).containsExactly(campaignId + " " + firstAdministratorEmail,
                campaignId + " " + firstAdministratorEmail);
        mockMvc.perform(post("/api/admin/mailings/999999/test").with(asAdministrator()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theLogShowsWhatBrevoLastReported() throws Exception {
        insertAccount("Karin Holm", "karin@example.test");
        prepare("ALL");
        long id = jdbc.sql("SELECT id FROM mailing").query(Long.class).single();
        long campaignId = jdbc.sql("SELECT brevo_campaign_id FROM mailing").query(Long.class).single();
        Instant sentAt = Instant.parse("2026-09-20T08:00:00Z");
        fake.markSent(campaignId, new CampaignReport("sent", sentAt, 1, 1, 1, 0, 0));

        mockMvc.perform(get("/api/admin/mailings/" + id).with(asAdministrator()))
                .andExpect(jsonPath("$.status").value("draft"));

        jdbc.sql("UPDATE mailing SET checked_at = ?").param(Timestamp.from(Instant.now().minus(Duration.ofHours(1))))
                .update();
        mockMvc.perform(get("/api/admin/mailings").with(asAdministrator()))
                .andExpect(jsonPath("$[0].status").value("sent"))
                .andExpect(jsonPath("$[0].uniqueViews").value(1))
                .andExpect(jsonPath("$[0].sentAt").value("2026-09-20T08:00:00Z"));
        mockMvc.perform(get("/admin/utskick").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Skickat")))
                .andExpect(content().string(containsString("20 september 2026 kl. 10:00")));
    }

    private long prepare(String audience) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/mailings").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\": \"Hej\", \"intro\": \"Hej\", \"audience\": \"" + audience + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        return jdbc.sql("SELECT brevo_list_id FROM mailing WHERE id = ?").param(id).query(Long.class).single();
    }

    private long insertShift(Instant startsAt) {
        return jdbc.sql("""
                INSERT INTO volunteer_shift (event_id, event_title, event_slug, task, starts_at, ends_at, places,
                    created_at)
                VALUES ('evenemang-varshow', 'Vårshowen', 'varshowen', 'SERVERING', ?, ?, 5, now())
                RETURNING id
                """)
                .param(Timestamp.from(startsAt))
                .param(Timestamp.from(startsAt.plus(Duration.ofHours(3))))
                .query(Long.class)
                .single();
    }

    private void book(long shiftId, long memberId) {
        jdbc.sql("INSERT INTO volunteer_booking (shift_id, member_id, created_at) VALUES (?, ?, now())")
                .param(shiftId).param(memberId).update();
    }

    private long memberOf(long accountId) {
        return jdbc.sql("SELECT member_id FROM account WHERE id = ?").param(accountId).query(Long.class).single();
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
}
