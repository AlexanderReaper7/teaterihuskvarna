package se.teaterihuskvarna.document;

/// What a member document is, which decides the group the member page lists
/// it under. Stored by name in `member_document.kind`.
public enum DocumentKind {

    /// Årsmöteshandling: a document for the annual meeting.
    ANNUAL_MEETING,

    /// Medlemsbrev: a letter to the members.
    MEMBER_LETTER
}
