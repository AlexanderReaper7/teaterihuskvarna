package se.teaterihuskvarna.document;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// Reads and writes member documents. Package private, so only
/// [MemberDocumentService] can reach the table:
/// `docs/decisions/0014-one-service-layer-two-adapters.md`.
interface MemberDocumentRepository extends JpaRepository<MemberDocument, Long> {

    /// Every column but `content`, so a list never reads the files.
    ///
    /// @return every document, by kind, newest first within a kind
    @Query("""
            select new se.teaterihuskvarna.document.DocumentSummary(
                d.id, d.title, d.kind, d.filename, d.sizeBytes, d.publishedOn, d.uploadedAt)
            from MemberDocument d
            order by d.kind, d.publishedOn desc, d.uploadedAt desc, d.id desc
            """)
    List<DocumentSummary> findSummaries();

    /// Deletes without loading the row, and so without reading the file.
    ///
    /// @param id the document
    /// @return how many rows were deleted, 0 or 1
    @Modifying
    @Query("delete from MemberDocument d where d.id = :id")
    int deleteDocument(@Param("id") long id);
}
