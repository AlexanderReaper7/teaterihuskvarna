package se.teaterihuskvarna.login;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/// The tokens in login links and confirmation links, and the hashes stored in
/// their place.
///
/// A plain SHA-256 without a salt is enough here, unlike for passwords. A salt
/// and a slow hash defend a guessable secret; a token is 256 random bits, so
/// there is nothing to guess, and the hash only has to stop a reader of the
/// database or of a backup from using what they read.
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTES = 32;

    private Tokens() {
    }

    /// @return a new token, URL safe, for a link
    public static String newToken() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /// @param token a token from a link
    /// @return its SHA-256 as 64 hex characters, the form the database stores
    public static String hash(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java runtime ships SHA-256", e);
        }
    }
}
