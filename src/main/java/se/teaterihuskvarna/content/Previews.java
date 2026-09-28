package se.teaterihuskvarna.content;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/// Who may see drafts: R009, per `docs/decisions/0021-content-from-sanity.md`.
///
/// The Studio's Presentation tool writes a secret to the dataset and opens the
/// site with it. [#start] checks the secret with Sanity and hands back a pass,
/// a signed expiry time, which the browser keeps in a cookie and a REST client
/// sends in a header. The pass is not stored anywhere, so it needs no session:
/// the Studio shows the site in an iframe from another site, where the browser
/// does not send the `SameSite=Lax` session cookie anyway.
///
/// The signing key is made at startup and never leaves memory, so a restart
/// ends every preview, and the editor opens the preview again.
///
/// A service like any other, so the REST API offers previews too
/// (`docs/decisions/0014-one-service-layer-two-adapters.md`).
@Service
public class Previews {

    /// The cookie that carries the pass.
    public static final String COOKIE = "forhandsgranskning";

    /// How long a pass lasts: as long as the Studio's secret does.
    public static final Duration LIFETIME = Duration.ofHours(1);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ContentSource source;
    private final Clock clock;
    private final byte[] key = new byte[32];

    Previews(ContentSource source, Clock clock) {
        this.source = source;
        this.clock = clock;
        RANDOM.nextBytes(key);
    }

    /// @param secret the `sanity-preview-secret` from the Studio
    /// @return a pass, or empty if Sanity does not know the secret
    /// @throws ContentUnavailable if Sanity cannot be asked
    public Optional<PreviewPass> start(String secret) {
        if (secret.isBlank() || !source.previewSecretValid(secret)) {
            return Optional.empty();
        }
        Instant expiresAt = Instant.ofEpochSecond(clock.instant().plus(LIFETIME).getEpochSecond());
        String expires = Long.toString(expiresAt.getEpochSecond());
        return Optional.of(new PreviewPass(
                expires + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(expires)), expiresAt));
    }

    /// @param pass the cookie's value, or null when there is none
    /// @return drafts for a valid pass that has not expired, published otherwise
    public Perspective perspective(@Nullable String pass) {
        if (pass == null) {
            return Perspective.PUBLISHED;
        }
        int dot = pass.indexOf('.');
        if (dot <= 0) {
            return Perspective.PUBLISHED;
        }
        String expires = pass.substring(0, dot);
        byte[] given;
        long until;
        try {
            given = Base64.getUrlDecoder().decode(pass.substring(dot + 1));
            until = Long.parseLong(expires);
        } catch (IllegalArgumentException e) {
            return Perspective.PUBLISHED;
        }
        boolean valid = MessageDigest.isEqual(sign(expires), given) && clock.instant().getEpochSecond() < until;
        return valid ? Perspective.DRAFTS : Perspective.PUBLISHED;
    }

    private byte[] sign(String expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(expires.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
