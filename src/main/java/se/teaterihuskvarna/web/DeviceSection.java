package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import se.teaterihuskvarna.login.DeviceService;
import se.teaterihuskvarna.login.NoSuchDevice;
import se.teaterihuskvarna.login.SignedIn;

/// The logged-in devices section of `/medlem` and `/admin`, and the countdown
/// before the login ends, which differ between the two pages only in whose
/// devices they list and where the forms post.
@Component
class DeviceSection {

    private final DeviceService devices;
    private final Copy copy;

    DeviceSection(DeviceService devices, Copy copy) {
        this.devices = devices;
        this.copy = copy;
    }

    /// @param model    receives `devices`, `loginStatus` and `deviceUrls`
    /// @param signedIn whose devices to list
    /// @param session  the asking request's session, which the list marks
    void addTo(Model model, SignedIn signedIn, HttpSession session) {
        model.addAttribute("devices", devices.list(signedIn, session));
        model.addAttribute("loginStatus", devices.status(session));
        model.addAttribute("deviceUrls", DeviceUrls.of(signedIn.kind()));
    }

    /// @param signedIn   whose device to log out
    /// @param id         the device's id
    /// @param session    the asking request's session
    /// @param redirected receives the outcome, shown after the redirect
    void end(SignedIn signedIn, String id, HttpSession session, RedirectAttributes redirected) {
        try {
            devices.end(signedIn, id, session);
        } catch (NoSuchDevice e) {
            redirected.addFlashAttribute("error", copy.text("device.error.noSuch"));
            return;
        }
        redirected.addFlashAttribute("notice", copy.text("device.ended"));
    }

    /// @param signedIn   whose other devices to log out
    /// @param session    the asking request's session, which stays logged in
    /// @param redirected receives the outcome, shown after the redirect
    void endOthers(SignedIn signedIn, HttpSession session, RedirectAttributes redirected) {
        devices.endOthers(signedIn, session);
        redirected.addFlashAttribute("notice", copy.text("device.endedOthers"));
    }
}
