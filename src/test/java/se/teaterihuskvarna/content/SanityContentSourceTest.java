package se.teaterihuskvarna.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class SanityContentSourceTest {

    @Test
    void requestsGzipFromSanityAndReadsTheDocuments() {
        RestClient.Builder client = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        ContentSettings settings = new ContentSettings(ContentSettings.Source.SANITY, "project", "dev",
                null, null, Duration.ofSeconds(45), null, null);
        SanityContentSource source = new SanityContentSource(settings, new ObjectMapper(), client);
        server.expect(requestTo(startsWith("https://project.api.sanity.io/v2025-02-19/data/query/dev?")))
                .andExpect(header(HttpHeaders.ACCEPT_ENCODING, "gzip"))
                .andExpect(queryParam("$value", "%22nyhet%22"))
                .andRespond(withSuccess("{\"result\":[{\"_id\":\"news-1\",\"title\":\"Hej\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(source.documents("nyhet", Perspective.PUBLISHED))
                .extracting(node -> node.path("_id").asString())
                .containsExactly("news-1");
        server.verify();
    }
}
