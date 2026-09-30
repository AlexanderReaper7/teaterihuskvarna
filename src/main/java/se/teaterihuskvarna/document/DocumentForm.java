package se.teaterihuskvarna.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;

/// What an administrator fills in beside the file when uploading a document.
/// The limits are the column widths in `member_document`, and the messages are
/// keys in `messages_sv.properties`.
///
/// @param title       what the lists call it, required
/// @param kind        which group it belongs to, required
/// @param publishedOn the date the document carries, such as the meeting's, required
public record DocumentForm(
        @NotBlank(message = "{document.title.required}")
        @Size(max = 200, message = "{document.title.size}")
        String title,

        @NotNull(message = "{document.kind.required}")
        @Nullable DocumentKind kind,

        @NotNull(message = "{document.publishedOn.required}")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Nullable LocalDate publishedOn) {

    /// @return an empty form, for the upload page
    public static DocumentForm empty() {
        return new DocumentForm("", null, null);
    }
}
