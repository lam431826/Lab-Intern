# API — Hệ thống Tuyển dụng DX Lab

Mọi endpoint dưới `/api` (trừ webhook) cần header `Authorization: Bearer <session token Clerk>`.
Lỗi luôn có dạng `{ "code": "...", "message": "...", "fieldErrors": [...] }`.

| Mã HTTP | Ý nghĩa |
|---|---|
| 401 `UNAUTHORIZED` | Thiếu token, token sai chữ ký, hết hạn, sai issuer hoặc azp |
| 403 `FORBIDDEN` | Đăng nhập Clerk được nhưng chưa có tài khoản / bị khóa / không đủ quyền |
| 409 | Xung đột: trùng dữ liệu (`USER_EMAIL_EXISTS`) hoặc dữ liệu vừa bị người khác sửa (`STALE_DATA`) |
| 422 | Dữ liệu không hợp lệ (`VALIDATION_FAILED`) hoặc vi phạm quy tắc nghiệp vụ (mã riêng) |

## Tài khoản (B3–B4)

| Phương thức | Đường dẫn | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/api/me` | mọi vai trò | Người đang đăng nhập + mã các ngạch đang làm Lead |
| GET | `/api/users` | ORGANIZER, LECTURER | |
| POST | `/api/users` | ORGANIZER | `{email, fullName, role, sendInvite?}`; gửi lời mời Clerk, lỗi gửi thì không tạo |
| PUT | `/api/users/{id}` | ORGANIZER | `{fullName, role, active}`; có hiệu lực ngay ở request kế tiếp |
| POST | `/api/users/{id}/invite` | ORGANIZER | Gửi lại lời mời cho tài khoản chưa đăng nhập lần nào |

Mã lỗi nghiệp vụ: `USER_EMAIL_EXISTS`, `CANNOT_CHANGE_SELF`, `LAST_ORGANIZER`, `USER_IS_TRACK_LEAD`, `ALREADY_LINKED`, `USER_INACTIVE`, `CLERK_INVITE_FAILED`, `CLERK_NOT_CONFIGURED`.

## Ngạch, tiêu chí, tham số chấm (B4)

| Phương thức | Đường dẫn | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/api/tracks` | mọi vai trò | 6 ngạch theo thứ tự hiển thị |
| PUT | `/api/tracks/{id}` | ORGANIZER | `{name, quota, slotMin, slotMax, requiresPortfolio, leadUserId}`; Lead phải có vai trò LEAD hoặc ORGANIZER |
| GET | `/api/criteria?trackId=` | mọi vai trò | Có `trackId`: 4 tiêu chí chung + tiêu chí chuyên môn của ngạch; không có: tiêu chí chung |
| POST | `/api/criteria` | ORGANIZER; LEAD của ngạch | `trackId = null` là tiêu chí chung (chỉ ORGANIZER) |
| PUT | `/api/criteria/{id}` | như trên | |
| DELETE | `/api/criteria/{id}` | như trên | Đã có điểm thì ẩn (`active = false`), chưa có thì xóa |
| GET | `/api/settings` | mọi vai trò | |
| PUT | `/api/settings` | ORGANIZER, LECTURER | `{weightGeneral, weightProfessional, passThreshold, slaHours, slotMinutes}`; hai trọng số cộng bằng 1 |

Mã lỗi nghiệp vụ: `INVALID_SLOT_RANGE`, `INVALID_TRACK_LEAD`, `INVALID_WEIGHTS`.

## Hồ sơ và sàng lọc CV (B5)

