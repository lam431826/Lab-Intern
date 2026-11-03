# Kế hoạch dựng Backend — Hệ thống Tuyển dụng DX Lab

> Phạm vi: backend Spring Boot cho luồng lõi (Phân hệ 1, 2, 4, 7 trong thiết kế), dùng **SQL Server**, quản lý schema bằng **Flyway**, xác thực bằng **Clerk** và phân quyền bằng **Spring Security**.
> Trạng thái hiện tại: `recruitment-system/backend` mới là khung Spring Boot 4.0.8 (Java 21) vừa sinh, chưa có code nghiệp vụ. Pom đang trỏ H2 + PostgreSQL, cần đổi sang SQL Server.

---

## 0. Các quyết định nền

| Chủ đề | Quyết định | Lý do |
|---|---|---|
| CSDL | SQL Server 2022 (máy dev đã có instance `MSSQLSERVER`); Docker image `mcr.microsoft.com/mssql/server:2022-latest` cho server và test | Đúng yêu cầu; lab đã cài sẵn |
| Migration | Flyway (`spring-boot-starter-flyway` + `flyway-sqlserver`), JPA `ddl-auto=validate` | Schema chỉ thay đổi qua file SQL có version, Hibernate chỉ kiểm tra khớp |
| Xác thực | Clerk lo đăng nhập (UI, mật khẩu, Google, MFA). Backend là **OAuth2 Resource Server** xác minh session token (JWT RS256) của Clerk qua JWKS | Không tự lưu mật khẩu; tránh lặp lại lỗi `PasswordUtil` của labintern |
| Phân quyền | **Vai trò lưu trong DB của hệ thống**, không lưu trong Clerk. Clerk chỉ trả lời "người này là ai" (`sub = user_...`) | Quyền chấm điểm phụ thuộc ca phỏng vấn, phải tra DB; đổi vai trò không phải chờ token mới |
| Đăng ký | Tắt tự đăng ký trên Clerk, chỉ mời (Invitations). Người tổ chức tạo tài khoản trong hệ thống trước (email + vai trò), rồi gửi lời mời Clerk | Hệ thống nội bộ; tránh lỗi "tự đăng ký thành MENTOR" của labintern |
| Ứng viên | Ứng viên **không** đăng nhập. Chỉ nhân sự lab có tài khoản Clerk | Dữ liệu ứng viên không đi sang Clerk |

---

## 1. Kiến trúc và luồng xác thực

```
React (Clerk SDK) ──getToken()──► Authorization: Bearer <JWT 60s>
        │
        ▼
Spring Security filter chain
  1. BearerTokenAuthenticationFilter
  2. NimbusJwtDecoder: chữ ký (JWKS Clerk), exp/nbf, iss, azp ∈ allowed origins
  3. ClerkJwtAuthenticationConverter:
       sub ──► app_users.clerk_user_id ──► Role + trạng thái active
       chưa liên kết? ──► khớp claim email với app_users.email (đã được mời) ──► lưu clerk_user_id
       không khớp / bị khóa ──► 403
  4. Authentication(principal = AppUserPrincipal, authorities = ROLE_*)
        │
        ▼
Controller ──@PreAuthorize("hasRole('ORGANIZER')")
           ──@PreAuthorize("@authz.isPanelMember(#sessionId)")  ← quyền theo ca, tra DB
        │
        ▼
Service ──► JPA ──► SQL Server (schema do Flyway quản lý)

Clerk ──webhook (Svix ký)──► POST /api/webhooks/clerk
       user.updated / user.deleted ──► cập nhật email, khóa tài khoản
```

---

## 2. Cấu trúc mã nguồn

