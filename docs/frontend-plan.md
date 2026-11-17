# Kế hoạch Frontend — Hệ thống Tuyển dụng DX Lab

*Lập ngày 17/11/2026, dựa trên backend tại commit `e5d24e8`: B0–B9 cùng module "Lịch sử gửi nhu cầu tuyển dụng" (V9).*

Tài liệu liên quan: [`api.md`](api.md) (hợp đồng API), [`business-flow-report.md`](business-flow-report.md) (luồng nghiệp vụ), [`project-structure.md`](project-structure.md) §3 (khung thư mục đã thống nhất).

---

## 1. Mục tiêu và nguyên tắc

- Phủ toàn bộ nghiệp vụ backend đã có. Frontend **không chứa logic nghiệp vụ**: quyền, điểm, xếp hạng và gợi ý đều lấy từ API. Frontend chỉ ẩn hoặc hiện nút theo vai trò để dễ dùng; chặn thật vẫn là việc của backend.
- Giao diện tiếng Việt, hiển thị giờ theo `Asia/Ho_Chi_Minh`, số thập phân dùng dấu phẩy (8,10).
- **Phiếu chấm phải dùng tốt trên điện thoại**, vì giám khảo chấm ngay trong buổi phỏng vấn. Các màn hình còn lại ưu tiên máy tính.
- Mọi lỗi API hiển thị theo `code` ổn định; người dùng không bao giờ thấy lỗi kỹ thuật thô.

## 2. Công nghệ

| Hạng mục | Lựa chọn | Lý do |
|---|---|---|
| Nền tảng | React + TypeScript + Vite | Đã chốt trong `project-structure.md` |
| Đăng nhập | `@clerk/react` (SDK thế hệ hiện tại; `@clerk/clerk-react` là bản cũ), giao diện Clerk dùng theme shadcn của `@clerk/ui` | Backend đã xác thực token Clerk; `getToken()` cấp Bearer. Ứng dụng Clerk: `app_3MDW6ryV9VCHUYrJEqzy2kLbVf1`, liên kết bằng `clerk link`, không tạo ứng dụng mới |
| Gọi API, cache | TanStack Query | Cache theo key, tự tải lại sau khi ghi, xử lý loading và error thống nhất |
| Định tuyến | React Router (data router) | Chặn route theo vai trò |
| Giao diện | **Tailwind CSS v4** (`@tailwindcss/vite`) + **shadcn/ui** (Radix) | Đã chốt. Component shadcn được chép vào `components/ui/` nên sửa thoải mái, không bị khóa vào thư viện. Icon dùng `lucide-react`, thông báo dùng `sonner` |
| Form | `react-hook-form` + Zod (component `Form` của shadcn) | Kiểm tra phía client khớp ràng buộc backend (1–10, độ dài, bắt buộc); gắn `fieldErrors` 422 vào ô bằng `setError` |
| Bảng lớn | TanStack Table (mẫu data-table của shadcn) | Lọc, sắp xếp, cố định cột cho bảng hồ sơ và bảng kết quả |
| Ngày giờ | `date-fns` (locale `vi`) + `react-day-picker` (Calendar của shadcn) | Chọn ngày ca, hiển thị giờ Việt Nam |
| Kiểu dữ liệu API | `openapi-typescript`, sinh từ springdoc (xem F0) | Tránh lệch kiểu với khoảng 65 endpoint; dự phòng là viết tay `api/types.ts` |
| Test | Vitest + Testing Library + MSW; Playwright cho e2e | MSW giả lập API theo đúng mã lỗi của backend |

Phiên bản thư viện được ghim chính xác lúc dựng khung (F0); không dùng `^` cho thư viện chính.

## 3. Kiến trúc

