package se.teaterihuskvarna.content;

import java.time.Instant;

/// What [Previews#start] hands back: a signed pass that shows drafts until it
/// expires. The site keeps it in a cookie; a REST client sends it in the
/// `Preview-Pass` header.
///
/// @param value     the pass
/// @param expiresAt when it stops working
public record PreviewPass(String value, Instant expiresAt) {
}
