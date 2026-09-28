package se.teaterihuskvarna.content;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

/// Checks the `sanity-webhook-signature` header Sanity sends with a webhook
/// that has a secret: `t=<milliseconds>,v1=<signature>`, where the signature is
/// HMAC-SHA256 with the secret over the timestamp, a dot and the raw body, in
/// base64url without padding. That is what Sanity's own `@sanity/webhook`
/// package computes.
///
/// No limit on the timestamp's age. A replayed webhook can only empty the
/// cache, which the next minute does anyway.
final class WebhookSignature {

    private WebhookSignature() {
    }

    /// @param secret the shared secret, or null or blank when none is set, which fails every check
    /// @param header the header's value, or null when it is missing
    /// @param body   the request body exactly as sent
    /// @return whether Sanity signed this body with this secret
    static boolean valid(@Nullable String secret, @Nullable String header, byte[] body) {
        if (secret == null || secret.isBlank() || header == null) {
            return false;
        }
        String timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            String trimmed = part.strip();
            if (trimmed.startsWith("t=")) {
                timestamp = trimmed.substring(2);
            } else if (trimmed.startsWith("v1=")) {
                signature = trimmed.substring(3);
            }
        }
        if (timestamp == null || signature == null || !timestamp.matches("\\d{1,15}")) {
            return false;
        }
        byte[] expected = sign(secret, timestamp, body);
        byte[] given;
        try {
            given = Base64.getUrlDecoder().decode(signature.replace('+', '-').replace('/', '_').replace("=", ""));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, given);
    }

    /// @param secret    the shared secret
    /// @param timestamp the `t` value
    /// @param body      the raw body
    /// @return the HMAC Sanity would send
    static byte[] sign(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(body);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