```
backend/src/main/java/vn/dxlab/recruitment/
├── RecruitmentApplication.java
├── config/            SecurityConfig, ClerkProperties, CorsConfig, JpaAuditingConfig
├── security/          ClerkJwtAuthenticationConverter, AudienceAzpValidator,
│                      AppUserPrincipal, AuthorizationService (@authz), CurrentUser
├── webhook/           ClerkWebhookController, SvixVerifier
├── common/            ApiError, GlobalExceptionHandler, BaseEntity (audit cột)
├── user/              AppUser, Role, UserController, UserService
├── track/             Track, Criterion, CriterionGroup, controllers
├── settings/          ScoringSettings
├── candidate/         Candidate, ScreeningStatus, CandidateService, ImportService (POI)
├── interview/         InterviewSession, PanelMember, InterviewSlot, Attendance
├── scoring/           Score, EvaluatorNote, ScoringService
├── result/            Decision, ResultService (tính điểm, xếp hạng), ExportService
└── audit/             StatusHistory (giữ ý tưởng candidate_request_history của labintern)

backend/src/main/resources/
├── application.yml
├── application-local.yml      SQL Server trên máy dev
├── application-prod.yml
└── db/migration/              V1__..., V2__..., R__...
```

---

## 3. Thay đổi `pom.xml`

| Bỏ | Thêm |
|---|---|
| `h2`, `spring-boot-h2console` | `com.microsoft.sqlserver:mssql-jdbc` (runtime, version theo Boot BOM) |
| `postgresql`, `flyway-database-postgresql` | `org.flywaydb:flyway-sqlserver` |
| | Test: `org.testcontainers:mssqlserver`, `org.testcontainers:junit-jupiter`, `spring-boot-testcontainers` |

Giữ: `starter-webmvc`, `starter-data-jpa`, `starter-flyway`, `starter-security`, `starter-security-oauth2-resource-server`, `starter-validation`, `poi-ooxml`, `lombok`.

---

## 4. SQL Server

### 4.1 Môi trường

| Môi trường | Nguồn | Cách chạy |
|---|---|---|
| Dev | Instance `MSSQLSERVER` trên máy | Tạo DB `dxlab_recruitment` + login `dxlab_app`, profile `local` |
| Test tự động | Testcontainers `mssql/server:2022-latest` | `@ServiceConnection`, mỗi lần chạy test dựng DB sạch và chạy Flyway từ V1 |
| Server lab | `docker-compose.yml`: `mssql` + `app` | Volume dữ liệu, healthcheck bằng `sqlcmd` |

Script khởi tạo một lần (không thuộc Flyway, chạy bằng quyền sa):

```sql
CREATE DATABASE dxlab_recruitment COLLATE Latin1_General_100_CI_AI;  -- tìm "nguyen" ra "Nguyễn", "dang" ra "Đặng"
CREATE LOGIN dxlab_app WITH PASSWORD = '<từ biến môi trường>';
USE dxlab_recruitment;
CREATE USER dxlab_app FOR LOGIN dxlab_app;
ALTER ROLE db_ddladmin ADD MEMBER dxlab_app;   -- để Flyway tạo bảng
ALTER ROLE db_datareader ADD MEMBER dxlab_app;
ALTER ROLE db_datawriter ADD MEMBER dxlab_app;
```

> **Lưu ý đã kiểm chứng (03/11/2026):** không đặt biến `MSSQL_COLLATION` cho container SQL Server. Trên SQL Server for Linux biến này làm hỏng `xp_userlock`, nên Flyway báo "Unable to acquire SQL Server application lock". Collation luôn đặt ở cấp database (`CREATE DATABASE ... COLLATE Vietnamese_100_CI_AI`), cả trong test lẫn `docker-compose`. Image được ghim tag `2022-CU20-ubuntu-22.04`, không dùng `2022-latest`.

### 4.2 Cấu hình

```yaml
spring:
  datasource:
    url: jdbc:sqlserver://${DB_HOST:localhost}:${DB_PORT:1433};databaseName=${DB_NAME:dxlab_recruitment};encrypt=true;trustServerCertificate=${DB_TRUST_CERT:true}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate.ddl-auto: validate
    open-in-view: false
    properties.hibernate.jdbc.time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-on-migrate: true
    clean-disabled: true        # không bao giờ xóa sạch DB thật
```

