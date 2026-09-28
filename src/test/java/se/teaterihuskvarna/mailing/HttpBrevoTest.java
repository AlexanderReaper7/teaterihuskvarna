package se.teaterihuskvarna.mailing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/// The requests [HttpBrevo] sends, checked against Brevo's API reference as
/// read on 2026-09-28. Nothing here reaches a real Brevo account, so a field
/// Brevo renames is caught only by the reference, not by this test.
class HttpBrevoTest {

    private static final String API = "https://api.brevo.test/v3";

    private MockRestServiceServer server;
    private HttpBrevo brevo;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        brevo = new HttpBrevo(settings("key-1", 7L, 9L), builder);
    }

    @Test
    void createsAListInTheFolderWithTheKey() {
        server.expect(requestTo(API + "/contacts/lists"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "key-1"))
                .andExpect(content().json("{\"name\":\"Utskick 2026-09-28 Höst\",\"folderId\":7}", true))
                .andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON));

        assertThat(brevo.createList("Utskick 2026-09-28 Höst")).isEqualTo(42);
        server.verify();
    }

    @Test
    /// Brevo's reference says `listIds` here adds the contact to those lists.
    /// Whether it also keeps the contact's other lists is Brevo's behaviour,
    /// which a mock server cannot show; this checks only what is sent.
    void addsAContactToTheListAndUpdatesAnExistingOne() {
        server.expect(requestTo(API + "/contacts"))
                .andExpect(content().json(
                        "{\"email\":\"karin@example.test\",\"listIds\":[42],\"updateEnabled\":true}", true))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"id\":5}"));

        brevo.addContact("karin@example.test", 42);
        server.verify();
    }

    @Test
    void createsADraftToTheListFromTheSender() {
        server.expect(requestTo(API + "/emailCampaigns"))
                .andExpect(content().json("""
                        {"name":"Utskick","subject":"Höst","htmlContent":"<p>Hej</p>",
                         "sender":{"name":"Teater i Huskvarna","email":"utskick@example.test"},
                         "recipients":{"listIds":[42]}}""", true))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"id\":300}"));

        assertThat(brevo.createDraft(new Brevo.Campaign("Utskick", "Höst", "<p>Hej</p>", 42))).isEqualTo(300);
        server.verify();
    }

    @Test
    void aTestSendPutsTheAddressOnTheTestListFirst() {
        server.expect(requestTo(API + "/contacts"))
                .andExpect(content().json(
                        "{\"email\":\"admin@example.test\",\"listIds\":[9],\"updateEnabled\":true}", true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(requestTo(API + "/emailCampaigns/300/sendTest"))
                .andExpect(content().json("{\"emailTo\":[\"admin@example.test\"]}", true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        brevo.sendTest(300, "admin@example.test");
        server.verify();
    }

    @Test
    void readsTheStatusAndTheGlobalStatistics() {
        server.expect(requestTo(API + "/emailCampaigns/300?statistics=globalStats&excludeHtmlContent=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":300,"status":"sent","sentDate":"2026-09-20T08:00:00.000Z",
                         "statistics":{"globalStats":{"sent":12,"delivered":11,"uniqueViews":6,
                           "unsubscriptions":1,"hardBounces":1}}}""", MediaType.APPLICATION_JSON));

        assertThat(brevo.report(300)).isEqualTo(
                new CampaignReport("sent", Instant.parse("2026-09-20T08:00:00Z"), 12, 11, 6, 1, 1));
    }

    @Test
    void aDraftHasNoSentDateAndNoNumbers() {
        server.expect(requestTo(API + "/emailCampaigns/300?statistics=globalStats&excludeHtmlContent=true"))
                .andRespond(withSuccess("{\"id\":300,\"status\":\"draft\"}", MediaType.APPLICATION_JSON));

        assertThat(brevo.report(300)).isEqualTo(new CampaignReport("draft", null, 0, 0, 0, 0, 0));
    }

    @Test
    void anUnknownStatusIsNoAnswer() {
        server.expect(requestTo(API + "/emailCampaigns/300?statistics=globalStats&excludeHtmlContent=true"))
                .andRespond(withSuccess("{\"id\":300,\"status\":\"exploded\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> brevo.report(300)).isInstanceOf(BrevoUnavailable.class)
                .hasMessageContaining("exploded");
    }

    @Test
    void aRefusalBecomesBrevoUnavailable() {
        server.expect(requestTo(API + "/contacts/lists"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"invalid_parameter\"}"));

        assertThatThrownBy(() -> brevo.createList("x")).isInstanceOf(BrevoUnavailable.class);
    }

    @Test
    void refusesToStartWithoutItsSettings() {
        assertThatThrownBy(() -> new HttpBrevo(settings(" ", null, null), RestClient.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BREVO_API_KEY, BREVO_FOLDER_ID, BREVO_TEST_LIST_ID");
    }

    private static BrevoSettings settings(String key, Long folder, Long testList) {
        return new BrevoSettings(BrevoSettings.Api.HTTP, key, URI.create(API), folder, testList,
                "Teater i Huskvarna", "utskick@example.test");
    }
}
