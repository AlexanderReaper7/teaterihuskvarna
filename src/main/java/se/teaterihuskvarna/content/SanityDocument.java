package se.teaterihuskvarna.content;

/// Content shown from one Sanity document, which a preview page can point the
/// Studio's editor at through [EditAttributes].
public interface SanityDocument {

    /// @return the published document's id, without `drafts.`
    String id();

    /// @return the document's type in `studio/schemaTypes/`, such as `nyhet`
    String type();
}
