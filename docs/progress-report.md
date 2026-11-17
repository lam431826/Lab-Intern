# Báo cáo tiến độ — Hệ thống Tuyển dụng DX Lab

*Cập nhật: 17/11/2026 (lần trước: 03/11/2026)*

## 1. Tóm tắt

Hệ thống thay bộ file Word và Excel đang dùng để tuyển thực tập sinh của DX Lab: hồ sơ và sàng lọc CV, lịch phỏng vấn, chấm điểm hội đồng, kết quả và phê duyệt.

| Hạng mục | Trạng thái |
|---|---|
| Phân tích nghiệp vụ, thiết kế luồng, so sánh với dự án `labintern` | Xong |
| Kế hoạch backend và cấu trúc dự án | Xong ([`backend-plan.md`](backend-plan.md), [`project-structure.md`](project-structure.md)) |
| **Backend, mốc B0–B9** | **Xong**: 53 endpoint |
| Module "Lịch sử gửi nhu cầu tuyển dụng cho trường" (TrinhMinhDuc, 10/11) | **Xong**: 11 endpoint, migration V9. Đã sửa một lỗi đồng bộ dữ liệu (mục 3.2) |
| Đóng gói Docker, sao lưu, hướng dẫn vận hành | Xong, đã chạy thử toàn bộ stack bằng `docker compose` |
| Cấu hình Clerk (instance Development) | **Xong**: đã có khóa, claim `email` trong session token, chế độ chỉ đăng ký qua lời mời |
| Kế hoạch frontend | Xong ([`frontend-plan.md`](frontend-plan.md)): React + Tailwind + shadcn/ui, image riêng, 10 mốc F0–F9 |
| **Frontend F0: khung dự án** | **Xong** (17/11) |
| **Frontend F1: đăng nhập và khung ứng dụng** | **Đã code xong, chờ thử đăng nhập thật** (mục 5) |
| Frontend F2–F9 | Chưa bắt đầu |

## 2. Công nghệ

- **Backend:** Spring Boot 4 · Java 21 · SQL Server 2022 · Flyway · Spring Security (OAuth2 Resource Server) và Clerk · Apache POI (Excel) · springdoc (OpenAPI, chỉ ở máy dev) · Testcontainers · Docker Compose.
- **Frontend:** React 19 · TypeScript 7 · Vite 8 · Tailwind CSS 4 · shadcn/ui · TanStack Query · React Router · `@clerk/react` · Vitest.

## 3. Các mốc đã hoàn thành

### 3.1. Backend

| Mốc | Nội dung | Tiêu chí nghiệm thu |
|---|---|---|
| B0 | Khung Spring Boot, cấu hình qua `.env`, test chạy trên SQL Server thật (Testcontainers) | `mvnw verify` chạy được |
| B1 | 8 migration Flyway: tài khoản, ngạch, tiêu chí, hồ sơ, lịch, điểm, kết luận, lịch sử. Seed 6 ngạch và bộ tiêu chí theo file Word | Hibernate `validate` khớp schema; tìm "nguyen" ra "Nguyễn" |
| B2 | Xác thực token Clerk (RS256, issuer, azp), vai trò lưu trong DB, liên kết tài khoản qua email ở lần đăng nhập đầu, webhook Svix | 401 / 403 đúng từng trường hợp |
| B3 | Phân quyền theo dữ liệu (`@authz`): theo ngạch và theo hội đồng của ca | Lead không xem được ngạch khác |
| B4 | Tài khoản (mời qua Clerk), ngạch, tiêu chí, trọng số và điểm sàn | Không khóa được Người tổ chức cuối cùng |
| B5 | Hồ sơ: nhập Excel (nhận cả file kiểu cũ, báo lỗi theo dòng, có lỗi thì không lưu dòng nào), sàng lọc 5 mục CV, cờ lọc nhanh, SLA 36 giờ, lịch sử trạng thái | Báo lỗi đúng số dòng Excel |
| B6 | Ca phỏng vấn, hội đồng 6 người, tự xếp slot 15 phút, dời lịch PV1 → PV2 (giữ lịch cũ), điểm danh, Interview Log, xuất lịch Excel | Kịch bản vắng PV1 rồi dời sang PV2 chạy đúng |
| B7 | Phiếu chấm theo hội đồng, nháp và nộp, chấm độc lập (chưa nộp thì không xem điểm người khác), tiến độ hội đồng, chống ghi đè | Thành viên ngoài hội đồng nhận 403 |
| B8 | Điểm tổng 40/60, xếp hạng, gợi ý theo sàn và chỉ tiêu, kết luận, Giảng viên phê duyệt và khóa, offer, gọi dự bị theo thứ hạng, xuất Excel, dashboard | Số liệu khớp phép tính tay trên bộ dữ liệu mẫu |
| B9 | Dockerfile, `docker-compose.yml`, script sao lưu và khôi phục, [`runbook.md`](runbook.md) | Chạy được bằng `docker compose up` |

