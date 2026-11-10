# Báo cáo tổng hợp nghiệp vụ hiện có

**Dự án:** Hệ thống Tuyển dụng Thực tập sinh DX Lab  
**Ngày cập nhật:** 04/11/2026  
**Phạm vi:** Các nghiệp vụ đã được triển khai trong backend hiện tại

## 1. Mục đích và phạm vi

Tài liệu này mô tả các nghiệp vụ backend đang thực sự hỗ trợ, bao gồm tác nhân, điều kiện, các bước xử lý, dữ liệu thay đổi, phân quyền, trạng thái và ngoại lệ.

Hệ thống hiện gồm hai nhóm nghiệp vụ:

1. Lưu lịch sử các lần Lab gửi chỉ tiêu và JD tuyển dụng cho nhà trường.
2. Vận hành tuyển dụng nội bộ sau khi Lab có danh sách ứng viên: hồ sơ, sàng lọc, lịch, chấm điểm, kết quả và offer.

Các chức năng chưa có trong backend:

- Campaign/đợt tuyển dụng đầy đủ theo học kỳ.
- Tài khoản và cổng đăng ký cho ứng viên.
- Đăng hoặc chỉnh sửa bài tuyển dụng trên hệ thống của trường.
- Tự gửi email mời phỏng vấn hoặc email kết quả.
- Task Tracker, Incident Log, ngân hàng câu hỏi và bài test.
- Frontend người dùng.

## 2. Vai trò và phạm vi quyền

| Vai trò | Mã | Phạm vi chính |
|---|---|---|
| Giảng viên | `LECTURER` | Xem toàn bộ nghiệp vụ, tham gia chấm và phê duyệt kết quả |
| Người tổ chức | `ORGANIZER` | Quản lý tài khoản, hồ sơ, lịch, kết luận, offer và lịch sử gửi JD |
| Lead chuyên môn | `LEAD` | Quản lý ngạch được giao, sàng lọc, sửa tiêu chí/JD đúng ngạch và xem kết quả ngạch |
| Thành viên hội đồng | `MEMBER` | Xem ca được phân công, điểm danh, ghi log và chấm ứng viên trong ca |

Một tài khoản hiện chỉ có một vai trò. Quyền chi tiết còn phụ thuộc dữ liệu, ví dụ Lead chỉ xử lý đúng ngạch và người chấm phải thuộc hội đồng của đúng ca.

## 3. Bản đồ luồng tổng thể

```text
Người tổ chức/Lead chuẩn bị chỉ tiêu và JD
                    ↓
Lưu bản nháp lần gửi tuyển dụng
                    ↓
Người tổ chức đánh dấu đã gửi cho trường
                    ↓
Lưu snapshot bất biến để tra cứu về sau

Nhận danh sách ứng viên từ nguồn bên ngoài
                    ↓
Nhập hồ sơ thủ công hoặc Excel
                    ↓
Lead/Người tổ chức sàng lọc CV
                    ↓
Người tổ chức tạo ca và xếp lịch
                    ↓
Hội đồng điểm danh và phỏng vấn
                    ↓
Từng giám khảo chấm độc lập
                    ↓
Hệ thống tính điểm và xếp hạng
                    ↓
Người tổ chức/Giảng viên chốt kết luận
                    ↓
Giảng viên phê duyệt
                    ↓
Người tổ chức ghi nhận email và offer
                    ↓
Gọi dự bị khi phát sinh chỗ trống
```

---

## 4. Lịch sử gửi nhu cầu tuyển dụng/JD cho trường

### 4.1. Mục tiêu

Lưu lại chính xác Lab đã gửi cho nhà trường những vị trí nào, chỉ tiêu bao nhiêu và nội dung/file JD nào. Module này chỉ ghi nhận lịch sử nội bộ; không thao tác lên hệ thống của trường.

### 4.2. Trạng thái

```text
DRAFT → SENT
```

- `DRAFT`: được phép sửa, thêm/xóa vị trí và thay file.
- `SENT`: snapshot bất biến, không được sửa hoặc xóa.
- Nếu cần điều chỉnh bản đã gửi, tạo bản mới và liên kết `replacesSubmissionId`.

### 4.3. Tạo bản nháp

**Tác nhân:** Người tổ chức.  
**API:** `POST /api/recruitment-submissions`.

Các bước:

