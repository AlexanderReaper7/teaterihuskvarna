package se.teaterihuskvarna.login;

/// The paths one kind of login uses. The filter chains, the rate limit and the
/// link in the mail all read them from here, so the link a mail carries and the
/// path the filter listens on cannot drift apart. The pages themselves are JTE
/// controllers in `se.teaterihuskvarna.web`, which name the same paths again.
///
/// @param page      GET shows the form, POST asks for a link
/// @param sent      the page shown after asking, whether or not a mail went out
/// @param link      GET shows the button page, POST with the token or the code logs in
/// @param success   where a login lands
/// @param failure   where an expired, used or unknown link lands
/// @param logout    POST ends the session
/// @param loggedOut where logging out lands
public record LoginUrls(
        String page,
        String sent,
        String link,
        String success,
        String failure,
        String logout,
        String loggedOut) {

    /// @param kind which login
    /// @return the paths for that login
    public static LoginUrls of(LoginKind kind) {
        return switch (kind) {
            case MEMBER -> new LoginUrls(
                    "/logga-in",
                    "/logga-in/skickat",
                    "/logga-in/lank",
                    "/medlem",
                    "/logga-in?fel",
                    "/logga-ut",
                    "/");
            case ADMINISTRATOR -> new LoginUrls(
                    "/admin/logga-in",
                    "/admin/logga-in/skickat",
                    "/admin/logga-in/lank",
                    "/admin",
                    "/admin/logga-in?fel",
                    "/admin/logga-ut",
                    "/admin/logga-in");
        };
    }
}
