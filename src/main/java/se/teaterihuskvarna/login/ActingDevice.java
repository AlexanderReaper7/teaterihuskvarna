package se.teaterihuskvarna.login;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/// The name of the device the current request is logged in form, as stored by
/// the login([LoginSession]). Lets other packages record which device did
/// somthing without every call passing the session along.
public class ActingDevice {

/// What is returned when no request is running, such as a ackgrund
/// thread, or when the session has no device.

    public static final String UNKNOWN = "unknown";

    private ActingDevice() {
    }

/// @return the device name of current request's session, or [# UNKNOWN]
    public static String name() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return UNKNOWN;
        }
        HttpSession session = attributes.getRequest().getSession(false);
        if (session != null && session.getAttribute(LoginSession.DEVICE) instanceof String device) {
            return device;
        }
        return UNKNOWN;
    }
}
