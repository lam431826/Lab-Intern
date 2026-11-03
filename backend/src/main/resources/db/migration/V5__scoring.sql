-- Phiếu chấm của từng giám khảo (Phân hệ 4).
-- Mỗi ô điểm là một dòng, nên tính trung bình theo tiêu chí, theo giám khảo hay theo khối đều bằng SQL được.

CREATE TABLE scores (
    id             BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_scores PRIMARY KEY,
    candidate_id   BIGINT        NOT NULL CONSTRAINT FK_scores_candidate REFERENCES candidates (id),
    evaluator_id   BIGINT        NOT NULL CONSTRAINT FK_scores_evaluator REFERENCES app_users (id),
    criterion_id   BIGINT        NOT NULL CONSTRAINT FK_scores_criterion REFERENCES criteria (id),
    value          INT           NOT NULL,
    row_version    INT           NOT NULL CONSTRAINT DF_scores_row_version DEFAULT 0,
    created_at     DATETIME2(3)  NOT NULL CONSTRAINT DF_scores_created_at DEFAULT SYSUTCDATETIME(),
    created_by     BIGINT        NULL,
    updated_at     DATETIME2(3)  NOT NULL CONSTRAINT DF_scores_updated_at DEFAULT SYSUTCDATETIME(),
    updated_by     BIGINT        NULL,
    CONSTRAINT UQ_scores_candidate_evaluator_criterion UNIQUE (candidate_id, evaluator_id, criterion_id),
    CONSTRAINT CK_scores_value CHECK (value BETWEEN 1 AND 10)
);

CREATE INDEX IX_scores_evaluator_id ON scores (evaluator_id);
CREATE INDEX IX_scores_criterion_id ON scores (criterion_id);

CREATE TABLE evaluator_notes (
    id               BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_evaluator_notes PRIMARY KEY,
    candidate_id     BIGINT          NOT NULL CONSTRAINT FK_evaluator_notes_candidate REFERENCES candidates (id),
    evaluator_id     BIGINT          NOT NULL CONSTRAINT FK_evaluator_notes_evaluator REFERENCES app_users (id),
    comment          NVARCHAR(2000)  NULL,
    recommendation   NVARCHAR(10)     NULL,
    -- NULL = phiếu nháp; có giá trị = giám khảo đã nộp, mới được xem điểm của người khác
    submitted_at     DATETIME2(3)    NULL,
    row_version      INT             NOT NULL CONSTRAINT DF_evaluator_notes_row_version DEFAULT 0,
    created_at       DATETIME2(3)    NOT NULL CONSTRAINT DF_evaluator_notes_created_at DEFAULT SYSUTCDATETIME(),
    created_by       BIGINT          NULL,
    updated_at       DATETIME2(3)    NOT NULL CONSTRAINT DF_evaluator_notes_updated_at DEFAULT SYSUTCDATETIME(),
    updated_by       BIGINT          NULL,
    CONSTRAINT UQ_evaluator_notes_candidate_evaluator UNIQUE (candidate_id, evaluator_id),
    CONSTRAINT CK_evaluator_notes_recommendation CHECK (recommendation IS NULL OR recommendation IN ('PASS', 'FAIL'))
);

CREATE INDEX IX_evaluator_notes_evaluator_id ON evaluator_notes (evaluator_id);
