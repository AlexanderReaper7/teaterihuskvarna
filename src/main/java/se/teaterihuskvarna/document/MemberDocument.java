package se.teaterihuskvarna.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/// A PDF for members, with the file itself in `content`.
///
/// Loading this entity loads the file. The lists therefore read a projection
/// ([MemberDocumentRepository#findSummaries]) and never the entity; only a
/// download loads it.
///
/// Column lengths mirror `V8__member_documents.sql` by hand, because
/// `ddl-auto: validate` does not compare them:
/// `docs/decisions/0012-jpa-over-a-schema-flyway-owns.md`.
@Entity
@Table(name = "member_document")
public class MemberDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private DocumentKind kind;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "uploaded_by")
    private @Nullable Long uploadedBy;

    @Column(name = "published_on", nullable = false)
    private LocalDate publishedOn;

    protected MemberDocument() {
        // for JPA
    }

    /// @param title       what the lists call it
    /// @param kind        which group it belongs to
    /// @param publishedOn the date the document carries
    /// @param filename    the sanitised file name
    /// @param content     the PDF, copied
    /// @param uploadedBy  the administrator uploading it
    MemberDocument(String title, DocumentKind kind, LocalDate publishedOn, String filename, byte[] content,
            long uploadedBy) {
        this.title = title;
        this.kind = kind;
        this.publishedOn = publishedOn;
        this.filename = filename;
        this.content = content.clone();
        this.sizeBytes = content.length;
        this.uploadedBy = uploadedBy;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public DocumentKind getKind() {
        return kind;
    }

    public String getFilename() {
        return filename;
    }

    /// @return a copy of the PDF
    public byte[] getContent() {
        return content.clone();
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public @Nullable Long getUploadedBy() {
        return uploadedBy;
    }

    public LocalDate getPublishedOn() {
        return publishedOn;
    }
}
