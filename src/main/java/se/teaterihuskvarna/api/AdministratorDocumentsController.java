package se.teaterihuskvarna.api;

import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import se.teaterihuskvarna.document.DocumentFile;
import se.teaterihuskvarna.document.DocumentForm;
import se.teaterihuskvarna.document.DocumentSummary;
import se.teaterihuskvarna.document.MemberDocumentService;
import se.teaterihuskvarna.login.SignedIn;

/// Member documents for administrators over HTTP (R015): what
/// `/admin/handlingar` offers.
@RestController
public class AdministratorDocumentsController {

    private final MemberDocumentService documents;

    AdministratorDocumentsController(MemberDocumentService documents) {
        this.documents = documents;
    }

    /// @return every document without its file
    @GetMapping("/api/admin/documents")
    public List<DocumentSummary> list() {
        return documents.list();
    }

    /// A `multipart/form-data` request with the parts `title`, `kind`
    /// (`ANNUAL_MEETING` or `MEMBER_LETTER`), `publishedOn` (`2026-03-14`) and
    /// `file`, a PDF of at most 10 MB.
    ///
    /// @param signedIn the logged-in administrator, recorded as the uploader
    /// @param form     the title, kind and date
    /// @param file     the PDF
    /// @return the document as stored, without its file
    /// @throws IOException if the upload cannot be read
    @PostMapping("/api/admin/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentSummary upload(@AuthenticationPrincipal SignedIn signedIn,
            @ModelAttribute DocumentForm form,
            @RequestParam(name = "file", required = false) @Nullable MultipartFile file) throws IOException {
        return documents.upload(form, file == null ? null : file.getOriginalFilename(),
                file == null ? null : file.getBytes(), signedIn.id());
    }

    /// @param id the document
    @DeleteMapping("/api/admin/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        documents.delete(id);
    }

    /// @param id the document
    /// @return the PDF as an attachment, with the headers [DocumentFile#headers] lists
    @GetMapping("/api/admin/documents/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable long id) {
        DocumentFile file = documents.file(id);
        return ResponseEntity.ok().headers(file.headers()).body(file.content());
    }
}