| V9 | Lịch sử gửi nhu cầu tuyển dụng cho trường: bản nháp, vị trí kèm JD (nội dung hoặc file PDF/DOCX), đánh dấu đã gửi thành snapshot không sửa được, bản thay thế | Bản đã gửi không sửa hoặc xóa được |

Danh sách endpoint, quyền và mã lỗi: [`api.md`](api.md). Luồng nghiệp vụ: [`business-flow-report.md`](business-flow-report.md).

### 3.2. Sửa lỗi trong đợt này

Hai test của module gửi nhu cầu tuyển dụng hỏng ngay trên commit gốc `e5d24e8`. Khi thêm hoặc xóa vị trí, danh sách vị trí của bản gửi trong bộ nhớ không được cập nhật. Hệ quả là thao tác "đánh dấu đã gửi" chạy trong cùng phiên có thể báo "chưa có vị trí", và lệnh xóa vị trí có thể bị Hibernate bỏ qua. Đã sửa ở commit `7bc15c9`; toàn bộ test pass.

### 3.3. Frontend

| Mốc | Nội dung | Nghiệm thu |
|---|---|---|
| F0 | Khung Vite, TypeScript, Tailwind v4, shadcn/ui. Lớp gọi API: token Clerk, lỗi theo `code`, tải và gửi file kèm token. Kiểu dữ liệu TypeScript sinh tự động từ OpenAPI của backend (`npm run gen:api`). Định dạng ngày giờ Việt Nam và điểm `8,10` | **Đạt**: build sạch, gọi `/api/me` qua proxy trả 401 đúng; secret key không lọt vào bundle |
| F1 | Đăng nhập Clerk (tiếng Việt, theme shadcn), link mời đưa thẳng tới trang đăng ký. Hỏi backend người dùng là ai; màn "Chưa được cấp quyền" (403) và "Máy chủ từ chối phiên" (401). Sidebar theo vai trò (thu gọn được, menu trượt trên điện thoại), chặn route theo vai trò, trang 404. Đổi người dùng thì xóa cache | Đã kiểm tra trên Chrome: chưa đăng nhập thì chuyển sang trang đăng nhập Clerk, console không lỗi. **Còn thiếu:** đăng nhập thật bằng tài khoản Người tổ chức |

## 4. Chất lượng

- **122 test tự động**, đều pass:
  - Backend 102 test: 19 unit test (công thức điểm, khung giờ, SLA, chữ ký webhook, tìm không dấu…) và 83 test tích hợp gọi API qua toàn bộ lớp bảo mật, trên SQL Server 2022 thật.
  - Frontend 20 test: lớp gọi API (token, mã lỗi, `STALE_DATA`, tải file tên tiếng Việt), định dạng giờ Việt Nam, menu theo vai trò, các trạng thái 200 / 401 / 403 khi đăng nhập.
- **Quy mô mã nguồn**:
  - Backend: khoảng 7.400 dòng mã chính (145 file), 2.900 dòng test (23 file), 9 migration, 64 endpoint.
  - Frontend: khoảng 1.300 dòng (30 file), không tính 14 component shadcn và file kiểu sinh tự động.
