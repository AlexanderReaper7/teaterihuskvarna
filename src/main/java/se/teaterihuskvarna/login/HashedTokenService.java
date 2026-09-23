package se.teaterihuskvarna.login;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.ott.DefaultOneTimeToken;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;

/// Spring Security's token store for one kind of login, replaced because both
/// of Spring's own get something wrong here. `InMemoryOneTimeTokenService`
/// loses every link on a restart, and `JdbcOneTimeTokenService` stores the token
/// itself, so a reader of the table could log in as anyone.
///
/// One instance per [LoginKind], built by [SecurityConfiguration] and not a bean.
/// A token stored for one kind never redeems on the other kind's page, because
/// the `DELETE` matches the kind as well as the hash.
final class HashedTokenService implements OneTimeTokenService {

    /// Wrong codes allowed per link. The link itself keeps working after them.
    static final int CODE_TRIES = 5;

    private final LoginKind kind;
    private final LoginLinks links;
    private final JdbcClient jdbc;

    /// @param kind  the login this store answers for
    /// @param links creates and mails the real token
    /// @param jdbc  reaches `one_time_token`
    HashedTokenService(LoginKind kind, LoginLinks links, JdbcClient jdbc) {
        this.kind = kind;
        this.links = links;
        this.jdbc = jdbc;
    }

    /// Mails a link if the address has a login of this kind, and returns a token
    /// that is never stored or sent, whether it did or not.
    ///
    /// Spring's `GenerateOneTimeTokenFilter` hands the returned token to the
    /// success handler, which only redirects. The token has to exist, because
    /// the filter expects one, but it must not differ between a known and an
    /// unknown address, and it must not be the real one, which lives only in
    /// the mail. A fresh random value meets all three.
    ///
    /// @param request the address from the form, the link lifetime and the browser, as a [BoundLinkRequest]
    /// @return a throwaway token for the same address
    /// @throws IllegalStateException if the request is not a [BoundLinkRequest], which means the
    ///         resolver in [SecurityConfiguration] was changed
    @Override
    public OneTimeToken generate(GenerateOneTimeTokenRequest request) {
        if (!(request instanceof BoundLinkRequest bound)) {
            throw new IllegalStateException("a link request without a browser: " + request.getClass());
        }
        links.send(kind, request.getUsername(), bound.browser());
        return new DefaultOneTimeToken(
                Tokens.newToken(), request.getUsername(), Instant.now().plus(request.getExpiresIn()));
    }

    /// Redeems a link or a code, but only in the browser that asked for it. A
    /// link or a code sent from any other browser redeems nothing and leaves the
    /// row alone, so it still works where it belongs.
    ///
    /// A link is deleted and returned in one statement, so two requests racing
    /// with the same link cannot both log in. An expired one is deleted too, and
    /// redeems nothing.
    ///
    /// A code first takes one of its five tries, in the same `UPDATE` that finds
    /// the rows, so requests racing with guesses cannot get more than five
    /// between them. The rows are every unexpired link this browser asked for on
    /// this page, since an older mail's code works as its link does. A match
    /// deletes that row the way a link does.
    ///
    /// @param authenticationToken the POSTed form, as a [BoundLogin]
    /// @return the address the link went to, or null if nothing matched
    @Override
    public @Nullable OneTimeToken consume(OneTimeTokenAuthenticationToken authenticationToken) {
        if (!(authenticationToken instanceof BoundLogin login)) {
            return null;
        }
        String cookie = login.browser();
        if (cookie == null) {
            return null;
        }
        String browser = Tokens.hash(cookie);
        String code = login.code();
        if (code != null) {
            return byCode(code, browser);
        }
        String token = login.getTokenValue();
        if (token == null) {
            return null;
        }
        return redeem(jdbc.sql("""
                        DELETE FROM one_time_token WHERE token_hash = ? AND kind = ? AND browser_hash = ?
                        RETURNING token_hash, email, expires_at""")
                .param(Tokens.hash(token))
                .param(kind.code())
                .param(browser));
    }

    private @Nullable OneTimeToken byCode(String code, String browser) {
        byte[] typed = Tokens.hash(code).getBytes(StandardCharsets.US_ASCII);
        List<Candidate> candidates = jdbc.sql("""
                        UPDATE one_time_token SET code_tries = code_tries + 1
                        WHERE browser_hash = ? AND kind = ? AND code_tries < ? AND expires_at > now()
                        RETURNING token_hash, code_hash""")
                .param(browser)
                .param(kind.code())
                .param(CODE_TRIES)
                .query((row, number) -> new Candidate(row.getString("token_hash"), row.getString("code_hash")))
                .list();
        for (Candidate candidate : candidates) {
            if (MessageDigest.isEqual(typed, candidate.codeHash().getBytes(StandardCharsets.US_ASCII))) {
                return redeem(jdbc.sql("""
                                DELETE FROM one_time_token WHERE token_hash = ?
                                RETURNING token_hash, email, expires_at""")
                        .param(candidate.tokenHash()));
            }
        }
        return null;
    }

    /// The token Spring gets back holds the hash, since the token itself is not
    /// known for a code, and Spring reads only the address and the expiry.
    private static @Nullable OneTimeToken redeem(JdbcClient.StatementSpec delete) {
        return delete.query((row, number) -> new DefaultOneTimeToken(
                        row.getString("token_hash"),
                        row.getString("email"),
                        row.getObject("expires_at", OffsetDateTime.class).toInstant()))
                .optional()
                .filter(found -> found.getExpiresAt().isAfter(Instant.now()))
                .orElse(null);
    }

    private record Candidate(String tokenHash, String codeHash) {
    }
}
