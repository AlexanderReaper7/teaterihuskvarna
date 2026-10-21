/// Member documents, R015: annual meeting documents and member letters as PDF
/// files, which administrators upload and members download.
///
/// Everything goes through [MemberDocumentService]. The file is stored in
/// PostgreSQL, and the lists read everything but the file.
@NullMarked
package se.teaterihuskvarna.document;

import org.jspecify.annotations.NullMarked;
