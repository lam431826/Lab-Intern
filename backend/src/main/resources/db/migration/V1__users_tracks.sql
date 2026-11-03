-- Tài khoản nhân sự lab và các ngạch tuyển dụng.
-- Quy ước chung cho mọi migration:
--   * Mọi cột chữ (kể cả mã và enum) là NVARCHAR, khớp hibernate.use_nationalized_character_data.
--   * Thời điểm lưu UTC trong DATETIME2(3).
--   * created_by / updated_by là id của app_users, không đặt khóa ngoại để bảng nào cũng audit được.

CREATE TABLE app_users (
    id              BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_app_users PRIMARY KEY,
    clerk_user_id   NVARCHAR(64)    NULL,
    email           NVARCHAR(254)  NOT NULL,
    full_name       NVARCHAR(100)  NOT NULL,
    role            NVARCHAR(20)    NOT NULL,
    active          BIT            NOT NULL CONSTRAINT DF_app_users_active DEFAULT 1,
    created_at      DATETIME2(3)   NOT NULL CONSTRAINT DF_app_users_created_at DEFAULT SYSUTCDATETIME(),
    created_by      BIGINT         NULL,
    updated_at      DATETIME2(3)   NOT NULL CONSTRAINT DF_app_users_updated_at DEFAULT SYSUTCDATETIME(),
    updated_by      BIGINT         NULL,
    CONSTRAINT UQ_app_users_email UNIQUE (email),
    CONSTRAINT CK_app_users_role CHECK (role IN ('LECTURER', 'ORGANIZER', 'LEAD', 'MEMBER'))
);

-- Một tài khoản Clerk chỉ gắn với một người; cho phép nhiều dòng chưa liên kết (NULL).
CREATE UNIQUE INDEX UX_app_users_clerk_user_id ON app_users (clerk_user_id) WHERE clerk_user_id IS NOT NULL;

CREATE TABLE tracks (
    id                  BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_tracks PRIMARY KEY,
    code                NVARCHAR(30)    NOT NULL,
    name                NVARCHAR(100)  NOT NULL,
    quota               INT            NOT NULL,
    slot_min            INT            NOT NULL,
    slot_max            INT            NOT NULL,
    requires_portfolio  BIT            NOT NULL CONSTRAINT DF_tracks_requires_portfolio DEFAULT 0,
    lead_user_id        BIGINT         NULL CONSTRAINT FK_tracks_lead REFERENCES app_users (id),
    sort_order          INT            NOT NULL CONSTRAINT DF_tracks_sort_order DEFAULT 0,
    created_at          DATETIME2(3)   NOT NULL CONSTRAINT DF_tracks_created_at DEFAULT SYSUTCDATETIME(),
    created_by          BIGINT         NULL,
    updated_at          DATETIME2(3)   NOT NULL CONSTRAINT DF_tracks_updated_at DEFAULT SYSUTCDATETIME(),
    updated_by          BIGINT         NULL,
    CONSTRAINT UQ_tracks_code UNIQUE (code),
    CONSTRAINT CK_tracks_quota CHECK (quota >= 0),
    CONSTRAINT CK_tracks_slots CHECK (slot_min >= 0 AND slot_min <= slot_max)
);

CREATE INDEX IX_tracks_lead_user_id ON tracks (lead_user_id);
