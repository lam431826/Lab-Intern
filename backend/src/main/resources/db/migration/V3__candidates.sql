-- Hồ sơ ứng viên và kết quả sàng lọc CV (Phân hệ 1).

CREATE TABLE candidates (
    id                   BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_candidates PRIMARY KEY,
    student_code         NVARCHAR(20)    NOT NULL,
    full_name            NVARCHAR(100)   NOT NULL,
    email                NVARCHAR(254)   NULL,
    phone                NVARCHAR(20)    NULL,
    major                NVARCHAR(100)   NULL,
    track_id             BIGINT          NOT NULL CONSTRAINT FK_candidates_track REFERENCES tracks (id),
    cv_link              NVARCHAR(500)   NULL,
    portfolio_link       NVARCHAR(500)   NULL,
    screening_status     NVARCHAR(20)     NOT NULL CONSTRAINT DF_candidates_screening_status DEFAULT 'PENDING',
    screening_reason     NVARCHAR(1000)  NULL,
    -- 5 mục chấm sơ bộ CV, thang 1–10
    cv_personal_info     INT             NULL,
    cv_experience        INT             NULL,
    cv_skills            INT             NULL,
    cv_projects          INT             NULL,
    cv_certificates      INT             NULL,
    screened_by          BIGINT          NULL CONSTRAINT FK_candidates_screened_by REFERENCES app_users (id),
    screened_at          DATETIME2(3)    NULL,
    -- Mốc tính SLA duyệt CV: thời điểm hồ sơ được giao cho Lead của ngạch
    assigned_at          DATETIME2(3)    NOT NULL CONSTRAINT DF_candidates_assigned_at DEFAULT SYSUTCDATETIME(),
    row_version          INT             NOT NULL CONSTRAINT DF_candidates_row_version DEFAULT 0,
    created_at           DATETIME2(3)    NOT NULL CONSTRAINT DF_candidates_created_at DEFAULT SYSUTCDATETIME(),
    created_by           BIGINT          NULL,
    updated_at           DATETIME2(3)    NOT NULL CONSTRAINT DF_candidates_updated_at DEFAULT SYSUTCDATETIME(),
    updated_by           BIGINT          NULL,
    CONSTRAINT UQ_candidates_student_code UNIQUE (student_code),
    CONSTRAINT CK_candidates_screening_status CHECK (screening_status IN ('PENDING', 'PASSED', 'REJECTED')),
    -- Loại hồ sơ thì bắt buộc ghi lý do
    CONSTRAINT CK_candidates_reject_reason CHECK (
        screening_status <> 'REJECTED' OR (screening_reason IS NOT NULL AND LEN(screening_reason) > 0)),
    CONSTRAINT CK_candidates_cv_scores CHECK (
            (cv_personal_info IS NULL OR cv_personal_info BETWEEN 1 AND 10)
        AND (cv_experience    IS NULL OR cv_experience    BETWEEN 1 AND 10)
        AND (cv_skills        IS NULL OR cv_skills        BETWEEN 1 AND 10)
        AND (cv_projects      IS NULL OR cv_projects      BETWEEN 1 AND 10)
        AND (cv_certificates  IS NULL OR cv_certificates  BETWEEN 1 AND 10))
);

CREATE INDEX IX_candidates_track_status ON candidates (track_id, screening_status);