### 4.3 Quy ước kiểu dữ liệu

| Dữ liệu | Kiểu SQL Server | Ghi chú |
|---|---|---|
| Khóa chính | `BIGINT IDENTITY(1,1)` | JPA `GenerationType.IDENTITY` |
| Mọi cột chữ (kể cả mã, enum) | `NVARCHAR(n)`; văn bản dài `NVARCHAR(MAX)` | Bắt buộc N-type để giữ dấu; Hibernate bật `use_nationalized_character_data` |
| Enum | `NVARCHAR(20)` + `CHECK (... IN (...))` | JPA `@Enumerated(STRING)` |
| Boolean | `BIT NOT NULL DEFAULT 0` | |
| Thời điểm | `DATETIME2(3)`, lưu UTC | `Instant`; Hibernate đặt `preferred_instant_jdbc_type=TIMESTAMP` để không sinh `DATETIMEOFFSET` |
| Ngày / giờ ca | `DATE`, `TIME(0)` | `LocalDate`, `LocalTime` |
| Điểm | `INT` + `CHECK (value BETWEEN 1 AND 10)` | Không dùng `TINYINT` để Hibernate validate khớp `Integer` |
| Trọng số, điểm sàn | `DECIMAL(4,2)` | |
| Khóa tự nhiên | `UNIQUE` (Mã SV, clerk_user_id), unique có điều kiện dùng filtered index `WHERE clerk_user_id IS NOT NULL` | |
| Khóa lạc quan | `row_version INT NOT NULL DEFAULT 0` | JPA `@Version int` cho `candidates`, `scores`, `evaluator_notes`, `decisions` |

---

## 5. Migration với Flyway

### 5.1 Quy tắc

1. Tên file: `V<số>__<mô_tả>.sql`, ví dụ `V3__interview.sql`. Seed có thể chạy lại dùng `R__<mô_tả>.sql`.
2. **Không sửa file đã chạy** ở bất kỳ môi trường nào. Sai thì viết file V mới. `validate-on-migrate` sẽ chặn nếu checksum lệch.
3. Mỗi file làm một việc, có thể review riêng. Thay đổi phá dữ liệu (đổi tên, xóa cột) tách thành 2 bước: thêm mới + chép dữ liệu, rồi xóa ở bản sau.
4. Hibernate không sinh schema (`ddl-auto=validate`). Entity và migration được sửa trong cùng một PR.
5. Có test `FlywayMigrationIT` chạy toàn bộ migration trên SQL Server thật (Testcontainers), rồi để Hibernate validate.
6. `clean-disabled: true` ở mọi profile trừ test.

### 5.2 Danh sách migration ban đầu