```
frontend/src/
├── main.tsx                 ClerkProvider (localization viVN) → QueryClientProvider → RouterProvider; Toaster (sonner)
├── app/
│   ├── routes.tsx           khai báo route kèm vai trò được vào
│   ├── AppLayout.tsx        sidebar theo vai trò, header (tên, vai trò, đăng xuất)
│   ├── RequireRole.tsx      chặn route; vai trò sai thì về trang 403
│   └── AccessGate.tsx       sau đăng nhập gọi /api/me: 403 → màn "Chưa được cấp quyền"
├── api/
│   ├── client.ts            fetch + Bearer + parse ApiError; tải và gửi file
│   ├── schema.d.ts          sinh từ OpenAPI
│   └── <module>.ts          hàm gọi API và hook useQuery/useMutation theo module
├── features/<module>/       màn hình và component riêng của module
├── components/ui/           component shadcn (button, dialog, table, form, select, tabs…)
├── components/              StatusBadge, ScoreInput, RubricHint, ConfirmDialog, FileDropzone,
│                            WarningList, StaleDataNotice, EmptyState, DownloadButton
├── hooks/                   useMe, useCan(role | trackId), useDownload
└── lib/                     format (ngày giờ VN, điểm), errorMessages.ts (code → câu tiếng Việt)
```

### 3.1. Lớp API (`client.ts`)
- Mỗi request lấy token mới bằng `getToken()` (Clerk tự làm mới token). Không lưu token vào localStorage.
- Phản hồi lỗi được parse thành `ApiError {status, code, message, fieldErrors}`:
  - **401**: gọi `signOut()` rồi chuyển về trang đăng nhập.
  - **403** `FORBIDDEN` / `SUBMIT_FIRST`: hiển thị đúng thông báo backend trả về.
  - **409** `STALE_DATA`: hiện `StaleDataNotice` kèm nút "Tải lại", và giữ nguyên nội dung người dùng đang nhập để họ không mất bài.
  - **422** `VALIDATION_FAILED`: gắn `fieldErrors` vào đúng ô của form.
  - Các mã nghiệp vụ khác: notification với `message` tiếng Việt của backend.
- **Tải file** (Excel, file JD): không dùng `<a href>` được, vì endpoint cần header Bearer. Thay vào đó: `fetch` → `blob` → lấy tên file từ `Content-Disposition` → tạo link tạm để tải về. Backend đã expose header `Content-Disposition` qua CORS.
- **Gửi file**: `FormData` với trường `file`. Kiểm tra trước ở client (`.xlsx` cho nhập hồ sơ; PDF/DOCX ≤ 5 MB cho JD) để báo lỗi sớm; backend vẫn kiểm tra lại.

### 3.2. Trạng thái và cache
- Query key theo tài nguyên: `['candidates', filter]`, `['sessions', id]`, `['results', trackId]`…
- Sau mỗi thao tác ghi thì invalidate các key liên quan. Ví dụ: điểm danh làm mới `sessions/{id}`, `me/scoring` và `dashboard`; chốt kết luận làm mới `results/{trackId}` và `dashboard`.
- Mọi request ghi có trường `version` đều lấy `version` từ dữ liệu đang hiển thị, không bao giờ tự đoán.
- Phiếu chấm tự lưu nháp sau 2 giây không gõ phím. Version mới nhận về được dùng cho lần lưu kế tiếp, tránh tự gây `STALE_DATA` cho chính mình (đúng lỗi đã gặp và sửa ở B7).

### 3.3. Phân quyền trên giao diện
`useMe()` trả `{role, leadTrackCodes}`. `useCan` gói các quy tắc **chỉ dùng để ẩn hoặc hiện**, ví dụ chỉ Giảng viên thấy nút "Phê duyệt", Lead chỉ thấy nút sàng lọc ở ngạch mình. Backend luôn là nơi quyết định cuối cùng.

## 4. Danh sách màn hình

Ký hiệu: **O** Người tổ chức · **L** Giảng viên · **D** Lead · **M** Thành viên.

