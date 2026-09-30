package se.teaterihuskvarna.document;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/// A member document's file, for a download.
///
/// @param filename the name it was stored under
/// @param content  the PDF
public record DocumentFile(String filename, ByteArrayResource content) {

    /// The headers every download sends, from `/medlem`, `/admin` and the API
    /// alike, so the adapters cannot drift apart:
    ///
    /// - `Content-Type: application/pdf`, whatever type the upload claimed;
    /// - `Content-Disposition: attachment` with an ASCII `filename` and the real
    ///   name in `filename*`, so a browser saves the file rather than opening it
    ///   inside this site's origin;
    /// - `X-Content-Type-Options: nosniff`, so a browser does not guess another type;
    /// - `Cache-Control: private, no-store`, because the documents are for
    ///   members only and no cache may keep a copy.
    ///
    /// @return the headers
    public HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, DocumentFilenames.contentDisposition(filename));
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl("private, no-store");
        return headers;
    }
}
