package se.teaterihuskvarna.content;

/// Which version of each document a page shows.
public enum Perspective {
    /// What visitors see.
    PUBLISHED,
    /// What an editor is working on, drafts in place of the published documents
    /// they belong to. Only for someone the Studio sent to the preview: R009.
    DRAFTS
}