1. Nhập mã kỳ, ví dụ `FA26`.
2. Nhập tiêu đề lần gửi.
3. Có thể nhập trước người nhận, mã tham chiếu và ghi chú.
4. Nếu là bản bổ sung, chọn một bản `SENT` cần thay thế.
5. Backend chuẩn hóa mã kỳ thành chữ hoa.
6. Tạo bản ghi ở trạng thái `DRAFT` và lưu audit người tạo.

Quy tắc:

- Mã kỳ chỉ gồm chữ, số, `_` hoặc `-`.
- Chỉ bản `SENT` mới được chọn làm bản bị thay thế.
- Version được trả về để chống hai người ghi đè nhau.

### 4.4. Thêm vị trí và JD

**Tác nhân:** Người tổ chức hoặc Lead đúng ngạch.  
**API:** `POST /api/recruitment-submissions/{id}/items`.

Mỗi vị trí gồm:

- Ngạch.
- Tên vị trí.
- Chỉ tiêu từ 1–1000.
- Nội dung JD dạng text, hoặc file JD.
- Ghi chú và thứ tự hiển thị.
- Snapshot mã/tên ngạch.

Lead chỉ được thêm hoặc sửa item thuộc ngạch mình đang phụ trách. Người tổ chức được thao tác mọi ngạch.

### 4.5. Upload file JD

**API:** `POST /api/recruitment-submission-items/{itemId}/file?version=`.

Quy trình an toàn:

1. Kiểm tra bản cha còn `DRAFT`.
2. Kiểm tra quyền theo ngạch.
3. Kiểm tra version item.
4. Giới hạn file tối đa 5 MB.
5. Chỉ nhận PDF hoặc DOCX.
6. Kiểm tra magic bytes; DOCX phải có cấu trúc OpenXML cơ bản.
7. Loại bỏ path và ký tự điều khiển khỏi tên file.
8. Tính SHA-256.
9. Lưu binary trong SQL Server cùng metadata.

Khi tải, file luôn được trả dạng attachment, kèm `nosniff`, `no-store` và checksum; backend không render file trực tiếp trên trình duyệt.

### 4.6. Đánh dấu đã gửi

**Tác nhân:** Người tổ chức.  
**API:** `POST /api/recruitment-submissions/{id}/mark-sent`.

Điều kiện:

- Bản ghi còn `DRAFT`.
- Version đúng.
- Có ít nhất một vị trí.
- Mỗi vị trí có nội dung hoặc file JD.
- Có ngày gửi và người/đơn vị nhận.
- Ngày gửi không nằm trong tương lai.

Xử lý:

1. Khóa bản ghi để tránh hai người cùng gửi.
2. Chốt snapshot mã và tên ngạch hiện tại.
3. Lưu người thực hiện và thời điểm gửi.
4. Chuyển trạng thái sang `SENT`.
5. Ghi lịch sử `DRAFT → SENT`.
6. Mọi API sửa/xóa sau đó trả `SUBMISSION_LOCKED`.

### 4.7. Tra cứu lịch sử

**Quyền:** Người tổ chức, Giảng viên, Lead. Thành viên không được xem.  
**API:** `GET /api/recruitment-submissions` và `GET /api/recruitment-submissions/{id}`.

Bộ lọc:

- Mã kỳ.
- Ngạch.
- Trạng thái.
- Khoảng thời gian gửi.
- Từ khóa theo tiêu đề, người nhận, mã tham chiếu, vị trí hoặc ngạch.

Kết quả tóm tắt trả số vị trí và tổng chỉ tiêu; chi tiết trả toàn bộ item và metadata file.

---

## 5. Xác thực và liên kết tài khoản Clerk

### 5.1. Đăng nhập

1. Người dùng đăng nhập trên Clerk.
2. Frontend nhận JWT và gửi Bearer token tới backend.
3. Backend xác minh chữ ký RS256, issuer, thời hạn và authorized party (`azp`).
4. Lấy Clerk user ID từ claim `sub`.
5. Tìm tài khoản nội bộ đã liên kết.
6. Nếu là lần đầu, tìm tài khoản được tạo trước theo email và gắn Clerk user ID.
7. Tạo principal chứa ID và vai trò nội bộ.

Clerk xác định danh tính; backend quyết định quyền nghiệp vụ.

### 5.2. Từ chối truy cập

- Token thiếu/sai/hết hạn: `401 UNAUTHORIZED`.
- Đăng nhập Clerk thành công nhưng chưa có tài khoản nội bộ: `403 FORBIDDEN`.
- Tài khoản bị khóa: `403 FORBIDDEN`.
- Email đã gắn với Clerk user khác: không được chiếm lại.

