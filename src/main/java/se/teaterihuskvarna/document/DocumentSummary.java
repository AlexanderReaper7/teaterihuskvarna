package se.teaterihuskvarna.document;

import java.time.Instant;
import java.time.LocalDate;

/// A member document without its file, for the lists.
///
/// @param id          the document's id
/// @param title       what the lists call it
/// @param kind        which group it belongs to
/// @param filename    the file name a download gets
/// @param sizeBytes   the file's size in bytes
/// @param publishedOn the date the document carries
/// @param uploadedAt  when an administrator uploaded it
public record DocumentSummary(long id, String title, DocumentKind kind, String filename, int sizeBytes,
        LocalDate publishedOn, Instant uploadedAt) {

    /// @return the size in kilobytes, rounded up, for a list to show
    public long kilobytes() {
        return (sizeBytes + 1023L) / 1024L;
    }
}
