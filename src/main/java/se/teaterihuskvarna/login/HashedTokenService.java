package se.teaterihuskvarna.login;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.ott.DefaultOneTimeToken;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.util.StringUtils;

/// Spring Security's token store for one kind of login, replaced because both
/// of Spring's own get something wrong here. `InMemoryOneTimeTokenService`
/// loses every link on a restart, and `JdbcOneTimeTokenService` stores the token
/// itself, so a reader of the table could log in as anyone.
///
/// One instance per [LoginKind], built by [SecurityConfiguration] and not a bean.
/// A token stored for one kind never redeems on the other kind's page, because
/// the `DELETE` matches the kind as well as the hash.
final class HashedTokenService implements OneTimeTokenService {

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
    /// @param request the address from the form, and the link lifetime
    /// @return a throwaway token for the same address
    @Override
    public OneTimeToken generate(GenerateOneTimeTokenRequest request) {
        links.send(kind, request.getUsername());
        return new DefaultOneTimeToken(
                Tokens.newToken(), request.getUsername(), Instant.now().plus(request.getExpiresIn()));
    }

    /// Deletes the token and returns what it was for, in one statement, so two
    /// requests racing with the same link cannot both log in. An expired token
    /// is deleted too, and redeems nothing.
    ///
    /// @param authenticationToken the token from the POSTed form
    /// @return the address the link went to, or null if the token is unknown, used, expired or another kind's
    @Override
    public @Nullable OneTimeToken consume(OneTimeTokenAuthenticationToken authenticationToken) {
        String token = authenticationToken.getTokenValue();
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return jdbc.sql("DELETE FROM one_time_token WHERE token_hash = ? AND kind = ? RETURNING email, expires_at")
                .param(Tokens.hash(token))
                .param(kind.code())
                .query((row, number) -> new DefaultOneTimeToken(
                        token,
                        row.getString("email"),
                        row.getObject("expires_at", OffsetDateTime.class).toInstant()))
                .optional()
                .filter(found -> found.getExpiresAt().isAfter(Instant.now()))
                .orElse(null);
    }
}
