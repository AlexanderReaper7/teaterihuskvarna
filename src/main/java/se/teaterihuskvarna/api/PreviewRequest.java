package se.teaterihuskvarna.api;

import org.jspecify.annotations.Nullable;

/// The body of `POST /api/content/preview`.
///
/// @param secret the `sanity-preview-secret` the Studio wrote to the dataset
public record PreviewRequest(@Nullable String secret) {
}
