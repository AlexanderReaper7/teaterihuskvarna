package se.teaterihuskvarna.web;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.WebUtils;

/// Whether the browser that asked for a page is taking the administrators'
/// walkthrough: `tag/adminTour.jte` and `/js/tour.js`.
///
/// The walkthrough's steps name the buttons of every administrator page, so a
/// page that always held them would hold "Skicka ett test till mig" and "inte
/// har betalat" whatever it showed. `MailingIT` and `FeeIT` read a page's text
/// for what it must not say, and failed on those two when the steps were in
/// every page. The script therefore sets a cookie when a walkthrough starts
/// and removes it when it ends, and the steps are written only into a page
/// asked for with the cookie.
///
/// The administrators' layout asks this itself, as it asks [LoginCountdown],
/// so no page has to pass it on.
public final class Walkthrough {

    /// The cookie's name, which `/js/tour.js` writes as `KEY`. Its value says nothing.
    public static final String COOKIE = "rundtur";

    private Walkthrough() {
    }

    /// @return whether the request for the page being rendered carries the walkthrough's cookie
    public static boolean running() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && WebUtils.getCookie(attributes.getRequest(), COOKIE) != null;
    }
}