| Phương thức | Đường dẫn | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/api/candidates?trackId=&status=&q=&overdue=` | ORGANIZER, LECTURER, LEAD | Lead chỉ nhận ngạch mình; `trackId` ngạch khác → 403. `q` tìm theo tên/Mã SV/email, không phân biệt dấu |
| GET | `/api/candidates/summary` | ORGANIZER, LECTURER, LEAD | Đếm Tổng / Chờ duyệt / Đạt / Loại / Quá hạn theo ngạch |
| GET | `/api/candidates/{id}` | xem được hồ sơ (`@authz.canSeeCandidate`) | Kèm `flags`, `cvAverage`, `slaDeadline`, `overdue`, `scheduled`, `version` |
| GET | `/api/candidates/{id}/history` | như trên | Lịch sử đổi trạng thái, ngạch |
| POST | `/api/candidates` | ORGANIZER | Mã SV tự viết hoa, trùng → 409 `CANDIDATE_CODE_EXISTS` |
| PUT | `/api/candidates/{id}` | ORGANIZER | Đổi ngạch → hồ sơ về Chờ duyệt; đã xếp lịch thì không đổi ngạch được |
| DELETE | `/api/candidates/{id}` | ORGANIZER | Đã xếp lịch hoặc có điểm → 409 `CANDIDATE_IN_USE` |
| PUT | `/api/candidates/{id}/screening` | ORGANIZER; LEAD của ngạch | `{cvPersonalInfo, cvExperience, cvSkills, cvProjects, cvCertificates, status, reason, version}` |
| GET | `/api/candidates/import-template` | ORGANIZER | File .xlsx mẫu có dropdown ngạch |
| POST | `/api/candidates/import?dryRun=` | ORGANIZER | multipart `file`; trả `ImportReport` |

Cờ lọc nhanh (`flags`): `MISSING_CV`, `INVALID_CV_LINK`, `MISSING_PORTFOLIO` (Graphic Design, Game Design), `INVALID_PORTFOLIO_LINK`.

Mã lỗi nghiệp vụ: `CANDIDATE_CODE_EXISTS`, `CANDIDATE_SCHEDULED`, `CANDIDATE_IN_USE`, `REASON_REQUIRED`, `STALE_DATA`, `IMPORT_HEADER_NOT_FOUND`, `IMPORT_UNREADABLE`, `IMPORT_EMPTY`, `IMPORT_TOO_LARGE`.

**Nhập Excel:** tự tìm dòng tiêu đề trong 15 dòng đầu; nhận cột theo nhiều cách gọi (`Mã SV`/`MSSV`, `Họ và tên`/`Tên ứng viên`, `Ngạch`/`Vị trí`/`Khối`...) và ngạch theo mã, tên hoặc cách gọi trong file cũ (`Graphic Designers`, `SE Intern`...). Ô có hyperlink thì lấy địa chỉ link. Có lỗi ở bất kỳ dòng nào thì không lưu dòng nào; trùng Mã SV thì cập nhật thông tin, giữ nguyên kết quả sàng lọc.

## Lịch phỏng vấn (B6)

| Phương thức | Đường dẫn | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/api/sessions?trackId=&date=` | mọi vai trò | Giảng viên, Người tổ chức: mọi ca; Lead: ca ngạch mình + ca mình ngồi; Thành viên: ca mình ngồi |
| GET | `/api/sessions/{id}` | `@authz.canSeeSession` | Kèm hội đồng, mọi slot (kể cả slot đã dời) và `warnings` |
| POST | `/api/sessions` | ORGANIZER | `{trackId, sessionDate, startTime, endTime, room, rotatingMemberIds}`; tự thêm mọi Giảng viên, Người tổ chức tạo ca, Lead ngạch |
| PUT | `/api/sessions/{id}` | ORGANIZER | Giờ mới phải chứa hết slot đang hiệu lực; ca đã có slot thì không đổi ngạch |
| DELETE | `/api/sessions/{id}` | ORGANIZER | Ca đã có slot → 409 `SESSION_IN_USE` |
| PUT | `/api/sessions/{id}/panel` | ORGANIZER | `{rotatingMemberIds}`; thành viên đã chấm trong ca thì không bỏ ra được |
| PUT | `/api/sessions/{id}/log` | ORGANIZER; hội đồng của ca | `{logSummary}`: Interview Log |
| POST | `/api/sessions/{id}/slots` | ORGANIZER | `{candidateId, startTime?}`; không có giờ thì lấy chỗ trống sớm nhất |
| POST | `/api/sessions/{id}/auto-schedule` | ORGANIZER | `{candidateIds?}`; rỗng = mọi hồ sơ Đạt chưa có lịch, theo thứ tự duyệt CV; trả `scheduled`, `notScheduled` |
| POST | `/api/slots/{id}/reschedule` | ORGANIZER | `{sessionId, startTime?, reason}`; PV1 → PV2, slot cũ giữ lại (`active = false`, `replacedById`) |
| DELETE | `/api/slots/{id}` | ORGANIZER | Chỉ slot chưa điểm danh; hủy PV2 thì PV1 hiệu lực lại |
| PUT | `/api/slots/{id}/attendance` | ORGANIZER; hội đồng của ca | `{attendance: PENDING/ATTENDED/ABSENT, note}` |
| GET | `/api/candidates/{id}/slots` | xem được hồ sơ | Toàn bộ lịch của ứng viên, cả lịch đã dời |
| GET | `/api/sessions/export?trackId=&from=&to=` | ORGANIZER, LECTURER | File .xlsx: ngày, giờ ("10h - 10h15"), ứng viên, PV1/PV2, lịch cũ, điểm danh, hội đồng |

