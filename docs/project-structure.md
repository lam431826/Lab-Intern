# Cấu trúc dự án — Hệ thống Tuyển dụng DX Lab

Các quyết định đã chốt (03/11/2026):

| # | Câu hỏi | Chốt |
|---|---|---|
| 1 | Server lab có ra Internet | Có, nên dùng Clerk |
| 2 | Lưu email và tên nhân sự trên Clerk | Thầy đồng ý (chỉ nhân sự, không có dữ liệu ứng viên) |
| 3 | Đăng nhập Google của trường | Bật Google trên Clerk, vẫn chỉ vào được qua lời mời |
| 4 | Tìm kiếm không phân biệt dấu | Database dùng collation `Latin1_General_100_CI_AI` (xem ghi chú ở mục 4) |

---

## 1. Toàn bộ repo

```
recruitment-system/
├── backend/                      Spring Boot 4 · Java 21 · SQL Server · Flyway · Clerk
├── frontend/                     React · TypeScript · Vite · Clerk React SDK
├── infra/
│   ├── docker-compose.yml        mssql + app (server lab)
│   ├── docker-compose.dev.yml    chỉ mssql (máy chưa cài SQL Server)
│   ├── mssql/
│   │   └── init-db.sql           tạo DB (collation CI_AI), login dxlab_app, phân quyền
│   └── backup/
│       └── backup.sql            BACKUP DATABASE định kỳ
├── docs/
│   ├── backend-plan.md
│   ├── project-structure.md      (file này)
│   ├── api.md                    danh sách endpoint, sinh dần theo từng mốc
│   └── runbook.md                cách cài, sao lưu, khôi phục, đổi khóa Clerk
├── .env.example                  mọi biến môi trường, không chứa giá trị thật
├── .gitignore
└── README.md                     chạy local trong 5 bước
```

Frontend và backend tách thư mục nhưng **build chung một ảnh Docker**: bước build copy `frontend/dist` vào `backend/src/main/resources/static`, Spring Boot phục vụ cả giao diện lẫn `/api`. Server lab chỉ chạy 2 container: `app` và `mssql`.

---

## 2. Backend

Chia **theo nghiệp vụ** (package-by-feature). Mỗi phân hệ có đủ entity, repository, service, controller và DTO của mình. Phần dùng chung nằm trong `common`, `config`, `security`.