| # | Màn hình | Vai trò | Endpoint chính | Ghi chú |
|---|---|---|---|---|
| S1 | Đăng nhập, chưa được cấp quyền | tất cả | Clerk, `GET /api/me` | Màn 403 hướng dẫn liên hệ Người tổ chức; có nút đăng xuất |
| S2 | Dashboard | tất cả | `GET /api/dashboard` | Thẻ việc cần làm (phiếu cần chấm, hồ sơ chờ duyệt, kết luận chờ duyệt) bấm vào là tới đúng màn hình; bảng tiến độ theo ngạch |
| S3 | Tài khoản | O (L chỉ xem) | `/api/users`, `/invite` | Tạo, mời, gửi lại lời mời, đổi vai trò, khóa. Hiển thị lỗi `LAST_ORGANIZER`, `USER_IS_TRACK_LEAD` |
| S4 | Ngạch và tiêu chí | O; D (tiêu chí ngạch mình) | `/api/tracks`, `/api/criteria` | Sửa chỉ tiêu, số slot, Lead; tiêu chí kèm 3 mức thang chấm |
| S5 | Tham số chấm | O, L | `/api/settings` | Hai trọng số luôn cộng bằng 1 (kéo một ô thì ô kia tự đổi); điểm sàn, SLA, độ dài slot |
| S6 | Lịch sử gửi nhu cầu tuyển dụng | O, D (sửa vị trí ngạch mình), L (xem) | `/api/recruitment-submissions…` | Danh sách lọc theo kỳ, ngạch, trạng thái. Chi tiết: vị trí, JD, file. "Đánh dấu đã gửi" có hộp xác nhận; bản SENT chỉ đọc và có nút "Tạo bản thay thế" |
| S7 | Hồ sơ ứng viên | O, L, D | `/api/candidates`, `/summary` | Bộ đếm theo ngạch, lọc (ngạch, trạng thái, quá hạn, từ khóa không dấu), cờ lọc nhanh, cột SLA |
| S8 | Chi tiết hồ sơ và sàng lọc | O, L, D, M (hội đồng) | `/api/candidates/{id}`, `/screening`, `/history`, `/slots` | 5 ô điểm CV, Đạt/Loại (Loại bắt buộc lý do), dòng thời gian lịch sử, lịch phỏng vấn |
| S9 | Nhập Excel | O | `/import-template`, `/import?dryRun=` | 3 bước: tải mẫu, chọn file và kiểm tra thử (`dryRun`, hiện lỗi theo số dòng), nhập thật |
| S10 | Danh sách ca | tất cả (lọc theo quyền) | `GET /api/sessions` | Xem dạng lịch theo ngày và dạng danh sách; mỗi ca có huy hiệu cảnh báo |
| S11 | Chi tiết ca | O sửa; hội đồng điểm danh và ghi log | `/api/sessions/{id}`, `/panel`, `/slots`, `/auto-schedule`, `/log`, `/progress` | Dòng thời gian slot; tự xếp lịch (hiện danh sách không xếp được); xếp tay; dời lịch sang PV2 kèm lý do; hủy; điểm danh; danh sách cảnh báo; tiến độ chấm |
| S12 | Ca của tôi / cần chấm | tất cả | `GET /api/me/scoring` | Trang chính của Thành viên, tối ưu cho điện thoại |
| S13 | Phiếu chấm | hội đồng của ca | `/scoresheet` | Mỗi tiêu chí: nút chọn 1–10 kèm gợi ý thang chấm; nhận xét; đề xuất; tự lưu nháp; nộp. Hiển thị khóa khi đã phê duyệt |
| S14 | Điểm hội đồng | O, L, D, hội đồng (sau khi nộp) | `/api/candidates/{id}/scores` | Ma trận giám khảo × tiêu chí; `SUBMIT_FIRST` hiện lời nhắc nộp phiếu |
| S15 | Bảng kết quả | O, L, D | `/api/results`, `/{trackId}` | Thanh chỉ tiêu (đã dùng / còn lại); bảng hạng, tổng, gợi ý, kết luận; tô màu dòng có kết luận khác gợi ý |
| S16 | Kết luận, phê duyệt, offer | O, L (duyệt) | `/decision`, `/approve`, `/reopen`, `/offer`, `/promote`, `/results/{id}/approve` | Hộp thoại kết luận (bắt buộc lý do khi khác gợi ý, chọn ngạch khi Chuyển ngạch); duyệt cả ngạch; gửi email và ghi nhận offer; nút "Gọi dự bị" chỉ hiện ở `nextWaitlistCandidateId` |
| S17 | Xuất file | O, L | `/api/sessions/export`, `/api/results/export` | Tải bằng `useDownload` |

