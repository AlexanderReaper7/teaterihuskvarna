package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.View;
import se.teaterihuskvarna.document.DocumentFile;
import se.teaterihuskvarna.document.MemberDocumentService;
import se.teaterihuskvarna.document.NoSuchDocument;

/// Member documents for members (R015): annual meeting documents and member
/// letters, and their files. Spring Security lets only a member's account
/// reach it. The page has no form, so it takes no [CsrfField].
@Controller
public class MemberDocumentPageController {

    private final MemberDocumentService documents;
    private final Copy copy;
    private final Templates templates;

    MemberDocumentPageController(MemberDocumentService documents, Copy copy, Templates templates) {
        this.documents = documents;
        this.copy = copy;
        this.templates = templates;
    }

    /// @return the list of every document, grouped by kind, without the files
    @GetMapping("/medlem/handlingar")
    public View list() {
        return HtmlPage.of(templates.memberDocuments(copy, documents.list()));
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