```
backend/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/
├── Dockerfile                                    multi-stage: node build FE → maven build → JRE 21
└── src/
    ├── main/
    │   ├── java/vn/dxlab/recruitment/
    │   │   ├── RecruitmentApplication.java
    │   │   │
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java           filter chain stateless, CORS, quy tắc URL
    │   │   │   ├── JwtDecoderConfig.java         JWKS Clerk + validator issuer/thời gian/azp
    │   │   │   ├── ClerkProperties.java          @ConfigurationProperties("app.clerk")
    │   │   │   ├── CacheConfig.java              cache sub → người dùng, 60 giây
    │   │   │   ├── JpaAuditingConfig.java        tự điền created_by/updated_by
    │   │   │   └── SpaForwardingController.java  route không phải /api → index.html
    │   │   │
    │   │   ├── security/
    │   │   │   ├── AzpValidator.java             azp phải thuộc danh sách origin được phép
    │   │   │   ├── ClerkJwtAuthenticationConverter.java   sub → app_users, liên kết lần đầu theo email
    │   │   │   ├── AppUserPrincipal.java         id, role, fullName
    │   │   │   ├── CurrentUser.java              lấy người dùng hiện tại trong service
    │   │   │   └── AuthorizationService.java     bean "@authz": isPanelMember, canScore, isLeadOf, canSeeCandidate
    │   │   │
    │   │   ├── clerk/
    │   │   │   ├── ClerkWebhookController.java   POST /api/webhooks/clerk
    │   │   │   ├── SvixSignatureVerifier.java    xác minh svix-id / svix-timestamp / svix-signature
    │   │   │   ├── ClerkWebhookService.java      user.updated, user.deleted; chống xử lý trùng
    │   │   │   ├── ProcessedWebhook.java         entity lưu svix-id đã xử lý
    │   │   │   └── ClerkInvitationClient.java    gọi Backend API Clerk để gửi lời mời
    │   │   │
    │   │   ├── common/
    │   │   │   ├── BaseEntity.java               id, created_at/by, updated_at/by
    │   │   │   ├── error/
    │   │   │   │   ├── ApiError.java             { code, message, fieldErrors }
    │   │   │   │   ├── BusinessException.java    lỗi nghiệp vụ có mã (409/422)
    │   │   │   │   ├── NotFoundException.java
    │   │   │   │   └── GlobalExceptionHandler.java
    │   │   │   ├── web/
    │   │   │   │   └── PageResponse.java         phân trang thống nhất
    │   │   │   └── excel/
    │   │   │       └── ExcelWriter.java          tiện ích POI dùng chung cho các file xuất
    │   │   │
    │   │   ├── audit/
    │   │   │   ├── StatusHistory.java            ai đổi gì, từ giá trị nào sang giá trị nào, lúc nào
    │   │   │   ├── StatusHistoryRepository.java
    │   │   │   └── StatusHistoryService.java
    │   │   │
    │   │   ├── user/                             Tài khoản nhân sự
    │   │   │   ├── AppUser.java
    │   │   │   ├── Role.java                     LECTURER, ORGANIZER, LEAD, MEMBER
    │   │   │   ├── AppUserRepository.java
    │   │   │   ├── UserService.java              tạo tài khoản + gửi lời mời, khóa/mở
    │   │   │   ├── UserController.java           /api/users, /api/me
    │   │   │   └── dto/  UserDto, CreateUserRequest, UpdateUserRequest
    │   │   │
    │   │   ├── track/                            Ngạch và tiêu chí
    │   │   │   ├── Track.java
    │   │   │   ├── Criterion.java
    │   │   │   ├── CriterionGroup.java           GENERAL, PROFESSIONAL
    │   │   │   ├── TrackRepository.java, CriterionRepository.java
    │   │   │   ├── TrackService.java, CriterionService.java
    │   │   │   ├── TrackController.java          /api/tracks
    │   │   │   ├── CriterionController.java      /api/criteria
    │   │   │   └── dto/
    │   │   │
    │   │   ├── settings/                         Trọng số, điểm sàn, SLA, độ dài slot
    │   │   │   ├── ScoringSettings.java
    │   │   │   ├── ScoringSettingsRepository.java
    │   │   │   ├── SettingsService.java
    │   │   │   └── SettingsController.java       /api/settings
    │   │   │
    │   │   ├── candidate/                        Phân hệ 1 · Hồ sơ và sàng lọc
    │   │   │   ├── Candidate.java
    │   │   │   ├── ScreeningStatus.java          PENDING, PASSED, REJECTED
    │   │   │   ├── CandidateRepository.java
    │   │   │   ├── CandidateSpecifications.java  lọc theo ngạch, trạng thái, từ khóa
    │   │   │   ├── CandidateService.java
    │   │   │   ├── ScreeningService.java         chấm 5 mục CV, cờ lọc nhanh, SLA
    │   │   │   ├── CandidateImportService.java   đọc Excel, báo lỗi theo dòng, upsert theo Mã SV
    │   │   │   ├── CandidateController.java      /api/candidates
    │   │   │   └── dto/  CandidateDto, ScreeningRequest, ImportReport
    │   │   │
    │   │   ├── interview/                        Phân hệ 2 · Lịch phỏng vấn
    │   │   │   ├── InterviewSession.java
    │   │   │   ├── PanelMember.java
    │   │   │   ├── PanelRole.java                LECTURER, ORGANIZER, LEAD, ROTATING
    │   │   │   ├── InterviewSlot.java
    │   │   │   ├── SlotType.java                 PV1, PV2
    │   │   │   ├── Attendance.java               PENDING, ATTENDED, ABSENT
    │   │   │   ├── *Repository.java
    │   │   │   ├── SessionService.java           tạo ca, hội đồng, cảnh báo ngoài khung giờ
    │   │   │   ├── SchedulingService.java        tự xếp slot, đổi lịch PV1 → PV2
    │   │   │   ├── AttendanceService.java        điểm danh, log ca
    │   │   │   ├── SessionController.java        /api/sessions
    │   │   │   ├── SlotController.java           /api/slots
    │   │   │   └── dto/
    │   │   │
    │   │   ├── scoring/                          Phân hệ 4 · Chấm điểm hội đồng
    │   │   │   ├── Score.java                    có @Version (row_version)
    │   │   │   ├── EvaluatorNote.java
    │   │   │   ├── Recommendation.java           PASS, FAIL
    │   │   │   ├── ScoreRepository.java, EvaluatorNoteRepository.java
    │   │   │   ├── ScoringService.java           lưu phiếu, tiến độ hội đồng
    │   │   │   ├── ScoringController.java        /api/scoring
    │   │   │   └── dto/  ScoreSheetDto, SubmitScoresRequest, PanelProgressDto
    │   │   │
    │   │   └── result/                           Phân hệ 7 · Kết quả và phê duyệt
    │   │       ├── Decision.java                 có @Version
    │   │       ├── FinalStatus.java              PENDING, HIRED, WAITLIST, TRANSFER, REJECTED
    │   │       ├── OfferStatus.java, EmailStatus.java
    │   │       ├── DecisionRepository.java
    │   │       ├── ScoreCalculator.java          hàm thuần: TB tiêu chí → khối → tổng; dễ unit test
    │   │       ├── ResultService.java            xếp hạng, so chỉ tiêu, gợi ý dự bị
    │   │       ├── DecisionService.java          kết luận, phê duyệt, khóa sau phê duyệt
    │   │       ├── ResultExportService.java      xuất Excel kết quả, danh sách gửi email
    │   │       ├── ResultController.java         /api/results
    │   │       ├── DashboardController.java      /api/dashboard
    │   │       └── dto/
    │   │
    │   └── resources/
    │       ├── application.yml                   cấu hình chung, đọc biến môi trường
    │       ├── application-local.yml             SQL Server trên máy, thêm db/dev vào Flyway
    │       ├── application-prod.yml              log JSON, tắt tài liệu API
    │       ├── db/
    │       │   ├── migration/
    │       │   │   ├── V1__users_tracks.sql
    │       │   │   ├── V2__criteria_settings.sql
    │       │   │   ├── V3__candidates.sql
    │       │   │   ├── V4__interview.sql
    │       │   │   ├── V5__scoring.sql
    │       │   │   ├── V6__decisions.sql
    │       │   │   ├── V7__status_history_webhooks.sql
    │       │   │   └── V8__seed_tracks_criteria.sql
    │       │   └── dev/
    │       │       └── R__dev_seed.sql           tài khoản và ứng viên MẪU, chỉ profile local
    │       ├── templates/
    │       │   └── import-candidates.xlsx        file mẫu để tải về
    │       └── static/                           (trống trong git, nhận frontend/dist khi build)
    │
    └── test/
        ├── java/vn/dxlab/recruitment/
        │   ├── support/
        │   │   ├── SqlServerContainerConfig.java Testcontainers mssql 2022, @ServiceConnection
        │   │   ├── IntegrationTest.java          annotation gộp: @SpringBootTest + container
        │   │   └── JwtFixtures.java              dựng JWT giả kiểu Clerk cho từng vai trò
        │   ├── migration/FlywayMigrationIT.java
        │   ├── security/
        │   │   ├── AuthenticationIT.java         401, azp sai, sub chưa liên kết, liên kết theo email
        │   │   └── AuthorizationIT.java          ngoài hội đồng không chấm được, Lead chỉ thấy ngạch mình
        │   ├── clerk/ClerkWebhookIT.java         sai chữ ký, gửi trùng svix-id
        │   ├── candidate/CandidateImportServiceTest.java
        │   ├── interview/SchedulingServiceTest.java
        │   ├── scoring/ConcurrentScoringIT.java
        │   └── result/ScoreCalculatorTest.java
        └── resources/
            ├── application-test.yml
            └── fixtures/candidates-40.xlsx
```

