/// Login by one-time link or passkey, for members' accounts and for administrators.
///
/// Spring Security runs the flow: its one-time-token filters, sessions, CSRF and
/// the access rules. This package supplies what Spring's defaults get wrong for
/// this system: a token store that keeps hashes rather than tokens, a lookup
/// that stays silent about unknown addresses, a rate limit, and mail sent on
/// another thread once the transaction commits. See
/// `docs/decisions/0015-login-links-on-spring-security.md`.
///
/// Passkeys are the second way in, on Spring's WebAuthn filters wired once per
/// kind of login: `docs/decisions/0016-passkeys-beside-links.md`.
@NullMarked
package se.teaterihuskvarna.login;

import org.jspecify.annotations.NullMarked;
