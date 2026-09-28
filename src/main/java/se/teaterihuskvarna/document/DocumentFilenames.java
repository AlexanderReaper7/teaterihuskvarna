package se.teaterihuskvarna.document;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/// The file name a document is stored under, and the `Content-Disposition`
/// header a download sends it in.
///
/// The name an upload claims is whatever the browser, or anyone posting by
/// hand, put in the multipart header. It can hold a path such as
/// `..\..\x.pdf`, control characters, or a quote that would end the header's
/// quoted string early.
final class DocumentFilenames {

    /// The column width of `member_document.filename`.
    static final int MAX_LENGTH = 255;

    private static final String EXTENSION = ".pdf";
    private static final String FALLBACK = "dokument";

    /// RFC 5987 `attr-char`, the characters `filename*` may carry unencoded.
    private static final String ATTR_CHARS = "!#$&+-.^_`|~";

    private DocumentFilenames() {
    }

    /// Keeps the last path segment, drops control characters and the
    /// characters Windows refuses in a name, and ends it in `.pdf`.
    ///
    /// @param claimed the name the upload gave, or null
    /// @return a name at most [#MAX_LENGTH] characters long, never blank
    static String sanitize(@Nullable String claimed) {
        String name = claimed == null ? "" : claimed;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        StringBuilder kept = new StringBuilder();
        name.codePoints()
                .filter(c -> !Character.isISOControl(c) && "<>:\"|?*".indexOf(c) < 0)
                .forEach(kept::appendCodePoint);
        name = kept.toString().strip();
        if (name.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            name = name.substring(0, name.length() - EXTENSION.length()).strip();
        }
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            name = FALLBACK;
        }
        int room = MAX_LENGTH - EXTENSION.length();
        if (name.length() > room) {
            int cut = Character.isHighSurrogate(name.charAt(room - 1)) ? room - 1 : room;
            name = name.substring(0, cut);
        }
        return name + EXTENSION;
    }

    /// `attachment` with two names: an ASCII one in `filename` for clients
    /// that read only that, and the real one in `filename*` (RFC 6266 and
    /// RFC 5987) for every current browser, which prefers it.
    ///
    /// @param filename a name from [#sanitize]
    /// @return the header value
    static String contentDisposition(String filename) {
        return "attachment; filename=\"" + ascii(filename) + "\"; filename*=UTF-8''" + encode(filename);
    }

    /// Letters lose their accents, so `Årsmöte.pdf` becomes `Arsmote.pdf`, and
    /// anything else outside letters, digits, `.`, `-` and `_` becomes `_`.
    ///
    /// @param filename a name from [#sanitize]
    /// @return a name that is safe inside a quoted string
    static String ascii(String filename) {
        String stripped = Normalizer.normalize(filename, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder ascii = new StringBuilder();
        for (char c : stripped.toCharArray()) {
            boolean safe = c < 128 && (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_');
            ascii.append(safe ? c : '_');
        }
        return ascii.toString();
    }

    private static String encode(String filename) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : filename.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if (c < 128 && (Character.isLetterOrDigit(c) || ATTR_CHARS.indexOf(c) >= 0)) {
                encoded.append((char) c);
            } else {
                encoded.append('%').append(String.format(Locale.ROOT, "%02X", c));
            }
        }
        return encoded.toString();
    }
}