### Quy ước trong backend

| Quy ước | Nội dung |
|---|---|
| Phụ thuộc giữa phân hệ | Chỉ gọi qua **service** của phân hệ khác, không gọi repository của nhau. Chiều phụ thuộc: `result → scoring → interview → candidate → track → user` |
| Entity ra ngoài | Controller **không** trả entity; luôn map sang DTO (record) |
| Quyền | `@PreAuthorize` ở controller **và** kiểm tra lại trong service |
| Giao dịch | `@Transactional` đặt ở service; controller không mở transaction |
| Thời gian | Lưu UTC (`Instant`); ngày và giờ của ca dùng `LocalDate`, `LocalTime` theo giờ Việt Nam |
| Tên bảng, cột | `snake_case`, số nhiều cho bảng (`interview_slots`) |
| Thông báo lỗi | Tiếng Việt, có `code` ổn định để frontend xử lý |

---

## 3. Frontend

```
frontend/
├── package.json
├── vite.config.ts                proxy /api → localhost:8080 khi dev
├── tsconfig.json
├── index.html
├── .env.example                  VITE_CLERK_PUBLISHABLE_KEY
└── src/
    ├── main.tsx                  ClerkProvider + Router + QueryClient
    ├── app/
    │   ├── routes.tsx            khai báo route, chặn theo vai trò
    │   ├── AppLayout.tsx         thanh điều hướng theo vai trò
    │   └── RequireRole.tsx
    ├── api/
    │   ├── client.ts             fetch kèm Bearer từ Clerk getToken(), xử lý ApiError
    │   ├── users.ts, tracks.ts, candidates.ts, sessions.ts, scoring.ts, results.ts
    │   └── types.ts              kiểu dữ liệu khớp DTO backend
    ├── features/
    │   ├── dashboard/            Tổng quan, việc cần làm
    │   ├── candidates/           Bảng hồ sơ, nhập Excel, phiếu sàng lọc
    │   ├── sessions/             Danh sách ca, xếp lịch, đổi lịch, điểm danh, log ca
    │   ├── scoring/              Ca của tôi, phiếu chấm (tối ưu điện thoại)
    │   ├── results/              Bảng kết quả, ma trận điểm, kết luận, phê duyệt, offer
    │   └── settings/             Tài khoản, ngạch, tiêu chí, tham số chấm
    ├── components/               StatusBadge, ScoreInput (1–10), RubricHint, ConfirmDialog...
    ├── hooks/                    useMe, useRole
    └── styles/
```

