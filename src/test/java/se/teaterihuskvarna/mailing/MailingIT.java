package se.teaterihuskvarna.mailing;

import static java.util.Objects.requireNonNull;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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
/// - Preparing makes a draft to the members' list or to a Brevo segment, and
///   logs it.
/// - The audiences are the list and Brevo's segments, and a segment holding
///   anyone not on the members' list is refused, as is an audience that
///   reaches no member.
/// - A test goes to the signed-in administrator.
/// - The log shows what Brevo last reported, keeps it when Brevo cannot be
///   reached, and stops asking about a mailing Brevo no longer changes.
/// - Brevo refusing a draft logs no mailing, and refusing a test says so.
class MailingIT extends IntegrationTestSupport {

    @Autowired
    private Brevo brevo;

    private FakeBrevo fake;

    @BeforeEach
    void fake() {
        fake = (FakeBrevo) brevo;
        fake.segments(List.of());
        // The fake's list is every member with an account.
        insertAccount("Maja Medlem", "maja@example.test");
    }

    /// The fake outlives the test, and every other IT shares it.
    @AfterEach
    void answerAgain() {
        fake.refuseCampaigns(false);
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
                        .content("{\"subject\": \"Hej\", \"intro\": \"Hej\", \"audience\": \"LIST\"}"))
                .andExpect(status().isForbidden());

