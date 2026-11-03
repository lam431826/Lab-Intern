-- Nhật ký thay đổi trạng thái (thay Version History của Google Sheet)
-- và danh sách webhook Clerk đã xử lý (chống xử lý trùng).

CREATE TABLE status_history (
    id            BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_status_history PRIMARY KEY,
    entity_type   NVARCHAR(30)     NOT NULL,
    entity_id     BIGINT          NOT NULL,
    field         NVARCHAR(50)     NOT NULL,
    old_value     NVARCHAR(200)   NULL,
    new_value     NVARCHAR(200)   NULL,
    note          NVARCHAR(500)   NULL,
    changed_by    BIGINT          NULL CONSTRAINT FK_status_history_changed_by REFERENCES app_users (id),
    changed_at    DATETIME2(3)    NOT NULL CONSTRAINT DF_status_history_changed_at DEFAULT SYSUTCDATETIME()
);

CREATE INDEX IX_status_history_entity ON status_history (entity_type, entity_id, changed_at);

CREATE TABLE processed_webhooks (
    svix_id        NVARCHAR(100)   NOT NULL CONSTRAINT PK_processed_webhooks PRIMARY KEY,
    event_type     NVARCHAR(100)   NOT NULL,
    processed_at   DATETIME2(3)   NOT NULL CONSTRAINT DF_processed_webhooks_processed_at DEFAULT SYSUTCDATETIME()
);
