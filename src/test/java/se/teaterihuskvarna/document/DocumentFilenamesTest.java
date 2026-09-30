package se.teaterihuskvarna.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/// The name an upload claims is untrusted; these cases are the ways it can
/// break a path or a header.
class DocumentFilenamesTest {

    @Test
    void keepsOnlyTheLastPathSegment() {
        assertThat(DocumentFilenames.sanitize("..\\..\\windows\\evil.pdf")).isEqualTo("evil.pdf");
        assertThat(DocumentFilenames.sanitize("../../etc/passwd")).isEqualTo("passwd.pdf");
    }

    @Test
    void dropsQuotesControlCharactersAndLeadingDots() {
        assertThat(DocumentFilenames.sanitize("ev\"il\r\n.pdf")).isEqualTo("evil.pdf");
        assertThat(DocumentFilenames.sanitize("...hidden.PDF")).isEqualTo("hidden.pdf");
        assertThat(DocumentFilenames.sanitize("a<b>c:d|e?f*.pdf")).isEqualTo("abcdef.pdf");
    }

    @Test
    void aMissingOrEmptyNameGetsTheFallback() {
        assertThat(DocumentFilenames.sanitize(null)).isEqualTo("dokument.pdf");
        assertThat(DocumentFilenames.sanitize("")).isEqualTo("dokument.pdf");
        assertThat(DocumentFilenames.sanitize("C:\\mapp\\")).isEqualTo("dokument.pdf");
        assertThat(DocumentFilenames.sanitize(".pdf")).isEqualTo("dokument.pdf");
    }

    @Test
    void aLongNameIsCutToTheColumnWidth() {
        String name = DocumentFilenames.sanitize("x".repeat(400) + ".pdf");

        assertThat(name).hasSize(DocumentFilenames.MAX_LENGTH).endsWith("x.pdf");
    }

    @Test
    void theHeaderCarriesAnAsciiNameAndTheEncodedRealOne() {
        assertThat(DocumentFilenames.contentDisposition("Årsmöte 2026.pdf"))
                .isEqualTo("attachment; filename=\"Arsmote_2026.pdf\"; filename*=UTF-8''%C3%85rsm%C3%B6te%202026.pdf");
    }
}
