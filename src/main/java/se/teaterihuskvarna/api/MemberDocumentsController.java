package se.teaterihuskvarna.api;

import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.document.DocumentFile;
import se.teaterihuskvarna.document.DocumentSummary;
import se.teaterihuskvarna.document.MemberDocumentService;

/// Member documents for a logged-in member (R015), as `/medlem/handlingar`
/// shows them.
@RestController
public class MemberDocumentsController {

    private final MemberDocumentService documents;

    MemberDocumentsController(MemberDocumentService documents) {
        this.documents = documents;
    }

    /// @return every document without its file, grouped by kind, newest first within a kind
    @GetMapping("/api/member/documents")
    public List<DocumentSummary> list() {
        return documents.list();
    }

    /// @param id the document
    /// @return the PDF as an attachment, with the headers [DocumentFile#headers] lists
    @GetMapping("/api/member/documents/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable long id) {
        DocumentFile file = documents.file(id);
        return ResponseEntity.ok().headers(file.headers()).body(file.content());
    }
}
