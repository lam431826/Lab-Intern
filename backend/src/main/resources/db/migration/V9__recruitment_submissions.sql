-- Lịch sử các lần DX Lab gửi nhu cầu tuyển dụng/JD cho nhà trường.
-- Bản SENT là snapshot bất biến; thay đổi phải tạo bản mới qua replaces_submission_id.

CREATE TABLE recruitment_submissions (
    id                       BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_recruitment_submissions PRIMARY KEY,
    term_code                NVARCHAR(20)    NOT NULL,
    title                    NVARCHAR(200)   NOT NULL,
    submission_status        NVARCHAR(20)    NOT NULL CONSTRAINT DF_recruitment_submissions_status DEFAULT 'DRAFT',
    sent_at                  DATETIME2(3)    NULL,
    recipient                NVARCHAR(200)   NULL,
    external_reference       NVARCHAR(300)   NULL,
    note                     NVARCHAR(2000)  NULL,
    submitted_by             BIGINT          NULL CONSTRAINT FK_recruitment_submissions_submitted_by REFERENCES app_users (id),
    replaces_submission_id   BIGINT          NULL CONSTRAINT FK_recruitment_submissions_replaces REFERENCES recruitment_submissions (id),
    row_version              INT             NOT NULL CONSTRAINT DF_recruitment_submissions_row_version DEFAULT 0,
    created_at               DATETIME2(3)    NOT NULL CONSTRAINT DF_recruitment_submissions_created_at DEFAULT SYSUTCDATETIME(),
    created_by               BIGINT          NULL,
    updated_at               DATETIME2(3)    NOT NULL CONSTRAINT DF_recruitment_submissions_updated_at DEFAULT SYSUTCDATETIME(),
    updated_by               BIGINT          NULL,
    CONSTRAINT CK_recruitment_submissions_status CHECK (submission_status IN ('DRAFT', 'SENT')),
    CONSTRAINT CK_recruitment_submissions_sent CHECK (
        submission_status = 'DRAFT'
        OR (sent_at IS NOT NULL AND recipient IS NOT NULL AND LEN(recipient) > 0 AND submitted_by IS NOT NULL))
);

CREATE INDEX IX_recruitment_submissions_term_status
    ON recruitment_submissions (term_code, submission_status, sent_at);

CREATE TABLE recruitment_submission_items (
    id                       BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_recruitment_submission_items PRIMARY KEY,
    submission_id            BIGINT          NOT NULL CONSTRAINT FK_recruitment_submission_items_submission
        REFERENCES recruitment_submissions (id) ON DELETE CASCADE,
    track_id                 BIGINT          NOT NULL CONSTRAINT FK_recruitment_submission_items_track REFERENCES tracks (id),
    track_code_snapshot      NVARCHAR(30)    NOT NULL,
    track_name_snapshot      NVARCHAR(100)   NOT NULL,
    position_title           NVARCHAR(200)   NOT NULL,
    quota                    INT             NOT NULL,
    jd_content               NVARCHAR(MAX)   NULL,
    note                     NVARCHAR(2000)  NULL,
    sort_order               INT             NOT NULL CONSTRAINT DF_recruitment_submission_items_sort_order DEFAULT 0,
    file_name                NVARCHAR(255)   NULL,
    file_mime_type           NVARCHAR(100)   NULL,
    file_size                BIGINT          NULL,
    file_sha256              NVARCHAR(64)    NULL,
    row_version              INT             NOT NULL CONSTRAINT DF_recruitment_submission_items_row_version DEFAULT 0,
    created_at               DATETIME2(3)    NOT NULL CONSTRAINT DF_recruitment_submission_items_created_at DEFAULT SYSUTCDATETIME(),
    created_by               BIGINT          NULL,
    updated_at               DATETIME2(3)    NOT NULL CONSTRAINT DF_recruitment_submission_items_updated_at DEFAULT SYSUTCDATETIME(),
    updated_by               BIGINT          NULL,
    CONSTRAINT CK_recruitment_submission_items_quota CHECK (quota BETWEEN 1 AND 1000),
    CONSTRAINT CK_recruitment_submission_items_file_metadata CHECK (
        (file_name IS NULL AND file_mime_type IS NULL AND file_size IS NULL AND file_sha256 IS NULL)
        OR (file_name IS NOT NULL AND file_mime_type IS NOT NULL AND file_size IS NOT NULL AND file_size > 0 AND file_sha256 IS NOT NULL))
);

CREATE INDEX IX_recruitment_submission_items_submission
    ON recruitment_submission_items (submission_id, sort_order, id);
CREATE INDEX IX_recruitment_submission_items_track
    ON recruitment_submission_items (track_id, submission_id);

CREATE TABLE recruitment_submission_files (
    id                       BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_recruitment_submission_files PRIMARY KEY,
    item_id                  BIGINT          NOT NULL CONSTRAINT FK_recruitment_submission_files_item
        REFERENCES recruitment_submission_items (id) ON DELETE CASCADE,
    file_data                VARBINARY(MAX)  NOT NULL,
    created_at               DATETIME2(3)    NOT NULL CONSTRAINT DF_recruitment_submission_files_created_at DEFAULT SYSUTCDATETIME(),
    created_by               BIGINT          NULL,
    updated_at               DATETIME2(3)    NOT NULL CONSTRAINT DF_recruitment_submission_files_updated_at DEFAULT SYSUTCDATETIME(),
    updated_by               BIGINT          NULL,
    CONSTRAINT UQ_recruitment_submission_files_item UNIQUE (item_id)
);
