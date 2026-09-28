package se.teaterihuskvarna.document;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/// Annual meeting documents and member letters, R015.
///
/// An administrator uploads a PDF and deletes it; members list and download.
/// Who may call which method is the adapters' paths: members under `/medlem`
/// and `/api/member`, administrators under `/admin` and `/api/admin`, which
/// `SecurityConfiguration` guards.
@Service
@Validated
@Transactional(readOnly = true)
public class MemberDocumentService {

    /// 10 MB, the most a document may be, checked here to the byte for any
    /// caller. `spring.servlet.multipart` in `application.yaml` only stops
    /// requests clearly larger, at 11 MB, before they reach this class.
    public static final int MAX_BYTES = 10 * 1024 * 1024;

    /// What every PDF file starts with, ISO 32000-2 section 7.5.2.
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final MemberDocumentRepository documents;

    MemberDocumentService(MemberDocumentRepository documents) {
        this.documents = documents;
    }

    /// @return every document without its file, grouped by kind, newest first within a kind
    public List<DocumentSummary> list() {
        return documents.findSummaries();
    }

    /// Stores a PDF. The file must start with `%PDF-`; the type the upload
    /// claimed is not trusted, since the sender chooses it.
    ///
    /// @param form       the title, kind and date
    /// @param filename   the name the upload gave, which is sanitised
    /// @param content    the file, or null when none was sent
    /// @param uploadedBy the administrator uploading it
    /// @return the document as stored, without its file
    /// @throws NotAPdf if the file is missing, empty or not a PDF
    /// @throws FileTooLarge if the file is over [#MAX_BYTES]
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    @Transactional
    public DocumentSummary upload(@Valid DocumentForm form, @Nullable String filename, byte @Nullable [] content,
            long uploadedBy) {
        if (content == null || content.length < PDF_HEADER.length
                || !Arrays.equals(content, 0, PDF_HEADER.length, PDF_HEADER, 0, PDF_HEADER.length)) {
            throw new NotAPdf();
        }
        if (content.length > MAX_BYTES) {
            throw new FileTooLarge();
        }
        MemberDocument document = documents.save(new MemberDocument(form.title().strip(), form.kind(),
                form.publishedOn(), DocumentFilenames.sanitize(filename), content, uploadedBy));
        return new DocumentSummary(document.getId(), document.getTitle(), document.getKind(),
                document.getFilename(), document.getSizeBytes(), document.getPublishedOn(),
                document.getUploadedAt());
    }

    /// @param id the document
    /// @return the file and its name
    /// @throws NoSuchDocument if no document has the id
    public DocumentFile file(long id) {
        MemberDocument document = documents.findById(id).orElseThrow(NoSuchDocument::new);
        return new DocumentFile(document.getFilename(), new ByteArrayResource(document.getContent()));
    }

    /// @param id the document
    /// @throws NoSuchDocument if no document has the id
    @Transactional
    public void delete(long id) {
        if (documents.deleteDocument(id) == 0) {
            throw new NoSuchDocument();
        }
    }
}