- **Bảo mật**:
  - Mọi quyền được kiểm tra ở cả controller lẫn service.
  - Bí mật chỉ nằm trong `.env`, không commit.
  - Tài khoản DB của ứng dụng không có quyền `db_owner`.
  - Container ứng dụng không chạy bằng root.
  - Cổng DB không mở ra mạng ngoài.
  - Frontend chỉ nhúng Clerk publishable key (khóa công khai); đã kiểm tra bundle không chứa secret key hay mật khẩu DB.
- **Toàn vẹn dữ liệu**:
  - Hai người cùng sửa một bản ghi thì người lưu sau nhận 409 `STALE_DATA` thay vì ghi đè.
  - Một ứng viên chỉ có một lịch đang hiệu lực, được ràng buộc ngay trong DB.
  - Điểm tổng không lưu sẵn mà luôn tính lại từ các phiếu đã nộp.

### Các điểm khắc phục so với `labintern`

| `labintern` | Hệ thống mới |
|---|---|
| API chấm điểm không kiểm tra quyền | Chỉ thành viên hội đồng của ca, với ứng viên đã có mặt, mới chấm được |
| Dời lịch sửa đè lên lịch cũ | PV1 được giữ lại, liên kết sang PV2, ghi kèm lý do |
| Cột "Tổng" trong Excel để trống hoặc tính sai | Điểm tính theo một công thức duy nhất, có test so với phép tính tay |
| Không có phê duyệt và khóa kết quả | Giảng viên phê duyệt rồi khóa cả kết luận lẫn phiếu chấm |

## 5. Việc còn lại

| # | Việc | Phụ thuộc |
|---|---|---|
| 1 | **Hoàn tất F1**: đăng nhập thật bằng tài khoản Người tổ chức | Cần email sẽ dùng để đăng nhập, điền vào `BOOTSTRAP_ORGANIZER_EMAIL` |
| 2 | Frontend F2–F9: quản trị, gửi nhu cầu tuyển dụng, hồ sơ, lịch, chấm điểm, kết quả, dashboard, đóng gói image nginx | F1 |
| 3 | Chia nhỏ bundle (hiện 223 KB sau gzip, chủ yếu do giao diện Clerk): tải mỗi màn hình khi cần | Làm dần từ F2 |
| 4 | Đổi secret key Clerk trước khi lên production (khóa dev đã từng được gửi qua chat); production dùng instance riêng | Trước khi triển khai |
| 5 | Gửi email kết quả tự động (hiện chỉ ghi nhận trạng thái "đã gửi") | Chọn dịch vụ gửi mail của lab |
| 6 | Triển khai lên máy chủ lab, cấu hình HTTPS và webhook Clerk production | Máy chủ và tên miền |

## 6. Quy tắc nghiệp vụ cần lab xác nhận

Các quy tắc dưới đây đã được cài đặt theo đề xuất. Quy tắc nào cần đổi thì chỉ phải sửa code, không phải sửa dữ liệu.

1. Trọng số 40% điểm chung và 60% điểm chuyên môn, điểm sàn 7,0, SLA duyệt CV 36 giờ, slot 15 phút. Các giá trị này đều sửa được trên giao diện cài đặt.
2. Mỗi ứng viên chỉ được dời lịch một lần (PV2).
3. Mọi tài khoản Giảng viên đều tự động có tên trong hội đồng của mọi ca.
4. Lịch sai khung giờ hay hội đồng khác 6 người chỉ bị cảnh báo, không bị chặn.
5. Hai ứng viên bằng điểm tổng thì ai cao điểm chuyên môn hơn xếp trên, sau đó mới xét điểm chung.
6. Kết luận chỉ chốt được khi cả hội đồng đã nộp phiếu.
7. Ngạch đã đủ chỉ tiêu thì không nhận thêm Trúng tuyển; muốn nhận thêm thì tăng chỉ tiêu.

## 7. Hạn chế đã biết

- Excel tự bỏ số 0 ở đầu SĐT trong các cột định dạng số của file cũ, và hệ thống không khôi phục được. File mẫu mới đã định dạng cột này là chữ.
- Tìm không dấu dùng collation `Latin1_General_100_CI_AI`, nên tên được sắp xếp theo bảng chữ Latin chứ không theo thứ tự chữ cái tiếng Việt.
- Máy chủ cần ra được Internet để xác thực token qua Clerk.
