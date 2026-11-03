-- Tiêu chí chấm điểm và tham số tính điểm.

CREATE TABLE criteria (
    id            BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_criteria PRIMARY KEY,
    -- NULL = tiêu chí chung, áp dụng cho mọi ngạch
    track_id      BIGINT          NULL CONSTRAINT FK_criteria_track REFERENCES tracks (id),
    crit_group    NVARCHAR(20)     NOT NULL,
    name          NVARCHAR(200)   NOT NULL,
    description   NVARCHAR(2000)  NULL,
    -- Thang hướng dẫn chấm: Trung bình (1–6), Khá – Đạt (7–8), Xuất sắc (9–10)
    rubric_low    NVARCHAR(1000)  NULL,
    rubric_mid    NVARCHAR(1000)  NULL,
    rubric_high   NVARCHAR(1000)  NULL,
    sort_order    INT             NOT NULL CONSTRAINT DF_criteria_sort_order DEFAULT 0,
    active        BIT             NOT NULL CONSTRAINT DF_criteria_active DEFAULT 1,
    created_at    DATETIME2(3)    NOT NULL CONSTRAINT DF_criteria_created_at DEFAULT SYSUTCDATETIME(),
    created_by    BIGINT          NULL,
    updated_at    DATETIME2(3)    NOT NULL CONSTRAINT DF_criteria_updated_at DEFAULT SYSUTCDATETIME(),
    updated_by    BIGINT          NULL,
    CONSTRAINT CK_criteria_group CHECK (crit_group IN ('GENERAL', 'PROFESSIONAL')),
    -- Tiêu chí chung không thuộc ngạch nào; tiêu chí chuyên môn bắt buộc thuộc một ngạch.
    CONSTRAINT CK_criteria_group_track CHECK (
        (crit_group = 'GENERAL' AND track_id IS NULL) OR (crit_group = 'PROFESSIONAL' AND track_id IS NOT NULL))
);

CREATE INDEX IX_criteria_track_id ON criteria (track_id);

-- Đúng một dòng (id = 1).
CREATE TABLE scoring_settings (
    id                   INT           NOT NULL CONSTRAINT PK_scoring_settings PRIMARY KEY,
    weight_general       DECIMAL(4,2)  NOT NULL,
    weight_professional  DECIMAL(4,2)  NOT NULL,
    pass_threshold       DECIMAL(4,2)  NOT NULL,
    sla_hours            INT           NOT NULL,
    slot_minutes         INT           NOT NULL,
    updated_at           DATETIME2(3)  NOT NULL CONSTRAINT DF_scoring_settings_updated_at DEFAULT SYSUTCDATETIME(),
    updated_by           BIGINT        NULL,
    CONSTRAINT CK_scoring_settings_singleton CHECK (id = 1),
    CONSTRAINT CK_scoring_settings_weights CHECK (
        weight_general >= 0 AND weight_professional >= 0 AND weight_general + weight_professional = 1.00),
    CONSTRAINT CK_scoring_settings_threshold CHECK (pass_threshold BETWEEN 1 AND 10),
    CONSTRAINT CK_scoring_settings_sla CHECK (sla_hours > 0),
    CONSTRAINT CK_scoring_settings_slot CHECK (slot_minutes BETWEEN 5 AND 60)
);