### 5.3. Webhook

Clerk gọi `POST /api/webhooks/clerk`. Backend xác minh chữ ký Svix, timestamp và chống xử lý lặp bằng `svix-id`.

- `user.created`, `user.updated`: đồng bộ email chính.
- `user.deleted`: khóa tài khoản nội bộ.
- Sau cập nhật, cache principal được xóa.

---

## 6. Quản lý tài khoản nội bộ

### 6.1. Khởi tạo Organizer đầu tiên

Khi hệ thống mới chưa có Organizer, biến `BOOTSTRAP_ORGANIZER_EMAIL` tạo hoặc nâng cấp tài khoản tương ứng thành Organizer. Sau khi hoàn thành nên xóa biến này khỏi môi trường.

### 6.2. Tạo tài khoản và gửi lời mời

**API:** `POST /api/users`.  
**Quyền:** Organizer.

1. Chuẩn hóa email chữ thường.
2. Kiểm tra email chưa tồn tại.
3. Tạo tài khoản nội bộ và vai trò.
4. Nếu yêu cầu, gọi Clerk gửi lời mời.
5. Nếu Clerk lỗi, transaction rollback, không để lại tài khoản rác.

### 6.3. Sửa vai trò hoặc khóa

**API:** `PUT /api/users/{id}`.

Quy tắc:

- Không được tự hạ quyền hoặc tự khóa mình.
- Phải còn ít nhất một Organizer hoạt động.
- Tài khoản đang làm Lead không được khóa/hạ vai trò trước khi đổi Lead.
- Cache danh tính bị xóa để thay đổi có hiệu lực ở request tiếp theo.

### 6.4. Gửi lại lời mời

Chỉ gửi lại cho tài khoản hoạt động chưa liên kết Clerk. Tài khoản đã liên kết trả `ALREADY_LINKED`.

---

## 7. Quản lý ngạch, Lead, tiêu chí và tham số

### 7.1. Ngạch tuyển dụng

Hệ thống seed sáu ngạch: AI, Graphic Design, Software Engineer, Game Developer, Information Assurance và Game Design.

Organizer có thể sửa tên, quota, phạm vi slot, yêu cầu portfolio và Lead. Lead phải là tài khoản hoạt động có vai trò `LEAD` hoặc `ORGANIZER`.

### 7.2. Tiêu chí chấm

- Tiêu chí chung: chỉ Organizer sửa.
- Tiêu chí chuyên môn: Organizer hoặc Lead đúng ngạch.
- Mọi vai trò được đọc tiêu chí phục vụ phiếu chấm.
- Tiêu chí chưa có điểm được xóa vật lý.
- Tiêu chí đã có điểm chỉ được đặt `active=false` để không mất dữ liệu gốc.

### 7.3. Tham số

Organizer hoặc Lecturer sửa:

- Trọng số điểm chung/chuyên môn.
- Điểm sàn.
- SLA sàng lọc.
- Độ dài slot.

Hai trọng số phải cộng bằng 1. Điểm kết quả được tính động theo cấu hình hiện tại.

---

## 8. Tiếp nhận hồ sơ ứng viên

### 8.1. Tạo thủ công

**API:** `POST /api/candidates`.  
**Quyền:** Organizer.

Hệ thống chuẩn hóa Mã SV thành chữ hoa, kiểm tra trùng, gắn ngạch và tạo trạng thái `PENDING`. Thời điểm tạo cũng là mốc SLA được giao sàng lọc.

### 8.2. Import Excel

**API:** `POST /api/candidates/import?dryRun=`.

1. Tìm header trong 15 dòng đầu.
2. Nhận nhiều alias cột và tên ngạch cũ.
3. Lấy URL thật từ hyperlink CV/Portfolio.
4. Kiểm tra tối đa 1.000 dòng, dữ liệu bắt buộc, độ dài, email và ngạch.
5. Thu thập tất cả lỗi theo số dòng.
6. Có một lỗi thì không lưu dòng nào.
7. `dryRun=true` chỉ kiểm tra.
8. Mã SV mới tạo hồ sơ; Mã SV cũ cập nhật thông tin nhưng giữ kết quả sàng lọc nếu không đổi ngạch.

Đổi ngạch của hồ sơ đã có lịch bị từ chối.

### 8.3. File mẫu

Backend sinh file Excel có dropdown ngạch, hướng dẫn và định dạng text cho Mã SV/SĐT.

### 8.4. Sửa và xóa

