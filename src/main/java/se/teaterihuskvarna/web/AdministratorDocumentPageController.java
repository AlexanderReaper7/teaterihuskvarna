package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.View;
import se.teaterihuskvarna.document.DocumentFile;
import se.teaterihuskvarna.document.DocumentForm;
import se.teaterihuskvarna.document.DocumentSummary;
import se.teaterihuskvarna.document.FileTooLarge;
import se.teaterihuskvarna.document.MemberDocumentService;
import se.teaterihuskvarna.document.NoSuchDocument;
import se.teaterihuskvarna.document.NotAPdf;
import se.teaterihuskvarna.login.SignedIn;

/// Member documents for administrators (R015): the list, an upload form, a
/// confirmation page before a delete, and the files. Spring Security lets only
/// an administrator account reach it.
///
/// An upload over the limit in `application.yaml` never reaches this class:
/// Tomcat will not read its body, so Spring Security finds no CSRF token, and
/// `RefusedRequests` sends the browser back here with `forstor` in the query.
@Controller
public class AdministratorDocumentPageController {

    private static final String LIST = "/admin/handlingar";

    /// What the keys of [DocumentForm]'s messages start with.
    private static final String KEYS = "document";

    /// The parameter `se.teaterihuskvarna.login.RefusedRequests` adds after an
    /// upload over the limit.
    private static final String TOO_LARGE = "forstor";

    private final MemberDocumentService documents;
    private final Copy copy;
    private final Templates templates;

    AdministratorDocumentPageController(MemberDocumentService documents, Copy copy, Templates templates) {
        this.documents = documents;
        this.copy = copy;
        this.templates = templates;
    }

    /// @param tooLarge present when the upload before was over the limit
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the documents page: every document and an empty upload form
    @GetMapping(LIST)
    public View list(@RequestParam(name = TOO_LARGE, required = false) @Nullable String tooLarge, CsrfField csrf,
            Feedback feedback) {
        return page(DocumentForm.empty(), FieldErrors.none(),
                tooLarge == null ? null : copy.text("adminDocuments.error.tooLarge"), csrf, feedback);
    }

    /// @param signedIn the logged-in administrator, recorded as the uploader
    /// @param form     the title, kind and date that were typed
    /// @param binding  the values that could not be read, which are null in the form
    /// @param file     the PDF
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return a redirect to the page, or the page again with what was wrong
    /// @throws IOException if the upload cannot be read
    @PostMapping(LIST)
    public View upload(@AuthenticationPrincipal SignedIn signedIn, @ModelAttribute("form") DocumentForm form,
            BindingResult binding, @RequestParam(name = "file", required = false) @Nullable MultipartFile file,
            CsrfField csrf, Feedback feedback) throws IOException {
        if (binding.hasErrors()) {
            return page(form, FieldErrors.of(binding, copy, KEYS), null, csrf, feedback);
        }
        DocumentSummary uploaded;
        try {
            uploaded = documents.upload(form, file == null ? null : file.getOriginalFilename(),
                    file == null ? null : file.getBytes(), signedIn.administratorId());
        } catch (ConstraintViolationException e) {
            return page(form, FieldErrors.of(e), null, csrf, feedback);
        } catch (NotAPdf e) {
            return page(form, FieldErrors.none(), copy.text("adminDocuments.error.notPdf"), csrf, feedback);
        } catch (FileTooLarge e) {
            return page(form, FieldErrors.none(), copy.text("adminDocuments.error.tooLarge"), csrf, feedback);
        }
        return Redirect.to(LIST).notice(copy.text("adminDocuments.uploaded", uploaded.title()));
    }

    /// The confirmation step before a delete. A document that is gone, as when
    /// another administrator deleted it while the list stood open, sends the
    /// list back with that said, as the delete itself does.
    ///
    /// @param id       the document, which the page names
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the page that asks before deleting, or a redirect to the list
    @GetMapping(LIST + "/{id}/ta-bort")
    public View confirmDelete(@PathVariable long id, CsrfField csrf, Feedback feedback) {
        DocumentSummary document;
        try {
            document = documents.summary(id);
        } catch (NoSuchDocument e) {
            return Redirect.to(LIST).error(copy.text("adminDocuments.error.noSuch"));
        }
        return HtmlPage.of(templates.adminDocumentDelete(copy, csrf, document, feedback));
    }

    /// @param id the document
    /// @return a redirect to the page, with the outcome
    @PostMapping(LIST + "/{id}/ta-bort")
    public View delete(@PathVariable long id) {
        try {
            documents.delete(id);
        } catch (NoSuchDocument e) {
            return Redirect.to(LIST).error(copy.text("adminDocuments.error.noSuch"));
        }
        return Redirect.to(LIST).notice(copy.text("adminDocuments.deleted"));
    }

    /// @param id the document
    /// @return the PDF as an attachment, with the headers [DocumentFile#headers] lists
    @GetMapping(LIST + "/{id}/fil")
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

    private View page(DocumentForm form, FieldErrors errors, @Nullable String fileError, CsrfField csrf,
            Feedback feedback) {
        return HtmlPage.of(templates.adminDocuments(copy, csrf, documents.list(), form, errors, fileError,
                feedback));
    }
}
