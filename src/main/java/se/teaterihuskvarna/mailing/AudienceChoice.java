package se.teaterihuskvarna.mailing;

/// One audience an administrator can pick: R022.
///
/// @param value what the form sends, such as `PAID` or `OFFER:12`
/// @param name  the audience in Swedish
public record AudienceChoice(String value, String name) {
}