- Đổi ngạch đưa hồ sơ về `PENDING` và tính lại SLA.
- Hồ sơ đã có lịch không được đổi ngạch.
- Hồ sơ có lịch hoặc điểm không được xóa.

---

## 9. Sàng lọc CV

### 9.1. Trạng thái

```text
PENDING → PASSED
        → REJECTED
```

### 9.2. Luồng xử lý

**Tác nhân:** Organizer hoặc Lead đúng ngạch.  
**API:** `PUT /api/candidates/{id}/screening`.

1. Mở hồ sơ và lấy version.
2. Nhập điểm năm mục CV.
3. Chọn trạng thái.
4. Nếu `REJECTED`, bắt buộc lý do.
5. Backend kiểm tra quyền ngạch và version.
6. Ghi người/thời điểm duyệt và lịch sử.

Hồ sơ đã có lịch active không được đổi khỏi `PASSED`. Hồ sơ `PENDING` quá `slaHours` được đánh dấu quá hạn.

### 9.3. Tra cứu

Organizer/Lecturer xem tất cả; Lead chỉ xem ngạch mình. Tìm theo tên, Mã SV, email không phân biệt dấu. Hồ sơ được gắn cờ thiếu/sai CV hoặc Portfolio.

---

## 10. Tạo ca và quản lý hội đồng

### 10.1. Tạo ca

**API:** `POST /api/sessions`.  
**Quyền:** Organizer.

Hệ thống tự thêm:

- Tất cả Lecturer hoạt động.
- Organizer tạo ca.
- Lead ngạch.
- Thành viên luân chuyển được chọn.

### 10.2. Cảnh báo

Các cảnh báo không chặn thao tác:

- Ngoài khung 10h–12h30 hoặc 16h–18h30.
- Hội đồng khác sáu người.
- Thiếu Lead.
- Thành viên trùng ca.
- Tổng slot ngạch vượt kế hoạch.

### 10.3. Sửa/xóa ca

- Giờ mới phải chứa toàn bộ slot active.
- Ca có slot không được đổi ngạch.
- Ca có bất kỳ slot nào không được xóa.

### 10.4. Sửa hội đồng

Organizer thay thành viên luân chuyển. Người đã chấm trong ca không được loại khỏi hội đồng. Lead hiện tại được bổ sung lại nếu thiếu.

### 10.5. Phạm vi xem

- Organizer/Lecturer: mọi ca.
- Lead: ca ngạch mình hoặc ca mình tham gia.
- Member: ca mình tham gia.

---

## 11. Xếp, dời và hủy lịch

### 11.1. Xếp thủ công

Ứng viên phải `PASSED`, đúng ngạch và chưa có lịch active. Nếu không truyền giờ, hệ thống chọn chỗ trống sớm nhất; nếu có giờ, kiểm tra nằm trong ca và không trùng.

### 11.2. Tự động xếp

Nếu không chọn ứng viên, hệ thống lấy hồ sơ `PASSED` chưa có lịch, sắp theo thời điểm duyệt rồi Mã SV. Các hồ sơ không còn chỗ được trả trong `notScheduled`.

### 11.3. Dời PV1 sang PV2

```text
PV1 active → PV1 inactive → PV2 active
```

- Chỉ Organizer thực hiện.
- Không dời ứng viên đã `ATTENDED`.
- Chỉ được dời một lần.
- Ca mới phải cùng ngạch.
- Lưu lý do, lịch cũ và liên kết `replacedBy`.

### 11.4. Hủy

- Chỉ hủy slot active còn `PENDING`.
- Ứng viên có điểm không được hủy.
- Hủy PV2 sẽ kích hoạt lại PV1.

Database bảo đảm mỗi ứng viên chỉ có một slot active.

---

## 12. Vận hành buổi phỏng vấn

### 12.1. Điểm danh

Organizer hoặc thành viên hội đồng cập nhật `PENDING`, `ATTENDED`, `ABSENT` và ghi chú. Khi đã có điểm, không được đổi khỏi `ATTENDED`.

### 12.2. Interview Log

Organizer hoặc hội đồng ghi nhận xét chung của ca. Chuỗi rỗng được chuẩn hóa thành `null`.

### 12.3. Xuất lịch

Organizer/Lecturer xuất Excel theo ngạch và khoảng ngày, gồm ngày, giờ, ứng viên, PV1/PV2, lịch sử đổi lịch, điểm danh và hội đồng.

---

## 13. Chấm điểm hội đồng