| File | Nội dung |
|---|---|
| `V1__users_tracks.sql` | `app_users` (id, clerk_user_id UNIQUE có điều kiện, email UNIQUE, full_name, role CHECK, active, audit), `tracks` (code UNIQUE, name, quota, slot_min, slot_max, requires_portfolio, lead_user_id FK) |
| `V2__criteria_settings.sql` | `criteria` (track_id NULL = tiêu chí chung, crit_group, name, description, rubric_low/mid/high, sort_order, active), `scoring_settings` (1 dòng: weight_general, weight_professional, pass_threshold, sla_hours, slot_minutes) |
| `V3__candidates.sql` | `candidates` (student_code UNIQUE, full_name, email, phone, major, track_id, cv_link, portfolio_link, screening_status, screening_reason, cv_* 5 cột điểm, screened_by, screened_at, assigned_at) |
| `V4__interview.sql` | `interview_sessions` (track_id, session_date, start_time, end_time, room, log_summary), `panel_members` (session_id, user_id, panel_role, UNIQUE(session_id,user_id)), `interview_slots` (session_id, candidate_id, start_time, end_time, slot_type PV1/PV2, active, attendance, replaced_by_id) + filtered unique index "mỗi ứng viên một slot active" |
| `V5__scoring.sql` | `scores` (candidate_id, evaluator_id, criterion_id, value, row_version, UNIQUE bộ ba), `evaluator_notes` (comment, recommendation, UNIQUE(candidate_id, evaluator_id)) |
| `V6__decisions.sql` | `decisions` (candidate_id UNIQUE, final_status, transfer_track_id, note, approved, approved_by, approved_at, email_status, offer_status, row_version) |
| `V7__status_history.sql` | `status_history` (entity_type, entity_id, field, old_value, new_value, changed_by, changed_at, note) |
| `V8__seed_tracks_criteria.sql` | 6 ngạch kèm chỉ tiêu và số slot theo Kế hoạch; 4 tiêu chí chung + tiêu chí chuyên môn và thang chấm lấy từ file "Câu hỏi Phỏng vấn & Đánh giá"; 1 dòng `scoring_settings` mặc định |
| `R__dev_seed.sql` *(chỉ thư mục `db/dev`, profile local)* | Tài khoản mẫu và ứng viên mẫu đánh dấu rõ "MẪU" |

Profile `local` thêm `classpath:db/dev` vào `spring.flyway.locations`. Profile `prod` không có, nên dữ liệu mẫu không bao giờ vào DB thật.

---

## 6. Clerk + Spring Security

### 6.1 Cấu hình phía Clerk (Dashboard)

1. Tạo application "DX Lab Recruitment", bật Email (+ Google nếu lab dùng tài khoản trường).
2. **Restrictions → Sign-up mode: Restricted**, chỉ vào qua Invitations.
3. **Sessions → Customize session token**, thêm claim để backend liên kết lần đầu theo email:
   ```json
   { "email": "{{user.primary_email_address}}" }
   ```
4. Lấy các giá trị: Frontend API URL (dùng làm `issuer`), Publishable key (frontend), Secret key (gọi Backend API khi gửi lời mời), Webhook signing secret `whsec_...`.
5. Webhook trỏ về `https://<domain>/api/webhooks/clerk`, chọn sự kiện `user.created`, `user.updated`, `user.deleted`.

Hai instance riêng: **Development** cho máy dev, **Production** cho server lab.

### 6.2 Cấu hình Spring

```yaml
app:
  clerk:
    issuer: ${CLERK_ISSUER}                 # https://<slug>.clerk.accounts.dev
    authorized-parties: ${CLERK_AZP:http://localhost:5173}
    secret-key: ${CLERK_SECRET_KEY}
    webhook-secret: ${CLERK_WEBHOOK_SECRET}

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${CLERK_ISSUER}
          jwk-set-uri: ${CLERK_ISSUER}/.well-known/jwks.json
```

### 6.3 Thành phần cần viết