Độ dài slot lấy từ `slotMinutes` trong `/api/settings` (mặc định 15 phút).

Cảnh báo (`warnings`, không chặn thao tác): `OUTSIDE_HOURS` (ngoài 10h–12h30, 16h–18h30), `PANEL_SIZE` (khác 6 người), `LEAD_MISSING`, `PANEL_BUSY` (thành viên ngồi ca khác trùng giờ), `TRACK_SLOTS_OVER_MAX` (vượt số slot tối đa của ngạch).

Mã lỗi nghiệp vụ: `INVALID_SESSION_TIME`, `SESSION_IN_USE`, `SESSION_FULL`, `SLOT_OUTSIDE_SESSION`, `SLOT_OVERLAP`, `CANDIDATE_NOT_PASSED`, `CANDIDATE_ALREADY_SCHEDULED`, `TRACK_MISMATCH`, `SLOT_INACTIVE`, `SLOT_ATTENDED`, `RESCHEDULE_LIMIT`, `ATTENDANCE_LOCKED`, `PANEL_MEMBER_HAS_SCORES`, `USER_INACTIVE`, `DATA_CONFLICT`.

## Chấm điểm (B7)

| Phương thức | Đường dẫn | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/api/me/scoring` | mọi vai trò | Ứng viên đã có mặt trong các ca mình ngồi hội đồng; `sheetStatus` NONE / DRAFT / SUBMITTED |
| GET | `/api/candidates/{id}/scoresheet` | `@authz.canScore` | Phiếu của mình: tiêu chí chung + chuyên môn kèm thang chấm, điểm đã nhập, `version`, `editable` |
| PUT | `/api/candidates/{id}/scoresheet` | `@authz.canScore` | `{scores: [{criterionId, value}], comment, recommendation: PASS/FAIL, version, submit}`; `value = null` xóa ô điểm |
| GET | `/api/candidates/{id}/scores` | xem được hồ sơ | Điểm cả hội đồng, chỉ tính phiếu đã nộp; trung bình theo tiêu chí và theo khối |
| GET | `/api/sessions/{id}/progress` | `@authz.canSeeSession` | Theo từng ứng viên: số phiếu đã nộp, số phiếu nháp, tên người chưa nộp |

Quy tắc:
- Chấm được khi có tên trong hội đồng của ca và ứng viên đã điểm danh có mặt. Áp dụng cả với Giảng viên.
- Phiếu nháp được thiếu điểm. Nộp phiếu (`submit = true`) phải đủ điểm mọi tiêu chí và có đề xuất. Phiếu đã nộp vẫn sửa được nhưng luôn phải đầy đủ.
- Thành viên hội đồng chưa nộp phiếu của mình thì không xem được điểm người khác (`SUBMIT_FIRST`). Phiếu nháp của người khác không bao giờ hiển thị.
- Mỗi lần lưu tăng `version`. Gửi `version` cũ thì nhận 409 `STALE_DATA`, ví dụ khi mở cùng phiếu ở hai tab.
- Ứng viên đã có điểm thì không đổi điểm danh khỏi "có mặt" được, không dời và không hủy lịch được, và người đã chấm không bị bỏ khỏi hội đồng được.
- Kết luận đã được Giảng viên phê duyệt thì phiếu bị khóa (`SCORING_LOCKED`).

Mã lỗi nghiệp vụ: `INCOMPLETE_SHEET`, `INVALID_CRITERION`, `DUPLICATE_CRITERION`, `SUBMIT_FIRST`, `SCORING_LOCKED`, `STALE_DATA`.

## Kết quả, phê duyệt, offer (B8)

| Phương thức | Đường dẫn | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/api/dashboard` | mọi vai trò | Số liệu theo ngạch được xem + việc cần làm (phiếu cần chấm, hồ sơ chờ duyệt, kết luận chờ phê duyệt) |
| GET | `/api/results` | ORGANIZER, LECTURER, LEAD | Bảng kết quả các ngạch được xem (Lead: ngạch mình) |
| GET | `/api/results/{trackId}` | như trên | Điểm, hạng, gợi ý, kết luận; `seatsTaken`, `seatsLeft`, `nextWaitlistCandidateId` |
| GET | `/api/results/export?trackId=` | ORGANIZER, LECTURER | File .xlsx |
| PUT | `/api/candidates/{id}/decision` | ORGANIZER, LECTURER | `{finalStatus, transferTrackId, note, version}` |
| POST | `/api/candidates/{id}/decision/approve` | LECTURER | `{version}` |
| POST | `/api/results/{trackId}/approve` | LECTURER | Phê duyệt mọi kết luận đã chốt của ngạch |
| POST | `/api/candidates/{id}/decision/reopen` | LECTURER | `{version}`; đã gửi email thì không mở lại được |
| PUT | `/api/candidates/{id}/decision/offer` | ORGANIZER | `{emailStatus, offerStatus, version}` |
| POST | `/api/candidates/{id}/decision/promote` | ORGANIZER, LECTURER | `{version}`: gọi dự bị lên Trúng tuyển |

