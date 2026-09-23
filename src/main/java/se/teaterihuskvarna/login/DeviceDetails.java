package se.teaterihuskvarna.login;

import java.time.Instant;

/// One device a person is logged in on, as the `/medlem` and `/admin` pages and
/// their endpoints list it. A device is one browser's login: the same computer
/// with two browsers is two devices.
///
/// @param id       a hash of the session id, which ending the login takes; never the session id itself,
///                 which would log in whoever read it
/// @param name     the browser and system, such as `Firefox på Windows`
/// @param loggedIn when the login started
/// @param lastUsed when the login last made a request
/// @param current  whether this is the device asking
public record DeviceDetails(String id, String name, Instant loggedIn, Instant lastUsed, boolean current) {
}
