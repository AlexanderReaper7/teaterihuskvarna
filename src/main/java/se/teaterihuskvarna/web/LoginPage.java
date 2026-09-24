package se.teaterihuskvarna.web;

import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.LoginUrls;
import se.teaterihuskvarna.login.PasskeyUrls;

/// The two login pages, one for members' accounts and one for administrator
/// accounts. They share templates and differ in where their forms post and what
/// the heading says, and the administrator page says whom it is for and links
/// to the member page. The POST handlers at these paths are Spring Security's,
/// configured in `se.teaterihuskvarna.login`, and the paths are theirs ([LoginUrls]).
public enum LoginPage {

    /// A member logs in to their account.
    MEMBER(LoginKind.MEMBER, "login.member.heading"),

    /// An administrator logs in to an administrator account.
    ADMINISTRATOR(LoginKind.ADMINISTRATOR, "login.administrator.heading");

    private final LoginKind kind;
    private final LoginUrls urls;
    private final String headingKey;

    LoginPage(LoginKind kind, String headingKey) {
        this.kind = kind;
        this.urls = LoginUrls.of(kind);
        this.headingKey = headingKey;
    }

    /// @return which kind of login this page is for
    public LoginKind kind() {
        return kind;
    }

    /// @return where the address form posts, and the page to return to
    public String path() {
        return urls.page();
    }

    /// @return where the button on the link page posts the token, and the code form posts the code
    public String linkPath() {
        return urls.link();
    }

    /// @return where an expired, used or unknown link sends the person, the address form with an error
    public String failurePath() {
        return urls.failure();
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