**Công thức** (`ScoreCalculator`, dùng chung cho phiếu điểm, bảng kết quả và file xuất):
- Điểm tiêu chí là trung bình điểm của các phiếu đã nộp.
- Điểm khối là trung bình các tiêu chí trong khối.
- Tổng = trọng số chung × điểm chung + trọng số chuyên môn × điểm chuyên môn.
- Chỉ làm tròn 2 chữ số ở bước cuối.
- Điểm không lưu sẵn: đổi trọng số hay điểm sàn thì bảng tính lại ngay.

**Xếp hạng và gợi ý:**
- Chỉ xếp hạng ứng viên đã có mặt và có điểm tổng. Hòa điểm thì ưu tiên điểm chuyên môn, rồi đến điểm chung.
- Đủ sàn và nằm trong chỉ tiêu: Trúng tuyển. Đủ sàn nhưng ngoài chỉ tiêu: Dự bị. Dưới sàn hoặc vắng mặt: Loại.

**Quy tắc kết luận:**
- Kết luận Trúng tuyển, Dự bị hay Chuyển ngạch chỉ được chốt khi hội đồng đã nộp đủ phiếu.
- Kết luận khác gợi ý thì phải ghi lý do.
- Ngạch đã đủ chỉ tiêu thì không nhận thêm (`QUOTA_FULL`). Chỗ trong chỉ tiêu tính cả người chuyển vào ngạch, và không tính người đã từ chối offer.
- Kết luận đã phê duyệt thì bị khóa, kèm theo khóa phiếu chấm.
- Phải gửi email trước khi ghi nhận ứng viên nhận hoặc từ chối offer.
- Gọi dự bị chỉ khi ngạch còn chỗ, và phải theo đúng thứ hạng.

Mã lỗi nghiệp vụ: `SCORES_INCOMPLETE`, `DECISION_NOTE_REQUIRED`, `INVALID_TRANSFER`, `QUOTA_FULL`, `DECISION_LOCKED`, `NOTHING_TO_APPROVE`, `DECISION_ALREADY_SENT`, `OFFER_NOT_ALLOWED`, `EMAIL_NOT_SENT`, `NOT_WAITLISTED`, `WAITLIST_ORDER`, `NOT_INTERVIEWABLE`, `STALE_DATA`.

## Webhook

| Phương thức | Đường dẫn | Xác thực |
|---|---|---|
| POST | `/api/webhooks/clerk` | Chữ ký Svix (`svix-id`, `svix-timestamp`, `svix-signature`); xử lý `user.created`, `user.updated`, `user.deleted` |
