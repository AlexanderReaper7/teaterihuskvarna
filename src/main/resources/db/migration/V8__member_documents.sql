-- Annual meeting documents and member letters, R015: PDFs members read and
-- administrators upload. The file is stored here, in BYTEA, so the database
-- backup covers it and the application container keeps no files. A document is
-- at most 10 MB, which MemberDocumentService enforces.
CREATE TABLE member_document (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    kind         VARCHAR(20)  NOT NULL CHECK (kind IN ('ANNUAL_MEETING', 'MEMBER_LETTER')),
    -- The uploaded file's name, stripped of any path and control characters.
    filename     VARCHAR(255) NOT NULL,
    content      BYTEA        NOT NULL,
    size_bytes   INTEGER      NOT NULL CHECK (size_bytes >= 0),
    uploaded_at  TIMESTAMPTZ  NOT NULL,
    -- Null once the uploading administrator's row is gone. An administrator
    -- is normally removed by marking the row, so this is rare.
    uploaded_by  BIGINT       REFERENCES administrator (id) ON DELETE SET NULL,
    -- The date the document carries, which the lists sort and show by.
    published_on DATE         NOT NULL
);

CREATE INDEX member_document_kind_published_on ON member_document (kind, published_on DESC);
