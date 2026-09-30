package se.teaterihuskvarna.content;

import java.util.List;
import tools.jackson.databind.JsonNode;

/// Fetches every published document of one type. The site's content is a few
/// hundred documents at most, so [ContentService] filters and sorts in Java,
/// and a fixture needs no query language.
interface ContentSource {

    /// @param type        a document type from `studio/schemaTypes`, such as `evenemang`
    /// @param perspective whether drafts replace the published documents they belong to
    /// @return the documents, with references already resolved as [SanityContentSource] projects them
    /// @throws ContentUnavailable if the source cannot be reached or answers with an error
    List<JsonNode> documents(String type, Perspective perspective);

    /// Checks a secret the Studio's Presentation tool wrote to the dataset just
    /// before it opened the site in its preview: R009.
    ///
    /// @param secret the `sanity-preview-secret` the Studio put in the address
    /// @return whether a secret document with that value exists and is less than an hour old
    /// @throws ContentUnavailable if the source cannot be reached
    boolean previewSecretValid(String secret);
}
