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
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/// The requests [HttpBrevo] sends, checked against Brevo's API reference as
/// read on 2026-09-28. Nothing here reaches a real Brevo account, so a field
/// Brevo renames is caught only by the reference, not by this test. The
/// expectations are matched in any order, since the attributes are created
/// in the order of a map.
class HttpBrevoTest {

    private static final String API = "https://api.brevo.test/v3";

    private MockRestServiceServer server;
    private HttpBrevo brevo;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        brevo = new HttpBrevo(settings("key-1", 7L, 9L), builder);
    }

    @Test
    void theFirstContactCreatesTheAttributesThenUpdatesByMemberId() {
        for (String attribute : new String[] {"NAMN", "PAID_YEAR", "LAST_SHIFT", "OFFERS"}) {
            server.expect(requestTo(API + "/contacts/attributes/normal/" + attribute))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("api-key", "key-1"))
                    .andExpect(content().json("LAST_SHIFT".equals(attribute)
                            ? "{\"type\":\"date\"}" : "{\"type\":\"text\"}", true))
                    .andRespond("PAID_YEAR".equals(attribute)
                            ? withBadRequest().contentType(MediaType.APPLICATION_JSON)
                                    .body("{\"code\":\"invalid_parameter\",\"message\":\"Attribute exists\"}")
                            : withStatus(HttpStatus.CREATED));
        }
        expectExisting(false);
        server.expect(requestTo(API + "/contacts/42?identifierType=ext_id"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("""
                        {"attributes":{"NAMN":"Karin Holm","PAID_YEAR":"2026",
                          "LAST_SHIFT":"2026-09-20","OFFERS":";3;5;"},
                         "listIds":[7]}""", true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        expectExisting(false);
        server.expect(requestTo(API + "/contacts/42?identifierType=ext_id"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("""
                        {"attributes":{"NAMN":"Karin Holm","PAID_YEAR":"",
                          "LAST_SHIFT":null,"OFFERS":""},
                         "listIds":[7]}""", true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        brevo.saveContact(new Brevo.Contact(42, "KARIN@example.test", "Karin Holm", 2026,
                LocalDate.of(2026, 9, 20), List.of(3L, 5L)));
        brevo.saveContact(new Brevo.Contact(42, "karin@example.test", "Karin Holm", null, null, List.of()));
        server.verify();
    }

    /// Brevo resubscribes a blocklisted contact whose address changes, so the
    /// address goes only with a change, and then with the blocklisting. The
    /// unchanged address of a blocklisted contact is not sent at all.
    @Test
    void anUnsubscribedContactStaysUnsubscribedWhenItsAddressChanges() {
        attributesExist();
        expectExisting(true);
        server.expect(requestTo(API + "/contacts/42?identifierType=ext_id"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("""
                        {"attributes":{"EMAIL":"ny@example.test","NAMN":"Karin Holm","PAID_YEAR":"",
                          "LAST_SHIFT":null,"OFFERS":""},
                         "listIds":[7],"emailBlacklisted":true}""", true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        expectExisting(true);
        server.expect(requestTo(API + "/contacts/42?identifierType=ext_id"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("""
                        {"attributes":{"NAMN":"Karin Holm","PAID_YEAR":"","LAST_SHIFT":null,"OFFERS":""},
                         "listIds":[7]}""", true))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        brevo.saveContact(new Brevo.Contact(42, "ny@example.test", "Karin Holm", null, null, List.of()));
        brevo.saveContact(new Brevo.Contact(42, "karin@example.test", "Karin Holm", null, null, List.of()));
        server.verify();
    }

    @Test
    void aContactBrevoDoesNotHaveIsCreatedWithTheMemberId() {
        attributesExist();
        server.expect(requestTo(API + "/contacts/42?identifierType=ext_id"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"document_not_found\"}"));
        server.expect(requestTo(API + "/contacts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"email":"karin@example.test","ext_id":"42","listIds":[7],"updateEnabled":true,
                         "attributes":{"NAMN":"Karin Holm","PAID_YEAR":"","LAST_SHIFT":null,"OFFERS":""}}""", true))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":5}"));

        brevo.saveContact(new Brevo.Contact(42, "karin@example.test", "Karin Holm", null, null, List.of()));
        server.verify();
    }

    @Test
    void deletingAContactThatIsGoneIsNoError() {
        server.expect(requestTo(API + "/contacts/42?identifierType=ext_id"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(requestTo(API + "/contacts/43?identifierType=ext_id"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        brevo.deleteContact(42);
        brevo.deleteContact(43);
        server.verify();
    }

    @Test
    void listsTheSegmentsByName() {
        server.expect(requestTo(API + "/contacts/segments?limit=50&offset=0&sort=asc"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"count":2,"segments":[{"id":12,"segmentName":"Volontärer","categoryName":"x"},
                          {"id":11,"segmentName":"betalat 2026","categoryName":"x"}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(brevo.segments()).containsExactly(
                new Brevo.Segment(11, "betalat 2026"), new Brevo.Segment(12, "Volontärer"));
    }

    /// Every page is read, a contact off the list counts as a non-member, and
    /// an unsubscribed member is not counted as reached.
    @Test
    void theReachCountsEveryPageOfTheSegment() {
        StringBuilder full = new StringBuilder("{\"contacts\":[");
        for (int i = 0; i < 1000; i++) {
            full.append(i == 0 ? "" : ",").append("{\"id\":").append(i).append(",\"listIds\":[7,9]}");
        }
        full.append("]}");
        server.expect(requestTo(API + "/contacts?segmentId=12&limit=1000&offset=0"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(full.toString(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/contacts?segmentId=12&limit=1000&offset=1000"))
                .andRespond(withSuccess("""
                        {"contacts":[{"id":2000,"listIds":[7],"emailBlacklisted":true},
                          {"id":2001,"listIds":[9]},{"id":2002,"listIds":[]}]}""", MediaType.APPLICATION_JSON));

        assertThat(brevo.reach(12L)).isEqualTo(new MailingReach(1000, 2));
        server.verify();
    }

    @Test
    void anAnswerWithoutContactsIsNoAnswer() {
        server.expect(requestTo(API + "/contacts?segmentId=12&limit=1000&offset=0"))
                .andRespond(withSuccess("{\"count\":3}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> brevo.reach(12L)).isInstanceOf(BrevoUnavailable.class);
    }

    @Test
    void theListsReachAsksForTheList() {
        server.expect(requestTo(API + "/contacts?listIds=7&limit=1000&offset=0"))
                .andRespond(withSuccess("{\"contacts\":[{\"id\":1,\"listIds\":[7]}]}", MediaType.APPLICATION_JSON));

        assertThat(brevo.reach(null)).isEqualTo(new MailingReach(1, 0));
    }

    @Test
    void aDraftGoesToTheListOrToASegmentFromTheSender() {
        server.expect(requestTo(API + "/emailCampaigns"))
                .andExpect(content().json("""
                        {"name":"Utskick","subject":"Höst","htmlContent":"<p>Hej</p>",
                         "sender":{"name":"Teater i Huskvarna","email":"utskick@example.test"},
                         "recipients":{"listIds":[7]}}""", true))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"id\":300}"));
        server.expect(requestTo(API + "/emailCampaigns"))
                .andExpect(content().json("""
                        {"name":"Utskick","subject":"Höst","htmlContent":"<p>Hej</p>",
                         "sender":{"name":"Teater i Huskvarna","email":"utskick@example.test"},
                         "recipients":{"segmentIds":[12]}}""", true))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"id\":301}"));

        assertThat(brevo.createDraft(new Brevo.Campaign("Utskick", "Höst", "<p>Hej</p>", null))).isEqualTo(300);
        assertThat(brevo.createDraft(new Brevo.Campaign("Utskick", "Höst", "<p>Hej</p>", 12L))).isEqualTo(301);
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
        attributesExist();
        expectExisting(false);
        server.expect(requestTo(API + "/contacts/42?identifierType=ext_id"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"duplicate_parameter\"}"));

        assertThatThrownBy(() -> brevo.saveContact(
                new Brevo.Contact(42, "karin@example.test", "Karin Holm", null, null, List.of())))
                .isInstanceOf(BrevoUnavailable.class);
    }

    @Test
    void refusesToStartWithoutItsSettings() {
        assertThatThrownBy(() -> new HttpBrevo(settings(" ", null, null), RestClient.builder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BREVO_API_KEY, BREVO_LIST_ID, BREVO_TEST_LIST_ID");
    }

    private void expectExisting(boolean blacklisted) {
        server.expect(requestTo(API + "/contacts/42?identifierType=ext_id"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":5,\"email\":\"karin@example.test\",\"emailBlacklisted\":"
                        + blacklisted + "}", MediaType.APPLICATION_JSON));
    }

    private void attributesExist() {
        for (String attribute : new String[] {"NAMN", "PAID_YEAR", "LAST_SHIFT", "OFFERS"}) {
            server.expect(requestTo(API + "/contacts/attributes/normal/" + attribute))
                    .andRespond(withBadRequest());
        }
    }

    private static BrevoSettings settings(String key, Long folder, Long testList) {
        return new BrevoSettings(BrevoSettings.Api.HTTP, key, URI.create(API), folder, testList,
                "Teater i Huskvarna", "utskick@example.test");
    }
}
