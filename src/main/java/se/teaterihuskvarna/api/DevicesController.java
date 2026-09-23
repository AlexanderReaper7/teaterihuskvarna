package se.teaterihuskvarna.api;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.login.DeviceDetails;
import se.teaterihuskvarna.login.DeviceService;
import se.teaterihuskvarna.login.LoginStatus;
import se.teaterihuskvarna.login.SignedIn;

/// The devices the logged-in member or administrator is logged in on, and when
/// the asking login ends: the same list, logouts and extension the `/medlem`
/// and `/admin` pages offer. The pages' countdown script calls the two
/// `session` endpoints itself. One path each for members and administrators,
/// which the two filter chains guard by kind.
///
/// Every endpoint here counts as a use of the login, so asking for the status
/// moves the device's "last used" time.
@RestController
public class DevicesController {

    private final DeviceService devices;

    DevicesController(DeviceService devices) {
        this.devices = devices;
    }

    /// @param signedIn the logged-in member or administrator
    /// @param session  the asking login, which the list marks as current
    /// @return the devices whose login has not ended, the asking one first
    @GetMapping({"/api/member/devices", "/api/admin/devices"})
    public List<DeviceDetails> list(@AuthenticationPrincipal SignedIn signedIn, HttpSession session) {
        return devices.list(signedIn, session);
    }

    /// @param signedIn the logged-in member or administrator, whose device it must be
    /// @param id       the device to log out; the asking one logs this client out
    /// @param session  the asking login
    @DeleteMapping({"/api/member/devices/{id}", "/api/admin/devices/{id}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void end(@AuthenticationPrincipal SignedIn signedIn, @PathVariable String id, HttpSession session) {
        devices.end(signedIn, id, session);
    }

    /// @param signedIn the logged-in member or administrator
    /// @param session  the asking login, which stays
    @DeleteMapping({"/api/member/devices/others", "/api/admin/devices/others"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endOthers(@AuthenticationPrincipal SignedIn signedIn, HttpSession session) {
        devices.endOthers(signedIn, session);
    }

    /// An ended login gets 401 here, as on every other endpoint, which is how
    /// the page's script learns it.
    ///
    /// @param session the asking login
    /// @return when it ends
    @GetMapping({"/api/member/session", "/api/admin/session"})
    public LoginStatus status(HttpSession session) {
        return devices.status(session);
    }

    /// @param signedIn the logged-in member or administrator
    /// @param session  the asking login
    /// @return when it now ends, the full lifetime from now
    @PostMapping({"/api/member/session/extend", "/api/admin/session/extend"})
    public LoginStatus extend(@AuthenticationPrincipal SignedIn signedIn, HttpSession session) {
        return devices.extend(signedIn, session);
    }
}