### 13.1. Điều kiện được chấm

Người dùng chỉ được chấm khi:

1. Ứng viên có slot active.
2. Điểm danh `ATTENDED`.
3. Người dùng thuộc hội đồng của ca.

### 13.2. Phiếu nháp

Phiếu lấy tiêu chí chung và chuyên môn đang active. Nháp được thiếu điểm; giá trị `null` xóa điểm đã nhập. Mỗi lần lưu tăng version.

### 13.3. Nộp phiếu

Phiếu nộp phải đủ mọi tiêu chí và có recommendation `PASS` hoặc `FAIL`. Phiếu đã nộp vẫn được sửa nhưng luôn phải đầy đủ, cho tới khi kết luận được phê duyệt.

### 13.4. Chấm độc lập

Thành viên hội đồng phải nộp phiếu của mình trước khi xem điểm người khác. Phiếu nháp của người khác không bao giờ được trả ra.

### 13.5. Tiến độ

Theo từng ứng viên, hệ thống trả số phiếu đã nộp, số nháp và danh sách người chưa nộp.

---

## 14. Tính điểm, xếp hạng và đề xuất

Chỉ phiếu đã nộp được tính:

```text
Điểm tiêu chí = trung bình điểm tiêu chí của các phiếu đã nộp
Điểm nhóm = trung bình các tiêu chí trong nhóm
Tổng = trọng số chung × điểm chung + trọng số chuyên môn × điểm chuyên môn
```

Chỉ làm tròn hai chữ số ở kết quả cuối.

Xếp hạng chỉ gồm ứng viên `ATTENDED` có điểm tổng:

1. Tổng giảm dần.
2. Điểm chuyên môn giảm dần.
3. Điểm chung giảm dần.
4. Mã SV tăng dần.

Đề xuất:

- Vắng hoặc dưới điểm sàn: `REJECTED`.
- Đủ sàn và trong quota: `HIRED`.
- Đủ sàn nhưng ngoài quota: `WAITLIST`.

---

## 15. Kết luận và phê duyệt

### 15.1. Chốt kết luận

**Tác nhân:** Organizer hoặc Lecturer.

Trạng thái:

```text
PENDING → HIRED
        → WAITLIST
        → TRANSFER
        → REJECTED
```

Quy tắc:

- `HIRED`, `WAITLIST`, `TRANSFER` yêu cầu toàn hội đồng nộp đủ phiếu.
- `TRANSFER` phải chọn ngạch khác.
- Kết luận khác đề xuất phải ghi lý do.
- Kết luận giữ chỗ phải còn quota.
- Mỗi lần sửa kiểm tra version.

### 15.2. Phê duyệt

Chỉ Lecturer được duyệt từng hồ sơ hoặc toàn ngạch. Sau duyệt:

- Lưu người và thời điểm duyệt.
- Khóa kết luận.
- Khóa phiếu chấm.

### 15.3. Mở lại

Lecturer được mở lại trước khi email kết quả được đánh dấu `SENT`. Đã gửi email thì trả `DECISION_ALREADY_SENT`.

---

## 16. Offer và gọi dự bị

### 16.1. Ghi nhận offer

Organizer cập nhật:

- `EmailStatus`: `NOT_SENT`, `SENT`.
- `OfferStatus`: `NONE`, `ACCEPTED`, `DECLINED`.

Chỉ kết luận `HIRED` hoặc `TRANSFER` đã duyệt mới được nhận offer. Muốn ghi nhận chấp nhận/từ chối thì email phải `SENT`.

Backend hiện chỉ ghi trạng thái; chưa tự gửi email.

### 16.2. Gọi dự bị

Organizer hoặc Lecturer gọi ứng viên `WAITLIST` đã duyệt khi còn chỗ. Phải gọi đúng người có thứ hạng cao nhất; sau đó reset trạng thái email/offer.

Người từ chối offer không còn chiếm quota.

---

## 17. Dashboard, lịch sử và lỗi

### 17.1. Dashboard

Trả thống kê theo ngạch người dùng được xem và công việc cá nhân:

- Phiếu cần chấm.
- Hồ sơ cần sàng lọc.
- Kết luận chờ phê duyệt.
- Chỉ tiêu, hồ sơ, phỏng vấn và kết quả.

### 17.2. Audit

Các thay đổi quan trọng được lưu: vai trò, Lead, trạng thái hồ sơ, ngạch, lịch, điểm danh, hội đồng, kết luận, phê duyệt, offer và trạng thái lần gửi JD.

