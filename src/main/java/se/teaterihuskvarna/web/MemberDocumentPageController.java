package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import se.teaterihuskvarna.document.DocumentFile;
import se.teaterihuskvarna.document.MemberDocumentService;
import se.teaterihuskvarna.document.NoSuchDocument;

/// Member documents for members (R015): annual meeting documents and member
/// letters, and their files. Spring Security lets only a member's account
/// reach it. The page has no form, so it is not in [FormModel].
@Controller
public class MemberDocumentPageController {

    private final MemberDocumentService documents;

    MemberDocumentPageController(MemberDocumentService documents) {
        this.documents = documents;
    }

    /// @param model receives every document, without the files
    /// @return the list, grouped by kind
    @GetMapping("/medlem/handlingar")
    public String list(Model model) {
        model.addAttribute("documents", documents.list());
        return "member/documents";
    }

    /// @param id the document
    /// @return the PDF as an attachment, with the headers [DocumentFile#headers] lists
    @GetMapping("/medlem/handlingar/{id}/fil")
    public ResponseEntity<Resource> file(@PathVariable long id) {
        DocumentFile file = documents.file(id);
        return ResponseEntity.ok().headers(file.headers()).body(file.content());
    }

    /// Sends the error page with 404, as for any path that does not exist.
    ///
    /// @param response the response to send the error on
    /// @throws IOException if the response cannot be written
    @ExceptionHandler(NoSuchDocument.class)
    public void notFound(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }
}
