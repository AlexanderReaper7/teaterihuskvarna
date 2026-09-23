package se.teaterihuskvarna.web;

import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.LoginUrls;

/// The paths the devices section and the login's countdown use on `/medlem`
/// and `/admin`. The forms post to the page controllers. The countdown's script
/// asks the REST API, since it wants JSON and the API already answers for the
/// same session and the same service methods (`se.teaterihuskvarna.api.DevicesController`).
///
/// @param page   the logged-in page, where the forms redirect back to
/// @param status GET says when the login ends
/// @param extend POST starts the login's full lifetime again
/// @param logout POST logs the asking device out, which is what its row's button does
/// @param login  the plain login page, which a page whose login ended links to; never a login link
public record DeviceUrls(String page, String status, String extend, String logout, String login) {

    /// @param kind which login
    /// @return the paths for that login
    public static DeviceUrls of(LoginKind kind) {
        LoginUrls urls = LoginUrls.of(kind);
        String api = switch (kind) {
            case MEMBER -> "/api/member";
            case ADMINISTRATOR -> "/api/admin";
        };
        return new DeviceUrls(urls.success(), api + "/session", api + "/session/extend", urls.logout(),
                urls.page());
    }

    /// @param id the device's id
    /// @return the POST path that logs that device out
    public String end(String id) {
        return page + "/enheter/" + id + "/logga-ut";
    }

    /// @return the POST path that logs out every device but the asking one
    public String endOthers() {
        return page + "/enheter/andra/logga-ut";
    }
}
