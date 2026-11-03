# Vận hành — Hệ thống Tuyển dụng DX Lab

Tài liệu cho người triển khai và trông máy chủ. Mọi lệnh chạy từ thư mục gốc repo, cạnh `docker-compose.yml`.

## 1. Thành phần

| Container | Vai trò |
|---|---|
| `mssql` | SQL Server 2022 (bản Express, miễn phí). Dữ liệu nằm trong volume `mssql-data`, bản sao lưu nằm ở thư mục `./backup` |
| `db-init` | Chạy một lần mỗi khi `up`: tạo database với collation không dấu và login ứng dụng, rồi thoát. Chạy lại không sao |
| `app` | Backend Spring Boot, cổng `APP_PORT` (mặc định 8080). Lúc khởi động, Flyway tự áp dụng migration mới |

Cổng SQL Server chỉ mở cho chính máy chủ (`127.0.0.1:1433`), không mở ra mạng ngoài.

## 2. Triển khai lần đầu

Cần: Docker Engine 24+ có Compose v2, máy chủ ra được Internet (để tải khóa JWKS của Clerk), RAM tối thiểu 4 GB (SQL Server cần 2 GB).

1. **Lấy mã nguồn và tạo `.env`**
   ```bash
   git clone <repo> /opt/dxlab-recruitment && cd /opt/dxlab-recruitment
   cp .env.example .env && chmod 600 .env
   ```
2. **Điền `.env`**

   | Biến | Giá trị |
   |---|---|
   | `MSSQL_SA_PASSWORD`, `DB_PASSWORD` | Hai mật khẩu khác nhau, dài ít nhất 16 ký tự, chỉ gồm chữ và số (ví dụ `openssl rand -base64 24 \| tr -dc A-Za-z0-9`) |
   | `CLERK_ISSUER`, `CLERK_SECRET_KEY`, `CLERK_WEBHOOK_SECRET` | Lấy từ instance **Production** của Clerk (mục 4) |
   | `CLERK_AZP`, `APP_CORS_ORIGINS`, `APP_FRONTEND_URL` | Địa chỉ trang web của hệ thống, ví dụ `https://tuyendung.dxlab.vn` |
   | `BOOTSTRAP_ORGANIZER_EMAIL` | Email của Người tổ chức đầu tiên |

   Không cần sửa `SPRING_PROFILES_ACTIVE` và `DB_HOST`: compose tự ghi đè thành `prod` và `mssql`.
3. **Thư mục sao lưu** (chỉ trên Linux): SQL Server trong container chạy bằng uid 10001.
   ```bash
   sudo chown 10001:0 backup && sudo chmod 770 backup
   ```
4. **Khởi động**
   ```bash
   docker compose up -d --build
   docker compose ps          # app: healthy; db-init: exited (0)
   docker compose logs app | grep -E "Successfully applied|Started"
   ```
5. **Đăng nhập lần đầu**: Người tổ chức đăng nhập Clerk bằng đúng email `BOOTSTRAP_ORGANIZER_EMAIL`, rồi vào mục Tài khoản để mời Giảng viên, Lead và Thành viên. Sau đó xóa giá trị `BOOTSTRAP_ORGANIZER_EMAIL` trong `.env`.

## 3. Reverse proxy và HTTPS

Đặt Nginx hoặc Caddy phía trước, chuyển `/api` và `/actuator/health` vào `app:8080`. Profile `prod` đã bật `forward-headers-strategy`, nên ứng dụng nhận đúng địa chỉ gốc qua `X-Forwarded-*`.

Ví dụ Caddy (tự lấy chứng chỉ HTTPS):
```
tuyendung.dxlab.vn {
    handle /api/*    { reverse_proxy 127.0.0.1:8080 }
    handle           { root * /srv/dxlab-frontend; try_files {path} /index.html; file_server }
}
```
Khi đã có proxy, nên đổi cổng của `app` trong compose thành `127.0.0.1:8080:8080` để không ai vào thẳng cổng 8080.

## 4. Cấu hình Clerk cho production

1. Tạo instance **Production** riêng; không dùng chung instance Development với máy dev.
2. **Sessions → Customize session token**, thêm claim:
   ```json
   { "email": "{{user.primary_email_address}}" }
   ```
   Thiếu claim này thì tài khoản không liên kết được khi đăng nhập lần đầu.
3. **User & Authentication**: bật Email và Google. Tắt đăng ký tự do (**Restrictions → Sign-up mode: Restricted**), vì người dùng mới vào qua lời mời của Người tổ chức.
4. **Webhooks**: endpoint `https://<tên miền>/api/webhooks/clerk`, chọn sự kiện `user.created`, `user.updated`, `user.deleted`, rồi chép *Signing secret* vào `CLERK_WEBHOOK_SECRET`.
5. Giá trị **Frontend API URL** điền vào `CLERK_ISSUER`, **Secret key** điền vào `CLERK_SECRET_KEY`.

