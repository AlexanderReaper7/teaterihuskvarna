package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import se.teaterihuskvarna.content.ContentService;
import se.teaterihuskvarna.login.MailSettings;

/// The sitemap is XML that a parser reads, whatever an editor typed as a slug.
class DiscoveryPageControllerTest {

    @Test
    void aSlugWithSwedishLettersStaysReadableXml() throws Exception {
        ContentService content = mock(ContentService.class);
        when(content.publicPaths()).thenReturn(List.of("/nyheter/vårens-kaffe&te"));
        DiscoveryPageController controller = new DiscoveryPageController(content,
                new MailSettings(URI.create("https://teaterihuskvarna.se"), "webb@teaterihuskvarna.se"));

        Document sitemap = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(controller.sitemap().getBytes(StandardCharsets.UTF_8)));

        assertThat(sitemap.getElementsByTagName("loc").item(0).getTextContent())
                .isEqualTo("https://teaterihuskvarna.se/nyheter/vårens-kaffe&te");
    }
}
