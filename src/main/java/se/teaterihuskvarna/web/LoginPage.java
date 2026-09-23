package se.teaterihuskvarna.web;

import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.PasskeyUrls;

/// The two login pages, one for members' accounts and one for administrator
/// accounts. They share templates and differ only in where their forms post and
/// what the heading says. The POST handlers at these paths are Spring Security's,
/// configured in `se.teaterihuskvarna.login`.
public enum LoginPage {

    /// A member logs in to their account.
    MEMBER(LoginKind.MEMBER, "/logga-in", "login.member.heading"),

    /// An administrator logs in to an administrator account.
    ADMINISTRATOR(LoginKind.ADMINISTRATOR, "/admin/logga-in", "login.administrator.heading");

    private final LoginKind kind;
    private final String path;
    private final String headingKey;

    LoginPage(LoginKind kind, String path, String headingKey) {
        this.kind = kind;
        this.path = path;
        this.headingKey = headingKey;
    }

    /// @return where the address form posts, and the page to return to
    public String path() {
        return path;
    }

    /// @return where the button on the link page posts the token
    public String linkPath() {
        return path + "/lank";
    }

    /// @return where the passkey button and autofill on this page post
    public PasskeyUrls passkeys() {
        return PasskeyUrls.of(kind);
    }

    /// @return the key of the page heading in `messages_sv.properties`
    public String headingKey() {
        return headingKey;
    }
}
