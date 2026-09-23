package se.teaterihuskvarna.login;

import java.time.Instant;

/// One passkey, as the `/medlem` and `/admin` pages and their endpoints list it.
///
/// @param id       the credential id, base64url, which removal takes
/// @param label    the browser and system it was added on, such as `Firefox på Windows`
/// @param created  when it was added
/// @param lastUsed when it last logged someone in; the time it was added until then
public record PasskeyDetails(String id, String label, Instant created, Instant lastUsed) {
}
