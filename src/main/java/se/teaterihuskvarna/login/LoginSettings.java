package se.teaterihuskvarna.login;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// How long things last, and how many link requests are allowed. The defaults
/// are the ones `docs/projektplan.md` states; each can be overridden with a
/// property such as `teaterihuskvarna.login.link-lifetime`.
///
/// @param linkLifetime         how long a login link works
/// @param memberSession        how long a member stays logged in, counted from login
/// @param administratorSession how long an administrator stays logged in, counted from login
/// @param applicationLifetime  how long an unconfirmed membership application is kept
/// @param requestsPerAddress   link requests allowed per address within the window
/// @param requestsPerClient    link requests allowed per client IP address within the window
/// @param requestWindow        the period the two limits count over
@ConfigurationProperties("teaterihuskvarna.login")
public record LoginSettings(
        @DefaultValue("1h") Duration linkLifetime,
        @DefaultValue("30d") Duration memberSession,
        @DefaultValue("8h") Duration administratorSession,
        @DefaultValue("24h") Duration applicationLifetime,
        @DefaultValue("5") int requestsPerAddress,
        @DefaultValue("20") int requestsPerClient,
        @DefaultValue("1h") Duration requestWindow) {

    /// @param kind which login
    /// @return how long a login of that kind lasts, counted from login or from the last extension
    public Duration session(LoginKind kind) {
        return switch (kind) {
            case MEMBER -> memberSession;
            case ADMINISTRATOR -> administratorSession;
        };
    }
}
