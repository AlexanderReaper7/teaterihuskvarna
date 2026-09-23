package se.teaterihuskvarna.login;

/// The paths one kind of login uses for passkeys. Spring Security's passkey
/// filters answer on all four, and each kind has its own, so a member's
/// ceremony and an administrator's never share a path or a session attribute.
/// The pages that run the ceremonies read the paths from here.
///
/// The login paths sit under the login page, which anyone may reach. The
/// registration paths sit under the logged-in pages, so the access rules
/// already demand a login of the right kind before a passkey can be added.
///
/// @param loginOptions    POST returns the challenge a login signs
/// @param login           POST with the signed challenge logs in
/// @param registerOptions POST returns the challenge a new passkey signs
/// @param register        POST with the new passkey stores it
public record PasskeyUrls(String loginOptions, String login, String registerOptions, String register) {

    /// @param kind which login
    /// @return the passkey paths for that login
    public static PasskeyUrls of(LoginKind kind) {
        return switch (kind) {
            case MEMBER -> new PasskeyUrls(
                    "/logga-in/passkey/alternativ",
                    "/logga-in/passkey",
                    "/medlem/passkeys/alternativ",
                    "/medlem/passkeys");
            case ADMINISTRATOR -> new PasskeyUrls(
                    "/admin/logga-in/passkey/alternativ",
                    "/admin/logga-in/passkey",
                    "/admin/passkeys/alternativ",
                    "/admin/passkeys");
        };
    }

    /// Where a page's form removes a passkey. Spring answers none of these;
    /// the page controllers do, through [PasskeyService].
    ///
    /// @param id the passkey's id, as [PasskeyDetails#id] gives it
    /// @return the POST path that removes it
    public String remove(String id) {
        return register + "/" + id + "/ta-bort";
    }

    /// Where the offer's "Fråga inte igen" posts. The page controllers answer it.
    ///
    /// @return the POST path that stops the offer on this browser
    public String decline() {
        return register + "/fraga-inte-igen";
    }
}