**Thiết kế cho điện thoại** (S12, S13, S11 phần điểm danh): ô bấm tối thiểu 44 px; chọn điểm bằng dãy 10 nút thay cho ô nhập số; thang chấm thu gọn mở bằng một chạm; nút "Nộp phiếu" cố định ở đáy màn hình; báo rõ trạng thái "Đã lưu nháp lúc 10:32".

## 5. Lộ trình

Mỗi mốc là một nhánh hoặc PR riêng, tách commit nhỏ, không có dòng co-author.

| Mốc | Nội dung | Nghiệm thu |
|---|---|---|
| **F0. Khung** | Vite, TS, ESLint, Prettier, Tailwind v4, shadcn/ui (style `base-nova`, font Geist có bộ ký tự tiếng Việt), Router, Query; `client.ts`; proxy `/api` sang `localhost:8080`; đọc `.env` ở gốc repo (`envDir: '..'`, Vite chỉ nhúng biến có tiền tố `VITE_`). Thêm springdoc vào backend (kiểm tra tương thích Spring Boot 4) và script `npm run gen:api` | `npm run build` sạch; gọi được `/api/me` qua proxy. **Xong 17/11/2026** |
| **F1. Đăng nhập và khung app** | Clerk, `AccessGate`, `AppLayout`, menu theo vai trò, trang 403/404, `errorMessages.ts` | Đăng nhập thật bằng Clerk, thấy đúng tên và vai trò; tài khoản chưa cấp quyền thấy màn S1. **Xong 17/11/2026** (đăng nhập thật bằng Google, tài khoản liên kết Clerk) |
| **F2. Quản trị** | S3, S4, S5 | Tạo và mời tài khoản; sửa ngạch, tiêu chí, tham số; hiển thị đúng lỗi nghiệp vụ  **Xong 17/11/2026** (thử trên Chrome: tạo tài khoản, chặn tự đổi vai trò, gán Lead, thêm/xóa tiêu chí, đổi trọng số) |
| **F3. Lịch sử gửi nhu cầu** | S6 | Tạo nháp, thêm vị trí và file JD, đánh dấu đã gửi; bản SENT chỉ đọc |
| **F4. Hồ sơ và sàng lọc** | S7, S8, S9 | Nhập file mẫu 40 dòng, xem lỗi theo dòng; Lead sàng lọc; hai tab cùng sửa thì tab sau thấy thông báo `STALE_DATA` |
| **F5. Lịch phỏng vấn** | S10, S11, S17 (lịch) | Kịch bản: tạo ca, tự xếp, vắng PV1, dời sang PV2, điểm danh; cảnh báo hiển thị đúng |
| **F6. Chấm điểm** | S12, S13, S14 | Chấm trọn một phiếu trên điện thoại (Chrome DevTools, iPhone SE); tự lưu nháp; nộp; `SUBMIT_FIRST` |
| **F7. Kết quả** | S15, S16, S17 (kết quả) | Chạy lại bộ dữ liệu mẫu B8 (AI01–AI05) qua giao diện, số liệu khớp; luồng từ chối offer rồi gọi dự bị |
| **F8. Dashboard và hoàn thiện** | S2; trạng thái rỗng, skeleton, kiểm tra accessibility cơ bản, tiêu đề trang | Mỗi vai trò đăng nhập vào là thấy ngay việc cần làm |
| **F9. Đóng gói** | `frontend/Dockerfile` (Node build → nginx unprivileged), service `web` trong compose, cấu hình chạy runtime; cập nhật runbook; e2e Playwright | `docker compose up`: `web` phục vụ giao diện và chuyển `/api` sang `app` |

Làm tuần tự F0 → F9 theo đúng luồng nghiệp vụ. F0 không cần khóa Clerk; từ F1 trở đi thì bắt buộc phải có.

