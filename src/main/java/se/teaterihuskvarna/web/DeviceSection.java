package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.stereotype.Component;
import se.teaterihuskvarna.login.DeviceDetails;
import se.teaterihuskvarna.login.DeviceService;
import se.teaterihuskvarna.login.NoSuchDevice;
import se.teaterihuskvarna.login.SignedIn;

/// The logged-in devices section of `/medlem` and `/admin`, which differ
/// between the two pages only in whose devices they list and where the forms
/// post.
@Component
class DeviceSection {

    private final DeviceService devices;
    private final Copy copy;

    DeviceSection(DeviceService devices, Copy copy) {
        this.devices = devices;
        this.copy = copy;
    }

    /// @param signedIn whose devices to list
    /// @param session  the asking request's session, which the list marks
    /// @return their logged-in devices
    List<DeviceDetails> list(SignedIn signedIn, HttpSession session) {
        return devices.list(signedIn, session);
    }

    /// @param signedIn whose device to log out
    /// @param id       the device's id
    /// @param session  the asking request's session
    /// @param page     the redirect back to the page
    /// @return the redirect with the outcome
    Redirect end(SignedIn signedIn, String id, HttpSession session, Redirect page) {
        try {
            devices.end(signedIn, id, session);
        } catch (NoSuchDevice e) {
            return page.error(copy.text("device.error.noSuch"));
        }
        return page.notice(copy.text("device.ended"));
    }

    /// @param signedIn whose other devices to log out
    /// @param session  the asking request's session, which stays logged in
    /// @param page     the redirect back to the page
    /// @return the redirect with the outcome
    Redirect endOthers(SignedIn signedIn, HttpSession session, Redirect page) {
        devices.endOthers(signedIn, session);
        return page.notice(copy.text("device.endedOthers"));
    }
}
