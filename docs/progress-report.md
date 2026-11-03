# Báo cáo tiến độ — Hệ thống Tuyển dụng DX Lab

*Cập nhật: 03/11/2026*

## 1. Tóm tắt

Hệ thống thay bộ file Word và Excel đang dùng để tuyển thực tập sinh của DX Lab: hồ sơ và sàng lọc CV, lịch phỏng vấn, chấm điểm hội đồng, kết quả và phê duyệt.

| Hạng mục | Trạng thái |
|---|---|
| Phân tích nghiệp vụ, thiết kế luồng, so sánh với dự án `labintern` | Xong |
| Kế hoạch backend và cấu trúc dự án | Xong ([`backend-plan.md`](backend-plan.md), [`project-structure.md`](project-structure.md)) |
| **Backend, mốc B0–B9** | **Xong**: 53 endpoint, 98 test tự động đều pass |
| Đóng gói Docker, sao lưu, hướng dẫn vận hành | Xong, đã chạy thử toàn bộ stack bằng `docker compose` |
| Frontend React | Chưa bắt đầu |
| Đăng nhập Clerk thật từ đầu đến cuối | Chờ khóa Clerk (mục 5) |

## 2. Công nghệ

Spring Boot 4 · Java 21 · SQL Server 2022 · Flyway · Spring Security (OAuth2 Resource Server) và Clerk · Apache POI (Excel) · Testcontainers · Docker Compose.

## 3. Các mốc đã hoàn thành

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

Danh sách endpoint, quyền và mã lỗi: [`api.md`](api.md).

## 4. Chất lượng

- **98 test tự động**, đều pass: 19 unit test (công thức điểm, khung giờ, SLA, chữ ký webhook, tìm không dấu…) và 79 test tích hợp gọi API qua toàn bộ lớp bảo mật, trên SQL Server 2022 thật.
- **Quy mô mã nguồn**: khoảng 6.400 dòng mã chính (129 file) và 2.700 dòng test (22 file).
- **Bảo mật**:
  - Mọi quyền được kiểm tra ở cả controller lẫn service.
  - Bí mật chỉ nằm trong `.env`, không commit.
  - Tài khoản DB của ứng dụng không có quyền `db_owner`.
  - Container ứng dụng không chạy bằng root.
  - Cổng DB không mở ra mạng ngoài.
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
| 1 | Frontend React gồm đăng nhập Clerk và các màn hình hồ sơ, lịch, phiếu chấm, kết quả, dashboard | Không |
| 2 | Thử đăng nhập Clerk thật từ đầu đến cuối | Cần điền `CLERK_ISSUER`, `CLERK_SECRET_KEY`, `VITE_CLERK_PUBLISHABLE_KEY` vào `.env`, và thêm claim `email` vào session token |
| 3 | Gửi email kết quả tự động (hiện chỉ ghi nhận trạng thái "đã gửi") | Chọn dịch vụ gửi mail của lab |
| 4 | Triển khai lên máy chủ lab, cấu hình HTTPS và webhook Clerk production | Máy chủ và tên miền |

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