| Thành phần | Nhiệm vụ |
|---|---|
| `SecurityConfig` | `SecurityFilterChain` stateless, tắt CSRF (API dùng Bearer), CORS theo `authorized-parties`. `permitAll` chỉ cho `/api/webhooks/clerk` và `/actuator/health`; còn lại `authenticated()`. Bật `@EnableMethodSecurity` |
| `JwtDecoder` bean (`JwtDecoderConfig`) | `NimbusJwtDecoder.withJwkSetUri(...)` + validator gộp: issuer mặc định, timestamp (cho lệch đồng hồ 30s), `AzpValidator` (azp phải nằm trong `authorized-parties`) |
| `ClerkJwtAuthenticationConverter` | `sub` → tìm `app_users.clerk_user_id`. Nếu chưa có: lấy claim `email`, tìm user đã được tạo sẵn, chưa liên kết và đang active, rồi gán `clerk_user_id`. Nếu không thấy hoặc bị khóa → `AccessDeniedException` (403 "Tài khoản chưa được cấp quyền"). Trả về token với principal `AppUserPrincipal(id, role, fullName)` và authority `ROLE_<role>` |
| Cache | Cache ánh xạ `sub → principal` 60 giây (Caffeine) để mỗi request không phải truy vấn DB. Xóa cache khi đổi vai trò hoặc khóa tài khoản |
| `AuthorizationService` (`@authz`) | `isPanelMember(sessionId)`, `canScore(candidateId)` (có tên trong hội đồng của slot active và ứng viên đã điểm danh có mặt), `isLeadOf(trackId)`, `canSeeCandidate(candidateId)` |
| `ClerkWebhookController` | Đọc body thô, xác minh header `svix-id`, `svix-timestamp`, `svix-signature` bằng HMAC-SHA256 theo chuẩn Svix (tự cài trong `SvixSignatureVerifier`, không thêm thư viện; chặn gói tin lệch quá 5 phút), sai chữ ký → 400. `user.updated` cập nhật email; `user.deleted` khóa `app_users`. Idempotent theo `svix-id` |
| `ClerkInvitationClient` | Khi Người tổ chức tạo tài khoản, gọi Clerk Backend API `POST /v1/invitations` bằng `secret-key` để gửi email mời |
| `GlobalExceptionHandler` | 401/403/404/409/422 trả JSON `{code, message}` bằng tiếng Việt; không lộ stack trace |

### 6.4 Ma trận quyền ở tầng API

| Nhóm endpoint | Quy tắc |
|---|---|
| `/api/users/**`, `/api/settings` (ghi) | `ORGANIZER` (Giảng viên được sửa trọng số và điểm sàn) |
| `/api/candidates/**` | đọc: `ORGANIZER`, `LECTURER`, `LEAD` của ngạch; sàng lọc: `ORGANIZER` hoặc `@authz.isLeadOf(track)` |
| `/api/sessions/**` | ghi: `ORGANIZER`; đọc: tất cả, lọc theo hội đồng |
| `/api/scoring/**` | `@authz.canScore(#candidateId)` với mọi vai trò |
| `/api/results/**` | đọc: `LECTURER`, `ORGANIZER`, Lead của ngạch; kết luận: `ORGANIZER`, `LECTURER`; phê duyệt: chỉ `LECTURER` |

Mọi quy tắc được kiểm tra ở **cả** `@PreAuthorize` lẫn service. Đây là chỗ labintern bị sót (POST chấm điểm không kiểm tra quyền).

---

## 7. Kiểm thử

| Loại | Công cụ | Nội dung bắt buộc |
|---|---|---|
| Migration | Testcontainers SQL Server | Chạy V1→Vn trên DB trống; Hibernate validate pass; seed tạo đủ 6 ngạch |
| Bảo mật | `spring-security-test` `jwt()` | 401 khi không có token; 403 khi `azp` sai; 403 khi `sub` chưa liên kết; thành viên ngoài hội đồng không chấm được; Lead không xem được ngạch khác |
| Webhook | MockMvc | Sai chữ ký → 400; gửi lại cùng `svix-id` không xử lý hai lần |
| Nghiệp vụ | JUnit thuần | Công thức điểm (trung bình theo tiêu chí, theo khối, tổng có trọng số); xếp hạng; SLA; chuyển PV1→PV2; khóa kết luận sau phê duyệt |
| Đồng thời | Testcontainers | Hai giám khảo lưu điểm cùng lúc không ghi đè (`row_version`) |

`./mvnw verify` phải pass trước mỗi lần merge.

---

## 8. Lộ trình thực hiện

