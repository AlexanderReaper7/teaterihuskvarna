package se.teaterihuskvarna.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/// The header Sanity sends, checked against a value computed independently of
/// [WebhookSignature#sign], so a mistake in both cannot cancel out.
class WebhookSignatureTest {

    private static final byte[] BODY = "{\"_id\":\"a\"}".getBytes(StandardCharsets.UTF_8);

    /// `printf '1700000000000.{"_id":"a"}' | openssl dgst -sha256 -hmac hemlig -binary | basenc --base64url`,
    /// padding removed.
    private static final String KNOWN = "t=1700000000000,v1=psn75aRt9EUUvQjyQNIs3cDdBjHEhTpzg3ZRum5pyNo";

    @Test
    void theSignatureOverTimestampAndBodyIsValid() {
        assertThat(WebhookSignature.valid("hemlig", KNOWN, BODY)).isTrue();
    }

    @Test
    void paddedStandardBase64IsAcceptedToo() {
        assertThat(WebhookSignature.valid("hemlig", KNOWN + "=", BODY)).isTrue();
    }

    @Test
    void anotherBodyOrSecretIsNot() {
        assertThat(WebhookSignature.valid("hemlig", KNOWN, "{}".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(WebhookSignature.valid("annan", KNOWN, BODY)).isFalse();
    }

    @Test
    void noSecretFailsEverything() {
        assertThat(WebhookSignature.valid(null, KNOWN, BODY)).isFalse();
        assertThat(WebhookSignature.valid(" ", KNOWN, BODY)).isFalse();
    }

    @Test
    void aMalformedHeaderIsNotValid() {
        assertThat(WebhookSignature.valid("hemlig", null, BODY)).isFalse();
        assertThat(WebhookSignature.valid("hemlig", "v1=abc", BODY)).isFalse();
        assertThat(WebhookSignature.valid("hemlig", "t=x,v1=abc", BODY)).isFalse();
        assertThat(WebhookSignature.valid("hemlig", "t=1,v1=!!!", BODY)).isFalse();
    }
}
