# Hệ thống Tuyển dụng DX Lab

Thay bộ file Word/Excel tuyển dụng thực tập sinh của DX Lab: hồ sơ và sàng lọc, lịch phỏng vấn theo ca, chấm điểm hội đồng, kết quả và phê duyệt.

- Thiết kế: [`docs/backend-plan.md`](docs/backend-plan.md), [`docs/project-structure.md`](docs/project-structure.md)
- Backend: Spring Boot 4 · Java 21 · SQL Server 2022 · Flyway · Clerk + Spring Security

## Chạy backend trên máy dev

Cần: JDK 21+, SQL Server 2022 (instance mặc định, đã bật mixed mode và TCP 1433), Docker Desktop (cho test).

1. **Tạo file cấu hình**
   ```
   copy .env.example .env
   ```
   Điền `DB_PASSWORD` bằng một mật khẩu mạnh.

2. **Tạo database và login ứng dụng** (một lần, bằng tài khoản Windows có quyền sysadmin)
   ```
   sqlcmd -S localhost -E -C -f 65001 -i infra/mssql/init-db.sql -v DB_NAME="dxlab_recruitment" APP_LOGIN="dxlab_app" APP_PASSWORD="<mật khẩu trong .env>"
   ```

3. **Chạy ứng dụng**
   ```
   cd backend
   mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```
   Flyway tự áp dụng migration trong `backend/src/main/resources/db/migration` khi khởi động.

4. **Chạy test** (Testcontainers tự dựng SQL Server trong Docker)
   ```
   cd backend
   mvnw verify
   ```

## Triển khai bằng Docker

```
copy .env.example .env      # điền MSSQL_SA_PASSWORD, DB_PASSWORD, khóa Clerk, BOOTSTRAP_ORGANIZER_EMAIL
docker compose up -d --build
```

Compose dựng SQL Server, tạo database và login (`db-init`), rồi chạy backend ở profile `prod`. Triển khai, Clerk production, sao lưu, khôi phục và xử lý sự cố: xem [`docs/runbook.md`](docs/runbook.md). API: [`docs/api.md`](docs/api.md).

Trên máy dev đã có SQL Server cài sẵn, cổng 1433 bị chiếm, nên đặt `MSSQL_HOST_PORT=14330` khi chạy compose.

## Quy tắc khi sửa database

- Mọi thay đổi schema là một file Flyway mới `V<số>__<mô_tả>.sql`. **Không sửa file đã chạy.**
- Hibernate chỉ `validate`; entity và migration sửa trong cùng một thay đổi.
- Dữ liệu mẫu đặt trong `db/dev`, chỉ nạp ở profile `local`.