## 6. Kiểm thử

- **Unit**: `lib/format`, `errorMessages`, `useCan`, logic hai trọng số luôn cộng bằng 1.
- **Component** (Vitest, Testing Library, MSW): mỗi form chính có test cho lỗi 422 gắn vào ô, 409 `STALE_DATA` giữ nội dung đã nhập, và ẩn nút theo vai trò.
- **E2E** (Playwright, chạy trên stack `docker compose`): đăng nhập bằng `@clerk/testing` với tài khoản test của instance Clerk Development, rồi chạy 3 kịch bản: sàng lọc → xếp lịch → điểm danh; chấm và nộp phiếu; kết luận → phê duyệt → offer → gọi dự bị.
- **Không thêm cơ chế bỏ qua đăng nhập** vào backend để phục vụ test, vì repo đang public và cơ chế đó có thể lọt lên production.

## 7. Triển khai: image frontend riêng

```
Trình duyệt ──HTTPS──> Caddy (TLS) ──> web (nginx, cổng 8080 trong container)
                                         ├── /            → file tĩnh dist/, mọi route SPA trả index.html
                                         └── /api/, /actuator/health → app:8080 (mạng nội bộ compose)
```

- **`frontend/Dockerfile`** có hai stage: `node` (ghim phiên bản) chạy `npm ci && npm run build`, sau đó `nginxinc/nginx-unprivileged` chỉ chứa `dist/` và `nginx.conf`, chạy bằng user không phải root.
- **Một origin cho cả giao diện và API**: nginx chuyển `/api` sang container `app`, nên trình duyệt không cần CORS ở production. `app` không còn mở cổng ra ngoài, chỉ `web` nhận request.
- **Cấu hình lúc chạy, không lúc build**: lúc container khởi động, entrypoint ghi `/config.js` (`window.__APP_CONFIG__ = {clerkPublishableKey: …}`) từ biến môi trường `CLERK_PUBLISHABLE_KEY`. Nhờ vậy cùng một image dùng được cho cả Clerk development và production. Khóa publishable được thiết kế để công khai; mọi khóa bí mật vẫn chỉ nằm ở backend.
- **nginx**:
  - `index.html` và `config.js` gửi kèm `Cache-Control: no-cache`; `assets/*` có hash nên cache 1 năm (`immutable`).
  - Bật gzip.
  - Header bảo mật: CSP cho phép domain Clerk (`*.clerk.accounts.dev` ở dev, Frontend API của instance production), `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `frame-ancestors 'none'`.
  - `client_max_body_size 6m`, khớp giới hạn upload của backend.
- **Trên máy dev**: `npm run dev` (Vite cổng 5173, proxy `/api` sang `localhost:8080`). Không cần build image.
- **Runbook §3**: Caddy chỉ còn lo HTTPS và `reverse_proxy web:8080`. Cập nhật ở F9.

## 8. Việc backend cần làm kèm

| Việc | Mốc |
|---|---|
| Thêm springdoc-openapi (chỉ bật ở profile `local`) để sinh kiểu dữ liệu | F0 |
| Compose: thêm service `web`, bỏ `ports` của `app`. `CLERK_AZP` và `APP_CORS_ORIGINS` là domain thật của giao diện | F9 |

Image riêng nên backend **không cần** controller forward route SPA nữa. Danh sách hồ sơ hiện trả toàn bộ, không phân trang, đủ dùng với quy mô một đợt (vài trăm hồ sơ); phân trang chỉ thêm khi thực sự cần.

## 9. Quyết định đã chốt (17/11/2026)

1. Giao diện dùng **Tailwind CSS + shadcn/ui**.
2. Clerk cấu hình theo [`clerk-setup.md`](clerk-setup.md). Khóa điền vào `.env` ở gốc repo; cần có trước F1.
3. Frontend đóng **image riêng** (nginx), xem §7.
4. **Toàn bộ frontend, F0–F9**, do một người làm, kể cả màn hình F3 (Lịch sử gửi nhu cầu).
