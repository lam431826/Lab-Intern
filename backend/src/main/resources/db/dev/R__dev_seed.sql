-- DỮ LIỆU MẪU, chỉ nạp ở profile local (thư mục db/dev). Không bao giờ chạy trên database thật.
-- Tạo sẵn một tài khoản Người tổ chức để đăng nhập lần đầu bằng Clerk;
-- email lấy từ biến DEV_ADMIN_EMAIL trong .env. Để trống thì không tạo gì.

IF N'${devAdminEmail}' <> N''
   AND NOT EXISTS (SELECT 1 FROM app_users WHERE email = N'${devAdminEmail}')
BEGIN
    INSERT INTO app_users (email, full_name, role) VALUES (N'${devAdminEmail}', N'Người tổ chức (dev)', N'ORGANIZER');
END;