---

## 4. Cấu hình và bí mật

`.env.example` ở gốc repo:

```
# SQL Server
DB_HOST=localhost
DB_PORT=1433
DB_NAME=dxlab_recruitment
DB_USER=dxlab_app
DB_PASSWORD=
DB_TRUST_CERT=true

# Clerk (Dashboard → API Keys)
CLERK_ISSUER=https://<slug>.clerk.accounts.dev
CLERK_AZP=http://localhost:5173
CLERK_SECRET_KEY=
CLERK_WEBHOOK_SECRET=
VITE_CLERK_PUBLISHABLE_KEY=

# Ứng dụng
APP_CORS_ORIGINS=http://localhost:5173
SPRING_PROFILES_ACTIVE=local
```

`infra/mssql/init-db.sql` tạo database bằng collation đã chốt:

```sql
CREATE DATABASE dxlab_recruitment COLLATE Latin1_General_100_CI_AI;
```

Với collation này, tìm "nguyen" ra "Nguyễn" và "dang" ra "Đặng". Ban đầu dự định dùng `Vietnamese_100_CI_AI`, nhưng khi kiểm chứng thì collation đó coi ê, ơ, ư, â, ă, đ là chữ cái riêng nên "nguyen" **không** khớp "Nguyễn". Đổi lại, tên được sắp xếp theo bảng chữ Latin (ă, â xếp cùng a). Mã SV và email chỉ gồm ký tự ASCII nên ràng buộc UNIQUE vẫn đúng.

---

## 5. Thứ tự tạo file theo lộ trình

| Mốc | Thư mục, file được tạo |
|---|---|
| B0 | `infra/mssql/init-db.sql`, `.env.example`, `README.md` |
| B1 | `pom.xml` (đổi sang SQL Server), `application*.yml`, `common/`, `config/` (trừ security), `test/support/` |
| B2 | `db/migration/V1`–`V8`, entity + repository của mọi phân hệ, `FlywayMigrationIT` |
| B3 | `security/`, `clerk/`, `config/SecurityConfig`, `JwtDecoderConfig`, `user/` + `/api/me`, test bảo mật |
| B4–B8 | service, controller, dto của `track`, `settings`, `candidate`, `interview`, `scoring`, `result` |
| B9 | `backend/Dockerfile`, `infra/docker-compose*.yml`, `docs/runbook.md`; dựng `frontend/` song song từ B3 |