| Mốc | Việc | Xong khi |
|---|---|---|
| **B0. Chuẩn bị** | Tạo DB và login trên SQL Server local; tạo Clerk dev instance; điền `.env.example` | Kết nối được bằng `sqlcmd`; lấy được JWKS của Clerk |
| **B1. Nền dự án** | Sửa pom; `application*.yml`; `GlobalExceptionHandler`; `BaseEntity` audit; healthcheck | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` lên, Flyway báo "no migrations" |
| **B2. Schema** | V1–V8; entity + repository; `FlywayMigrationIT` | Test migration pass trên Testcontainers và trên DB local |
| **B3. Xác thực** | `SecurityConfig`, `JwtDecoder` + azp, converter liên kết lần đầu, `/api/me`, webhook, gửi lời mời | Đăng nhập Clerk ở trang test → gọi `/api/me` ra đúng vai trò; bộ test bảo mật pass |
| **B4. Người dùng, ngạch, tiêu chí** | CRUD tài khoản (kèm lời mời), ngạch, tiêu chí, settings | Người tổ chức cấu hình được toàn bộ qua API |
| **B5. Hồ sơ và sàng lọc** | Nhập/xuất Excel, sàng lọc, cờ lọc nhanh, SLA, lịch sử trạng thái | Nhập file mẫu 40 dòng, báo lỗi đúng dòng |
| **B6. Lịch phỏng vấn** | Ca, hội đồng, tự xếp lịch, PV1/PV2, điểm danh, log ca | Kịch bản đổi lịch và vắng mặt chạy đúng |
| **B7. Chấm điểm** | Phiếu chấm, kiểm tra quyền theo ca, tiến độ hội đồng | Thành viên ngoài hội đồng nhận 403 |
| **B8. Kết quả** | Tính điểm, xếp hạng, kết luận, phê duyệt, offer, dự bị, xuất Excel | Số liệu khớp phép tính tay trên bộ dữ liệu mẫu |
| **B9. Đóng gói** | Dockerfile multi-stage, `docker-compose.yml` (mssql + app), README vận hành, sao lưu DB | `docker compose up` trên máy sạch chạy được |

B0–B3 là phần nền và phải làm tuần tự. Từ B4 trở đi mỗi mốc là một PR độc lập.

---

## 9. Biến môi trường

```
DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, DB_TRUST_CERT
CLERK_ISSUER, CLERK_AZP, CLERK_SECRET_KEY, CLERK_WEBHOOK_SECRET
APP_CORS_ORIGINS
```

Chỉ commit `.env.example`, không commit `.env` (labintern đang làm đúng điểm này).

---

## 10. Rủi ro và câu hỏi cần chốt

| # | Vấn đề | Đề xuất |
|---|---|---|
| 1 | Clerk là dịch vụ ngoài: server lab cần ra được Internet để tải JWKS (JWKS được cache, nên mất mạng ngắn vẫn chạy được) | Chấp nhận; nếu lab offline hoàn toàn thì phải thay Clerk bằng Keycloak tự host |
| 2 | Webhook cần URL công khai; máy dev không có | Ở dev dùng Clerk CLI/ngrok, hoặc chỉ dựa vào liên kết lần đầu qua claim `email` (webhook là phần bổ sung) |
| 3 | Dữ liệu cá nhân của nhân sự (email, tên) nằm trên Clerk | Chỉ tài khoản nhân sự, không có dữ liệu ứng viên; cần thầy đồng ý |
| 4 | Testcontainers SQL Server cần Docker và chấp nhận license (`acceptLicense()`) | Máy dev đã có Docker Desktop |
| 5 | Tìm kiếm không phân biệt dấu | **Đã chốt:** database dùng `Latin1_General_100_CI_AI`. Không dùng `Vietnamese_100_CI_AI` vì collation đó coi ê, ơ, ư, â, ă, đ là chữ cái riêng nên "nguyen" không khớp "Nguyễn" (đã kiểm chứng). Đổi lại, sắp xếp tên theo bảng chữ Latin |
| 6 | Còn mở từ thiết kế: trọng số và điểm sàn, bộ tiêu chí SE và Game Dev | Seed V8 dùng giá trị đề xuất (40/60, sàn 7.0, tiêu chí theo file Word); sửa được qua API mà không cần migration |
