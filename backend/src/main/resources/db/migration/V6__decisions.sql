-- Kết luận tuyển dụng, phê duyệt và theo dõi offer (Phân hệ 7).
-- Điểm tổng không lưu ở đây: luôn tính lại từ bảng scores để không bị lệch.

CREATE TABLE decisions (
    id                  BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_decisions PRIMARY KEY,
    candidate_id        BIGINT          NOT NULL CONSTRAINT FK_decisions_candidate REFERENCES candidates (id),
    final_status        NVARCHAR(20)     NOT NULL CONSTRAINT DF_decisions_final_status DEFAULT 'PENDING',
    transfer_track_id   BIGINT          NULL CONSTRAINT FK_decisions_transfer_track REFERENCES tracks (id),
    note                NVARCHAR(1000)  NULL,
    approved            BIT             NOT NULL CONSTRAINT DF_decisions_approved DEFAULT 0,
    approved_by         BIGINT          NULL CONSTRAINT FK_decisions_approved_by REFERENCES app_users (id),
    approved_at         DATETIME2(3)    NULL,
    email_status        NVARCHAR(20)     NOT NULL CONSTRAINT DF_decisions_email_status DEFAULT 'NOT_SENT',
    offer_status        NVARCHAR(20)     NOT NULL CONSTRAINT DF_decisions_offer_status DEFAULT 'NONE',
    row_version         INT             NOT NULL CONSTRAINT DF_decisions_row_version DEFAULT 0,
    created_at          DATETIME2(3)    NOT NULL CONSTRAINT DF_decisions_created_at DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT          NULL,
    updated_at          DATETIME2(3)    NOT NULL CONSTRAINT DF_decisions_updated_at DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT          NULL,
    CONSTRAINT UQ_decisions_candidate UNIQUE (candidate_id),
    CONSTRAINT CK_decisions_final_status CHECK (final_status IN ('PENDING', 'HIRED', 'WAITLIST', 'TRANSFER', 'REJECTED')),
    CONSTRAINT CK_decisions_transfer CHECK (final_status <> 'TRANSFER' OR transfer_track_id IS NOT NULL),
    CONSTRAINT CK_decisions_approval CHECK (approved = 0 OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)),
    CONSTRAINT CK_decisions_email_status CHECK (email_status IN ('NOT_SENT', 'SENT')),
    CONSTRAINT CK_decisions_offer_status CHECK (offer_status IN ('NONE', 'ACCEPTED', 'DECLINED'))
);

CREATE INDEX IX_decisions_final_status ON decisions (final_status);
