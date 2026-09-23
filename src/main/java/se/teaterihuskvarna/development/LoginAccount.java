package se.teaterihuskvarna.development;

import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.LoginUrls;

/// An address that can log in right now.
///
/// @param kind     an account or an administrator account
/// @param email    the address a login link goes to
/// @param fullName the member's or the administrator's name
public record LoginAccount(LoginKind kind, String email, String fullName) {

    /// @return the path a POST with the address asks for a link on
    public String loginPage() {
        return LoginUrls.of(kind).page();
    }
}
