package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import se.teaterihuskvarna.document.DocumentFile;
import se.teaterihuskvarna.document.DocumentForm;
import se.teaterihuskvarna.document.DocumentSummary;
import se.teaterihuskvarna.document.FileTooLarge;
import se.teaterihuskvarna.document.MemberDocumentService;
import se.teaterihuskvarna.document.NoSuchDocument;
import se.teaterihuskvarna.document.NotAPdf;
import se.teaterihuskvarna.login.SignedIn;

/// Member documents for administrators (R015): the list, an upload form, a
/// delete button per document, and the files. Spring Security lets only an
/// administrator account reach it.
///
/// An upload over the limit in `application.yaml` never reaches this class:
/// Tomcat will not read its body, so Spring Security finds no CSRF token, and
/// `RefusedRequests` sends the browser back here with `forstor` in the query.
@Controller
public class AdministratorDocumentPageController {

    private static final String LIST = "/admin/handlingar";

    /// The parameter `se.teaterihuskvarna.login.RefusedRequests` adds after an
    /// upload over the limit.
    private static final String TOO_LARGE = "forstor";

    private final MemberDocumentService documents;
    private final Copy copy;

    AdministratorDocumentPageController(MemberDocumentService documents, Copy copy) {
        this.documents = documents;
        this.copy = copy;
    }

    /// @param tooLarge present when the upload before was over the limit
    /// @param model    receives every document and an empty upload form
    /// @return the documents page
    @GetMapping(LIST)
    public String list(@RequestParam(name = TOO_LARGE, required = false) @Nullable String tooLarge,
            Model model) {
        return page(DocumentForm.empty(), FieldErrors.none(),
                tooLarge == null ? null : copy.text("adminDocuments.error.tooLarge"), model);
    }

    /// @param signedIn   the logged-in administrator, recorded as the uploader
    /// @param form       the title, kind and date that were typed
    /// @param file       the PDF
    /// @param model      receives the page again when the upload fails
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the page, or the page again with what was wrong
    /// @throws IOException if the upload cannot be read
    @PostMapping(LIST)
    public String upload(@AuthenticationPrincipal SignedIn signedIn, @ModelAttribute("form") DocumentForm form,
            @RequestParam(name = "file", required = false) @Nullable MultipartFile file, Model model,
            RedirectAttributes redirected) throws IOException {
        DocumentSummary uploaded;
        try {
            uploaded = documents.upload(form, file == null ? null : file.getOriginalFilename(),
                    file == null ? null : file.getBytes(), signedIn.id());
        } catch (ConstraintViolationException e) {
            return page(form, FieldErrors.of(e), null, model);
        } catch (NotAPdf e) {
            return page(form, FieldErrors.none(), copy.text("adminDocuments.error.notPdf"), model);
        } catch (FileTooLarge e) {
            return page(form, FieldErrors.none(), copy.text("adminDocuments.error.tooLarge"), model);
        }
        redirected.addFlashAttribute("notice", copy.text("adminDocuments.uploaded", uploaded.title()));
        return "redirect:" + LIST;
    }

    /// @param id         the document
    /// @param redirected receives the outcome, shown after the redirect
    /// @return a redirect to the page
    @PostMapping(LIST + "/{id}/ta-bort")
    public String delete(@PathVariable long id, RedirectAttributes redirected) {
        try {
            documents.delete(id);
        } catch (NoSuchDocument e) {
            redirected.addFlashAttribute("error", copy.text("adminDocuments.error.noSuch"));
            return "redirect:" + LIST;
        }
        redirected.addFlashAttribute("notice", copy.text("adminDocuments.deleted"));
        return "redirect:" + LIST;
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

    private String page(DocumentForm form, FieldErrors errors, @Nullable String fileError, Model model) {
        model.addAttribute("documents", documents.list());
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        if (fileError != null) {
            model.addAttribute("fileError", fileError);
        }
        return "admin/documents";
    }
}