Mọi entity nghiệp vụ có `created_by`, `updated_by`, `created_at`, `updated_at` khi phù hợp.

### 17.3. Kiểm soát đồng thời

- `@Version` cho hồ sơ, phiếu, kết luận và lịch sử gửi JD.
- Phiếu dùng update có điều kiện để tăng version nguyên tử.
- Ca dùng khóa bi quan khi xếp lịch.
- Bản gửi JD dùng khóa bi quan khi chuyển `SENT`.
- Constraint database xử lý xung đột cuối cùng.

Lỗi có cấu trúc:

```json
{
  "code": "STALE_DATA",
  "message": "Dữ liệu vừa được người khác cập nhật",
  "fieldErrors": []
}
```

---

## 18. Vận hành và bảo vệ dữ liệu

### 18.1. Database

- Flyway là nguồn duy nhất quản lý schema.
- Hibernate chỉ `validate`.
- Tài khoản ứng dụng có quyền DDL/data cần thiết nhưng không phải `db_owner`.
- SQL Server dùng kết nối mã hóa.

### 18.2. Backup

Script tạo full backup `.bak`, dùng checksum và `RESTORE VERIFYONLY`. Mặc định giữ 30 ngày; cần sao chép thêm ra NAS hoặc nơi lưu trữ độc lập.

File JD lưu trong database nên đi cùng backup. Backup có thể chứa dữ liệu cá nhân và tài liệu nội bộ, phải giới hạn quyền truy cập.

### 18.3. Restore

Restore yêu cầu gõ đúng tên database để xác nhận, dừng backend, phục hồi database, nối lại login và khởi động ứng dụng.

---

## 19. Ma trận nghiệp vụ và API

| Nghiệp vụ | API chính | Tác nhân ghi |
|---|---|---|
| Lịch sử gửi JD | `/api/recruitment-submissions` | Organizer; Lead sửa JD đúng ngạch |
| Tài khoản | `/api/users`, `/api/me` | Organizer |
| Ngạch/tiêu chí | `/api/tracks`, `/api/criteria`, `/api/settings` | Organizer, Lead/Lecturer theo chức năng |
| Hồ sơ | `/api/candidates` | Organizer |
| Sàng lọc | `/api/candidates/{id}/screening` | Organizer, Lead đúng ngạch |
| Ca/hội đồng | `/api/sessions` | Organizer; hội đồng ghi log |
| Slot/điểm danh | `/api/slots` | Organizer; hội đồng điểm danh |
| Chấm điểm | `/api/candidates/{id}/scoresheet` | Thành viên hội đồng hợp lệ |
| Kết quả | `/api/results`, `/api/candidates/{id}/decision` | Organizer, Lecturer |
| Phê duyệt | `/decision/approve`, `/results/{trackId}/approve` | Lecturer |
| Offer/dự bị | `/decision/offer`, `/decision/promote` | Organizer; Lecturer được gọi dự bị |
| Dashboard/export | `/api/dashboard`, các endpoint `/export` | Theo quyền đọc |

## 20. Trạng thái triển khai và giới hạn hiện tại

### Đã có

- 53 endpoint cũ cho tài khoản đến offer.
- Module lịch sử gửi JD mới với API, migration và kiểm thử tích hợp.
- Docker, backup/restore, Clerk và Testcontainers.

### Chưa có

- Frontend.
- Gửi email nghiệp vụ thật.
- Campaign/Application tách theo nhiều học kỳ.
- Tích hợp hoặc đồng bộ với hệ thống trường.
- Antivirus cho file JD; hiện backend kiểm tra loại, cấu trúc, kích thước và buộc tải dạng attachment.

### Xác minh gần nhất

- Production code và test code biên dịch thành công.
- Maven package thành công.
- 18 unit test không cần Docker pass.
- Integration test mới đã biên dịch nhưng chưa chạy do Docker Desktop chưa hoạt động.

## 21. Kết luận

Backend hiện hỗ trợ một chuỗi tuyển dụng nội bộ hoàn chỉnh từ tiếp nhận hồ sơ đến offer, cộng thêm khả năng lưu snapshot các lần gửi chỉ tiêu/JD cho nhà trường. Phần gửi JD độc lập với hồ sơ ứng viên và không can thiệp vào hệ thống của trường. Những mở rộng lớn tiếp theo, nếu cần vận hành nhiều học kỳ, là mô hình Campaign/Application và frontend cho các luồng đã có.
