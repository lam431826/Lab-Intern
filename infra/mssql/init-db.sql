-- Khởi tạo database cho Hệ thống Tuyển dụng DX Lab.
-- Chạy một lần bằng tài khoản sysadmin, KHÔNG thuộc Flyway.
-- Chạy được nhiều lần: đối tượng đã có thì bỏ qua.
--
--   sqlcmd -S localhost -E -C -f 65001 -i infra/mssql/init-db.sql ^
--          -v DB_NAME="dxlab_recruitment" APP_LOGIN="dxlab_app" APP_PASSWORD="<mật khẩu>"
--
-- Collation Latin1_General_100_CI_AI: không phân biệt hoa thường, không phân biệt dấu kể cả đ/ê/ơ...
-- (tìm "nguyen" ra "Nguyễn", "dang" ra "Đặng"). Không dùng Vietnamese_100_CI_AI: collation đó coi
-- ê, ơ, ư, â, ă, đ là chữ cái riêng nên "nguyen" KHÔNG khớp "Nguyễn" (đã kiểm chứng 03/11/2026).

SET NOCOUNT ON;

IF DB_ID(N'$(DB_NAME)') IS NULL
BEGIN
    PRINT N'Tạo database $(DB_NAME)';
    EXEC (N'CREATE DATABASE [$(DB_NAME)] COLLATE Latin1_General_100_CI_AI');
END
ELSE
    PRINT N'Database $(DB_NAME) đã tồn tại';
GO

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'$(APP_LOGIN)')
BEGIN
    PRINT N'Tạo login $(APP_LOGIN)';
    EXEC (N'CREATE LOGIN [$(APP_LOGIN)] WITH PASSWORD = N''$(APP_PASSWORD)'', '
        + N'DEFAULT_DATABASE = [$(DB_NAME)], CHECK_POLICY = ON, CHECK_EXPIRATION = OFF');
END
ELSE
    PRINT N'Login $(APP_LOGIN) đã tồn tại';
GO

USE [$(DB_NAME)];
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'$(APP_LOGIN)')
    CREATE USER [$(APP_LOGIN)] FOR LOGIN [$(APP_LOGIN)];

-- db_ddladmin để Flyway tạo và sửa bảng; đọc/ghi dữ liệu cho ứng dụng.
-- Không cấp db_owner: tài khoản ứng dụng không được xóa database hay đổi quyền.
ALTER ROLE db_ddladmin   ADD MEMBER [$(APP_LOGIN)];
ALTER ROLE db_datareader ADD MEMBER [$(APP_LOGIN)];
ALTER ROLE db_datawriter ADD MEMBER [$(APP_LOGIN)];
GO

SELECT name AS database_name, collation_name FROM sys.databases WHERE name = N'$(DB_NAME)';
GO
