package se.teaterihuskvarna.export;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/// A CSV file to download (R020): an offer's registrations, a shift's volunteers,
/// the member register.
///
/// @param filename an ASCII file name ending in `.csv`
/// @param text     the file, starting with a byte order mark, as [Csv] writes it
public record CsvFile(String filename, String text) {

    /// The headers both adapters send the file with, so they cannot drift apart.
    ///
    /// @return `Content-Type: text/csv; charset=UTF-8` and `Content-Disposition: attachment` with the name
    public HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        return headers;
    }
}
