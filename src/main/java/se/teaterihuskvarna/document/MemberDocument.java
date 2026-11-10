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
import se.teaterihuskvarna.login.AdministratorId;

/// A PDF for members, with the file itself in `content`.
///
/// Loading this entity loads the file. The lists therefore read a projection
/// ([MemberDocumentRepository#findSummaries]) and never the entity; only a
/// download loads it.
///
/// Column lengths mirror `V8__member_documents.sql`, which `EntityColumnsIT` checks:
/// `docs/decisions/0012-jpa-over-a-schema-flyway-owns.md`.
@Entity
@Table(name = "member_document")
public class MemberDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

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
    private Instant uploadedAt;

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
    /// @param uploadedAt  when it is uploaded
    MemberDocument(String title, DocumentKind kind, LocalDate publishedOn, String filename, byte[] content,
            AdministratorId uploadedBy, Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
        this.title = title;
        this.kind = kind;
        this.publishedOn = publishedOn;
        this.filename = filename;
        this.content = content.clone();
        this.sizeBytes = content.length;
        this.uploadedBy = uploadedBy.value();
    }

    /// @return the ID assigned by Hibernate
    /// @throws IllegalStateException if this entity has not been persisted
    public Long getId() {
        if (id == null) {
            throw new IllegalStateException("Entity has not been persisted");
        }
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

    public @Nullable AdministratorId getUploadedBy() {
        return uploadedBy == null ? null : new AdministratorId(uploadedBy);
    }

    public LocalDate getPublishedOn() {
        return publishedOn;
    }
}