        assertThat(rowsIn("mailing")).isZero();
    }

    @Test
    void thePagesLinkToTheEditorsForActionsCompletedElsewhere() throws Exception {
        mockMvc.perform(get("/admin").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"https://studio.example.test/studio/\"")))
                .andExpect(content().string(containsString("href=\"https://app.brevo.com/contact/segment\"")))
                .andExpect(content().string(containsString("href=\"https://app.brevo.com/campaigns/listing/email\"")));
        mockMvc.perform(get("/admin/utskick").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"https://studio.example.test/studio/\"")))
                .andExpect(content().string(containsString("href=\"https://app.brevo.com/contact/segment\"")));
    }

    @Test
    void eachEventAndNewsItemToChooseGivesItsDateInBrackets() throws Exception {
        // A title often ends in its own punctuation, which a comma after it doubled.
        mockMvc.perform(get("/admin/utskick").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ny webbplats (")))
                .andExpect(content().string(containsString("Kulturnatten på torget (")))
                .andExpect(content().string(not(containsString("Ny webbplats,"))));
    }

    @Test
    void thePreviewShowsTheChosenContentAndTouchesNothing() throws Exception {
        mockMvc.perform(post("/admin/utskick/forhandsgranska").with(asAdministrator()).with(csrf())
                        .param("audience", "LIST")
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
                        .content("{\"subject\": \"Hej\", \"audience\": \"LIST\", \"events\": [\"kulturnatten\"]}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("http://localhost/evenemang/kulturnatten")));

        assertThat(rowsIn("mailing")).isZero();
    }

    @Test
    void aMailingNeedsSomethingToSayAndOnlyPublishedContent() throws Exception {
        insertAccount("Karin Holm", "karin@example.test");

        mockMvc.perform(post("/api/admin/mailings").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\": \"Hej\", \"audience\": \"LIST\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/mailings").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\": \"Hej\", \"audience\": \"LIST\", \"news\": [\"framtida\"]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/mailings").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\": \"Hej\", \"intro\": \"Hej\", \"audience\": \"NOBODY\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/utskick").with(asAdministrator()).with(csrf())
                        .param("audience", "LIST")
                        .param("subject", "Hej"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Skriv en egen text eller välj minst ett evenemang eller en nyhet.")));

        assertThat(rowsIn("mailing")).isZero();
    }

    @Test
    void preparingMakesADraftToTheListAndLogsIt() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/utskick").with(asAdministrator()).with(csrf())
                        .param("audience", "LIST")
                        .param("subject", "Höstens program")
                        .param("intro", "Hej alla!")
                        .param("events", "kulturnatten"))
                .andExpect(flash().attribute("notice", "Utkastet finns i Brevo."))
                .andReturn();
        assertThat(locationOf(result)).startsWith("/admin/utskick/");

        long campaignId = jdbc.sql("SELECT brevo_campaign_id FROM mailing").query(Long.class).single();
        Brevo.Campaign campaign = fake.campaign(campaignId);
        assertThat(campaign).isNotNull();
        assertThat(campaign.segmentId()).isNull();
        assertThat(campaign.subject()).isEqualTo("Höstens program");
        assertThat(campaign.html()).contains("Kulturnatten på torget").contains("{{ unsubscribe }}");
        assertThat(jdbc.sql("SELECT created_by FROM mailing").query(Long.class).single())
                .isEqualTo(firstAdministratorId());

        mockMvc.perform(get(requireNonNull(locationOf(result))).with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Utkast")))
                .andExpect(content().string(containsString("Alla medlemmar med konto")))
                .andExpect(content().string(containsString("Skicka ett test till mig")))
                .andExpect(content().string(not(containsString("Siffror från Brevo"))));
    }

    @Test
    void aSegmentMadeInBrevoIsAnAudience() throws Exception {
        fake.segments(List.of(new Brevo.Segment(7, "Betalat 2026")));
        fake.reach(7, new MailingReach(3, 0));

        mockMvc.perform(get("/api/admin/mailings/audiences").with(asAdministrator()))
                .andExpect(jsonPath("$.segmentsUnavailable").value(false))
                .andExpect(jsonPath("$.choices[0].value").value("LIST"))
                .andExpect(jsonPath("$.choices[1].value").value("SEGMENT:7"))
                .andExpect(jsonPath("$.choices[1].name").value("Betalat 2026"));
        mockMvc.perform(get("/admin/utskick").with(asAdministrator()))
                .andExpect(content().string(containsString("<option value=\"SEGMENT:7\"")));

        long campaignId = prepare("SEGMENT:7");
        Brevo.Campaign campaign = fake.campaign(campaignId);
        assertThat(campaign).isNotNull();
        assertThat(campaign.segmentId()).isEqualTo(7L);
        assertThat(jdbc.sql("SELECT audience_name FROM mailing").query(String.class).single())
                .isEqualTo("Betalat 2026");

        mockMvc.perform(post("/api/admin/mailings").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\": \"Hej\", \"intro\": \"Hej\", \"audience\": \"SEGMENT:8\"}"))
                .andExpect(status().isBadRequest());
    }

    /// A segment with a contact outside the members' list is refused, and
    /// the preview says so and how many it would reach before anything is made.
    @Test
    void aSegmentWithNonMembersIsRefused() throws Exception {
        fake.segments(List.of(new Brevo.Segment(7, "Betalat 2026"), new Brevo.Segment(8, "Alla kontakter")));
        fake.reach(7, new MailingReach(12, 0));
        fake.reach(8, new MailingReach(12, 2));

        mockMvc.perform(post("/admin/utskick/forhandsgranska").with(asAdministrator()).with(csrf())
                        .param("audience", "SEGMENT:7").param("subject", "Hej").param("intro", "Hej"))
                .andExpect(content().string(containsString("12 medlemmar")))
                .andExpect(content().string(containsString("formaction=\"/admin/utskick\"")));
        mockMvc.perform(post("/admin/utskick/forhandsgranska").with(asAdministrator()).with(csrf())
                        .param("audience", "SEGMENT:8").param("subject", "Hej").param("intro", "Hej"))
                .andExpect(content().string(containsString("Segmentet innehåller 2 kontakter")))
                .andExpect(content().string(not(containsString("formaction=\"/admin/utskick\""))));
        mockMvc.perform(get("/api/admin/mailings/reach").param("audience", "SEGMENT:8").with(asAdministrator()))
                .andExpect(jsonPath("$.members").value(12))
                .andExpect(jsonPath("$.nonMembers").value(2));

        mockMvc.perform(post("/admin/utskick").with(asAdministrator()).with(csrf())
                        .param("audience", "SEGMENT:8").param("subject", "Hej").param("intro", "Hej"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Segmentet innehåller 2 kontakter")));
        mockMvc.perform(post("/api/admin/mailings").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\": \"Hej\", \"intro\": \"Hej\", \"audience\": \"SEGMENT:8\"}"))
                .andExpect(status().isConflict());
        assertThat(rowsIn("mailing")).isZero();
    }

    /// An audience in which no member can receive the mail, because it is
    /// empty or everyone in it has unsubscribed, makes no draft and says why
    /// instead of asking to try again: #200.
    @Test
    void anAudienceThatReachesNoMemberMakesNoDraft() throws Exception {
        jdbc.sql("DELETE FROM account").update();
        fake.segments(List.of(new Brevo.Segment(9, "Ingen kvar")));
        fake.reach(9, new MailingReach(0, 0));
        int campaigns = fake.campaignCount();

        for (String audience : List.of("LIST", "SEGMENT:9")) {
            mockMvc.perform(post("/admin/utskick/forhandsgranska").with(asAdministrator()).with(csrf())
                            .param("audience", audience).param("subject", "Hej").param("intro", "Hej"))
                    .andExpect(content().string(containsString("<dd>0 medlemmar</dd>")))
                    .andExpect(content().string(containsString("Utskicket skulle nå 0 medlemmar.")))
                    .andExpect(content().string(not(containsString("formaction=\"/admin/utskick\""))));
            mockMvc.perform(post("/admin/utskick").with(asAdministrator()).with(csrf())
                            .param("audience", audience).param("subject", "Hej").param("intro", "Hej"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Utskicket skulle nå 0 medlemmar.")))
                    .andExpect(content().string(not(containsString("Försök igen om en stund"))));
            mockMvc.perform(post("/api/admin/mailings").with(asAdministrator()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subject\": \"Hej\", \"intro\": \"Hej\", \"audience\": \"" + audience + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Audience reaches no one"));
        }
        assertThat(rowsIn("mailing")).isZero();
        assertThat(fake.campaignCount()).isEqualTo(campaigns);
    }

    @Test
    void aTestGoesToTheSignedInAdministrator() throws Exception {
        prepare("LIST");
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
        prepare("LIST");
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
        mockMvc.perform(get("/admin/utskick/" + id).with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Siffror från Brevo")))
                .andExpect(content().string(not(containsString("Skicka ett test till mig"))));
    }

    /// A mailing is saved only once its campaign exists in Brevo.
    @Test
    void aDraftBrevoRefusesLogsNoMailing() throws Exception {
        fake.refuseCampaigns(true);

        mockMvc.perform(post("/admin/utskick").with(asAdministrator()).with(csrf())
                        .param("audience", "LIST").param("subject", "Hej").param("intro", "Hej"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Brevo svarade inte som väntat.")));
        mockMvc.perform(post("/api/admin/mailings").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\": \"Hej\", \"intro\": \"Hej\", \"audience\": \"LIST\"}"))
                .andExpect(status().isBadGateway());

        assertThat(rowsIn("mailing")).isZero();
    }

    @Test
    void aTestBrevoRefusesSaysSo() throws Exception {
        long id = mailingOf(prepare("LIST"));
        int tests = fake.tests().size();
        fake.refuseCampaigns(true);

        mockMvc.perform(post("/admin/utskick/" + id + "/testa").with(asAdministrator()).with(csrf()))
                .andExpect(flash().attribute("error", "Brevo svarade inte som väntat. Försök igen om en stund."))
                .andExpect(flash().attributeCount(1));
        mockMvc.perform(post("/api/admin/mailings/" + id + "/test").with(asAdministrator()).with(csrf()))
                .andExpect(status().isBadGateway());

        assertThat(fake.tests()).hasSize(tests);
    }

    @Test
    void theLogKeepsBrevosLastAnswerWhenBrevoCannotBeReached() throws Exception {
        long campaignId = prepare("LIST");
        long id = mailingOf(campaignId);
        // Sent yesterday, so its numbers can still change.
        Instant sentAt = Instant.now().minus(Duration.ofDays(1));
        fake.markSent(campaignId, new CampaignReport("sent", sentAt, 4, 4, 3, 0, 0));
        lastAskedAnHourAgo();
        mockMvc.perform(get("/api/admin/mailings/" + id).with(asAdministrator()))
                .andExpect(jsonPath("$.uniqueViews").value(3));

        fake.markSent(campaignId, new CampaignReport("sent", sentAt, 4, 4, 4, 0, 0));
        fake.refuseCampaigns(true);
        lastAskedAnHourAgo();

        mockMvc.perform(get("/api/admin/mailings").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("sent"))
                .andExpect(jsonPath("$[0].uniqueViews").value(3));
        mockMvc.perform(get("/admin/utskick").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Skickat")));
        mockMvc.perform(get("/admin/utskick/" + id).with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Siffror från Brevo")));

        fake.refuseCampaigns(false);
        mockMvc.perform(get("/api/admin/mailings/" + id).with(asAdministrator()))
                .andExpect(jsonPath("$.uniqueViews").value(4));
    }

    /// Brevo stops changing a campaign once it is cancelled, or a week after
    /// it was sent, so the log stops asking. A week is the boundary: a mailing
    /// sent six days ago still gets its new numbers.
    @Test
    void aMailingSentOverAWeekAgoOrCancelledIsNotAskedAgain() throws Exception {
        Instant now = Instant.now();
        long sixDays = prepare("LIST");
        long eightDays = prepare("LIST");
        long cancelled = prepare("LIST");
        fake.markSent(sixDays, new CampaignReport("sent", now.minus(Duration.ofDays(6)), 1, 1, 1, 0, 0));
        fake.markSent(eightDays, new CampaignReport("sent", now.minus(Duration.ofDays(8)), 1, 1, 1, 0, 0));
        fake.markSent(cancelled, new CampaignReport("cancelled", null, 0, 0, 0, 0, 0));
        lastAskedAnHourAgo();
        mockMvc.perform(get("/api/admin/mailings").with(asAdministrator())).andExpect(status().isOk());

        fake.markSent(sixDays, new CampaignReport("sent", now.minus(Duration.ofDays(6)), 1, 1, 5, 0, 0));
        fake.markSent(eightDays, new CampaignReport("sent", now.minus(Duration.ofDays(8)), 1, 1, 5, 0, 0));
        fake.markSent(cancelled, new CampaignReport("draft", null, 0, 0, 0, 0, 0));
        lastAskedAnHourAgo();

        mockMvc.perform(get("/api/admin/mailings/" + mailingOf(sixDays)).with(asAdministrator()))
                .andExpect(jsonPath("$.uniqueViews").value(5));
        mockMvc.perform(get("/api/admin/mailings/" + mailingOf(eightDays)).with(asAdministrator()))
                .andExpect(jsonPath("$.uniqueViews").value(1));
        mockMvc.perform(get("/api/admin/mailings/" + mailingOf(cancelled)).with(asAdministrator()))
                .andExpect(jsonPath("$.status").value("cancelled"));
    }

    /// [HttpBrevo] accepts only the statuses it knows, and each needs a
    /// Swedish name, or the log page fails for a mailing in that status.
    @Test
    void everyStatusBrevoReportsHasASwedishName() throws Exception {
        // One mailing each: once archived or cancelled, a mailing is not asked again.
        Map<String, Long> mailingByStatus = new LinkedHashMap<>();
        for (String status : HttpBrevo.STATUSES) {
            long campaignId = prepare("LIST");
            fake.markSent(campaignId, new CampaignReport(status, null, 0, 0, 0, 0, 0));
            mailingByStatus.put(status, mailingOf(campaignId));
        }
        lastAskedAnHourAgo();

        mockMvc.perform(get("/admin/utskick").with(asAdministrator()))
                .andExpect(status().isOk());
        for (Map.Entry<String, Long> mailing : mailingByStatus.entrySet()) {
            mockMvc.perform(get("/admin/utskick/" + mailing.getValue()).with(asAdministrator()))
                    .andExpect(status().isOk());
            assertThat(jdbc.sql("SELECT status FROM mailing WHERE id = ?").param(mailing.getValue())
                    .query(String.class).single()).isEqualTo(mailing.getKey());
        }
    }

    private long mailingOf(long campaignId) {
        return jdbc.sql("SELECT id FROM mailing WHERE brevo_campaign_id = ?").param(campaignId).query(Long.class)
                .single();
    }

    private void lastAskedAnHourAgo() {
        jdbc.sql("UPDATE mailing SET checked_at = ?").param(Timestamp.from(Instant.now().minus(Duration.ofHours(1))))
                .update();
    }

    /// @return the draft campaign's id in Brevo
    private long prepare(String audience) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/mailings").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\": \"Hej\", \"intro\": \"Hej\", \"audience\": \"" + audience + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        return jdbc.sql("SELECT brevo_campaign_id FROM mailing WHERE id = ?").param(id).query(Long.class).single();
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