## 5. Cập nhật phiên bản

```bash
./infra/backup/backup.sh            # luôn sao lưu trước khi cập nhật
git pull
docker compose up -d --build app
docker compose logs -f app          # xem Flyway áp dụng migration mới, đợi "Started"
```
Migration không chạy được thì ứng dụng không khởi động, và Flyway rollback migration đó, nên dữ liệu cũ vẫn nguyên. Khi đó, đọc log, sửa lỗi rồi deploy lại, hoặc quay về phiên bản trước bằng `git checkout <tag>` rồi `up -d --build app`.

## 6. Sao lưu và khôi phục

**Sao lưu** (full backup kèm `RESTORE VERIFYONLY`, mặc định giữ 30 ngày):
```bash
./infra/backup/backup.sh                       # Linux
powershell -File infra\backup\backup.ps1       # Windows
```
Đặt lịch chạy hằng đêm:
```
0 2 * * * cd /opt/dxlab-recruitment && ./infra/backup/backup.sh >> backup/backup.log 2>&1
```
Thư mục `backup/` nằm trên cùng máy chủ, nên **phải chép thêm ra nơi khác** (NAS hoặc ổ mạng của lab), ví dụ bằng `rsync` hằng đêm. File `.bak` chứa dữ liệu cá nhân của ứng viên, chỉ lưu ở nơi có kiểm soát truy cập. Git đã được cấu hình để bỏ qua các file này.

**Khôi phục** (ghi đè database hiện tại, phải gõ tên database để xác nhận):
```bash
./infra/backup/restore.sh dxlab_recruitment_20261103_020000.bak
```
Script dừng `app`, khôi phục, nối lại user `dxlab_app` với login (cần khi bản sao lưu đến từ máy chủ khác), rồi chạy lại `app`. Nên diễn tập khôi phục mỗi học kỳ một lần, trên một máy thử.

## 7. Chuyển sang máy chủ mới

1. Trên máy mới, làm mục 2 với cùng `DB_PASSWORD`, để trống `BOOTSTRAP_ORGANIZER_EMAIL`, rồi chạy `docker compose up -d`.
2. Chép file `.bak` mới nhất vào `backup/`, chạy `restore.sh`.
3. Trỏ tên miền và URL webhook Clerk sang máy mới.

## 8. Theo dõi và xử lý sự cố

| Hiện tượng | Kiểm tra |
|---|---|
| `app` không lên, log có `Thiếu cấu hình CLERK_ISSUER` | Điền `CLERK_ISSUER` trong `.env`, chạy `docker compose up -d app` |
| `db-init` thoát với mã khác 0 | `docker compose logs db-init`. Thường do `MSSQL_SA_PASSWORD` sai hoặc không đủ độ phức tạp, hoặc `DB_PASSWORD` không đạt chính sách mật khẩu |
| Flyway báo `Unable to acquire SQL Server application lock` | Có ai đã đặt `MSSQL_COLLATION` cho container. Bỏ biến đó; collation chỉ đặt ở cấp database |
| Mọi request đều nhận 401 | Đồng hồ máy chủ lệch (token chỉ được lệch tối đa 30 giây), `CLERK_ISSUER` không phải của instance đang dùng, hoặc máy chủ không ra được Internet để tải JWKS |
| Đăng nhập được nhưng nhận 403 | Email Clerk chưa có tài khoản trong hệ thống, tài khoản đã bị khóa, hoặc thiếu claim `email` (mục 4.2) |
| Webhook nhận 400 `INVALID_SIGNATURE` | `CLERK_WEBHOOK_SECRET` không khớp với endpoint trong Clerk |

Xem log: `docker compose logs -f app`. Kiểm tra sống: `curl -fsS localhost:8080/actuator/health`.

## 9. Đổi mật khẩu

- **`DB_PASSWORD`**: chạy `ALTER LOGIN [dxlab_app] WITH PASSWORD = N'...'` bằng tài khoản `sa`, sửa `.env`, rồi chạy `docker compose up -d app`.
- **`MSSQL_SA_PASSWORD`**: chạy `ALTER LOGIN [sa] WITH PASSWORD = N'...'`, rồi sửa `.env`. Biến môi trường của container chỉ có tác dụng ở lần khởi tạo đầu tiên của volume.
- **Khóa Clerk**: tạo khóa mới trên Dashboard, sửa `.env`, chạy `docker compose up -d app`, rồi thu hồi khóa cũ.
