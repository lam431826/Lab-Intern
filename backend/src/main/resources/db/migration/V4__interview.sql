-- Ca phỏng vấn, hội đồng và slot của từng ứng viên (Phân hệ 2).

CREATE TABLE interview_sessions (
    id             BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_interview_sessions PRIMARY KEY,
    track_id       BIGINT          NOT NULL CONSTRAINT FK_interview_sessions_track REFERENCES tracks (id),
    session_date   DATE            NOT NULL,
    start_time     TIME(0)         NOT NULL,
    end_time       TIME(0)         NOT NULL,
    room           NVARCHAR(50)    NULL,
    -- Interview Log: đánh giá chung chất lượng ca
    log_summary    NVARCHAR(MAX)   NULL,
    created_at     DATETIME2(3)    NOT NULL CONSTRAINT DF_interview_sessions_created_at DEFAULT SYSUTCDATETIME(),
    created_by     BIGINT          NULL,
    updated_at     DATETIME2(3)    NOT NULL CONSTRAINT DF_interview_sessions_updated_at DEFAULT SYSUTCDATETIME(),
    updated_by     BIGINT          NULL,
    CONSTRAINT CK_interview_sessions_time CHECK (start_time < end_time)
);

CREATE INDEX IX_interview_sessions_track_date ON interview_sessions (track_id, session_date);

CREATE TABLE panel_members (
    id           BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_panel_members PRIMARY KEY,
    session_id   BIGINT        NOT NULL CONSTRAINT FK_panel_members_session REFERENCES interview_sessions (id) ON DELETE CASCADE,
    user_id      BIGINT        NOT NULL CONSTRAINT FK_panel_members_user REFERENCES app_users (id),
    panel_role   NVARCHAR(20)   NOT NULL,
    created_at   DATETIME2(3)  NOT NULL CONSTRAINT DF_panel_members_created_at DEFAULT SYSUTCDATETIME(),
    created_by   BIGINT        NULL,
    updated_at   DATETIME2(3)  NOT NULL CONSTRAINT DF_panel_members_updated_at DEFAULT SYSUTCDATETIME(),
    updated_by   BIGINT        NULL,
    CONSTRAINT UQ_panel_members_session_user UNIQUE (session_id, user_id),
    CONSTRAINT CK_panel_members_role CHECK (panel_role IN ('LECTURER', 'ORGANIZER', 'LEAD', 'ROTATING'))
);

CREATE INDEX IX_panel_members_user_id ON panel_members (user_id);

CREATE TABLE interview_slots (
    id               BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_interview_slots PRIMARY KEY,
    session_id       BIGINT          NOT NULL CONSTRAINT FK_interview_slots_session REFERENCES interview_sessions (id),
    candidate_id     BIGINT          NOT NULL CONSTRAINT FK_interview_slots_candidate REFERENCES candidates (id),
    start_time       TIME(0)         NOT NULL,
    end_time         TIME(0)         NOT NULL,
    slot_type        NVARCHAR(5)      NOT NULL CONSTRAINT DF_interview_slots_slot_type DEFAULT 'PV1',
    -- PV1 bị thay bằng PV2 thì active = 0 và replaced_by_id trỏ sang slot mới
    active           BIT             NOT NULL CONSTRAINT DF_interview_slots_active DEFAULT 1,
    attendance       NVARCHAR(20)     NOT NULL CONSTRAINT DF_interview_slots_attendance DEFAULT 'PENDING',
    note             NVARCHAR(500)   NULL,
    replaced_by_id   BIGINT          NULL CONSTRAINT FK_interview_slots_replaced_by REFERENCES interview_slots (id),
    created_at       DATETIME2(3)    NOT NULL CONSTRAINT DF_interview_slots_created_at DEFAULT SYSUTCDATETIME(),
    created_by       BIGINT          NULL,
    updated_at       DATETIME2(3)    NOT NULL CONSTRAINT DF_interview_slots_updated_at DEFAULT SYSUTCDATETIME(),
    updated_by       BIGINT          NULL,
    CONSTRAINT CK_interview_slots_time CHECK (start_time < end_time),
    CONSTRAINT CK_interview_slots_type CHECK (slot_type IN ('PV1', 'PV2')),
    CONSTRAINT CK_interview_slots_attendance CHECK (attendance IN ('PENDING', 'ATTENDED', 'ABSENT')),
    CONSTRAINT CK_interview_slots_replaced CHECK (replaced_by_id IS NULL OR active = 0)
);

-- Mỗi ứng viên chỉ có một slot đang hiệu lực (giữ quy tắc "một lịch đang mở" của labintern).
CREATE UNIQUE INDEX UX_interview_slots_candidate_active ON interview_slots (candidate_id) WHERE active = 1;
CREATE INDEX IX_interview_slots_session_id ON interview_slots (session_id);
