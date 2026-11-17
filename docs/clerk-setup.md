# Cấu hình Clerk cho máy dev

Làm một lần, mất khoảng 10 phút. Xong bước này thì frontend (F1) và backend đăng nhập thật được.

Production làm tương tự trên một instance **Production** riêng, xem [`runbook.md`](runbook.md) §4. Tên menu trên Clerk Dashboard có thể khác đôi chút giữa các phiên bản giao diện; mỗi bước đều ghi rõ cần tìm cài đặt nào.

## Bước 1. Tạo ứng dụng

1. Vào https://dashboard.clerk.com, đăng nhập (dùng tài khoản Google cũng được).
2. **Create application**, đặt tên `DX Lab Tuyển dụng`.
3. Ở phần **Sign in options**, chỉ bật **Email** và **Google**; tắt Username, Phone và các mạng xã hội khác.
4. **Create application**. Clerk tạo sẵn một instance **Development**, dùng cho máy dev.

## Bước 2. Lấy khóa

Vào **Configure → API keys** (hoặc trang **API keys** ở menu trái):

| Trên Dashboard | Điền vào `.env` ở gốc repo | Ví dụ |
|---|---|---|
| **Publishable key** | `VITE_CLERK_PUBLISHABLE_KEY` | `pk_test_...` |
| **Secret key** (bấm hiện) | `CLERK_SECRET_KEY` | `sk_test_...` |
| **Frontend API URL** (trong mục *Show API URLs* hoặc *Advanced*) | `CLERK_ISSUER` | `https://<tên>.clerk.accounts.dev` |

- `CLERK_ISSUER` không có dấu `/` ở cuối. Để kiểm tra, mở `<CLERK_ISSUER>/.well-known/jwks.json` trên trình duyệt; trang phải trả về JSON có `keys`.
- **Secret key là bí mật**: chỉ để trong `.env` (đã nằm trong `.gitignore`), không gửi qua chat và không commit. Repo đang public.
- Publishable key thì công khai được, vì nó được nhúng vào frontend.

## Bước 3. Thêm claim `email` vào session token

Backend liên kết người đăng nhập với tài khoản tạo sẵn qua email, nên token phải mang email.

1. Vào **Configure → Sessions**, tìm mục **Customize session token**, bấm **Edit**.
2. Dán nội dung sau rồi **Save**:
   ```json
   {
     "email": "{{user.primary_email_address}}"
   }
   ```

Thiếu bước này thì đăng nhập Clerk vẫn thành công, nhưng hệ thống báo "Chưa được cấp quyền" (403).

## Bước 4. Chỉ cho người được mời đăng ký

1. Vào **Configure → Restrictions**, mục **Sign-up mode**, chọn **Restricted**.
2. Từ đó người lạ không tự đăng ký được nữa. Người tổ chức mời bằng chức năng Tài khoản của hệ thống; backend gọi Clerk Invitations API để gửi lời mời.

Lời mời đưa người dùng về `APP_FRONTEND_URL` (mặc định `http://localhost:5173`). Instance Development chấp nhận `localhost` sẵn, không cần khai báo thêm.

## Bước 5. Điền `.env` và tạo Người tổ chức đầu tiên

Mở file `.env` ở gốc repo (`recruitment-system/.env`) và điền các giá trị sau. Những dòng khác giữ nguyên.

```dotenv
CLERK_ISSUER=https://<tên>.clerk.accounts.dev
CLERK_SECRET_KEY=sk_test_...
VITE_CLERK_PUBLISHABLE_KEY=pk_test_...
CLERK_AZP=http://localhost:5173
APP_CORS_ORIGINS=http://localhost:5173
APP_FRONTEND_URL=http://localhost:5173

# Email bạn sẽ dùng để đăng nhập Clerk. Lần khởi động đầu, hệ thống tạo tài khoản Người tổ chức cho email này
BOOTSTRAP_ORGANIZER_EMAIL=<email của bạn>
```

`CLERK_WEBHOOK_SECRET` để trống ở máy dev. Webhook cần một URL công khai, mà không có webhook thì hệ thống vẫn chạy bình thường, vì tài khoản được liên kết ở lần đăng nhập đầu. Xem bước 7 nếu muốn thử.

## Bước 6. Kiểm tra

1. Chạy backend:
   ```
   cd backend
   mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```
   Log phải có hai dòng: `Đã tạo tài khoản Người tổ chức đầu tiên cho <email>` và `Đã gửi lời mời Clerk tới <email>`. Nếu thấy lỗi `Thiếu cấu hình CLERK_ISSUER`, kiểm tra lại bước 5.
   **Mở thư mời của Clerk và bấm link trong thư.** Ở chế độ Restricted (bước 4), Clerk chỉ cho người đã được mời đăng ký, nên nếu đăng nhập thẳng ở trang chủ thì sẽ nhận lỗi "New sign-ups are currently restricted". Không thấy thư thì vào Dashboard → Users → Invitations để mời tay.
2. Khi đã có frontend (F1): chạy `npm run dev` trong `frontend/`, mở http://localhost:5173 và đăng nhập Google bằng đúng email ở trên. Header phải hiện tên và vai trò **Người tổ chức**.
3. Chưa có frontend thì kiểm tra nhanh bằng cách:
   - Đăng nhập vào trang Account Portal của Clerk (`https://<tên>.accounts.dev`).
   - Mở DevTools, vào Console, chạy `await window.Clerk.session.getToken()` để lấy token.
   - Gọi API:
     ```
     curl -H "Authorization: Bearer <token>" http://localhost:8080/api/me
     ```
   - Kết quả phải là JSON có `"role":"ORGANIZER"`. Token chỉ sống khoảng 60 giây, nên chạy lệnh `curl` ngay sau khi lấy.

## Bước 7 (không bắt buộc). Webhook ở máy dev

Webhook đồng bộ email khi người dùng đổi email, và khóa tài khoản khi bị xóa trên Clerk.

1. Mở đường hầm ra cổng 8080, ví dụ `ngrok http 8080`, để có URL `https://xxxx.ngrok-free.app`.
2. Vào **Configure → Webhooks → Add endpoint**:
   - URL: `https://xxxx.ngrok-free.app/api/webhooks/clerk`
   - Sự kiện: `user.created`, `user.updated`, `user.deleted`
3. Chép **Signing secret** (`whsec_...`) vào `CLERK_WEBHOOK_SECRET`, rồi khởi động lại backend.
4. Bấm **Send test event**. Backend trả 204 là đúng.

## Lỗi thường gặp

| Hiện tượng | Nguyên nhân |
|---|---|
| Mọi request trả 401 | `CLERK_ISSUER` không phải của instance đang dùng hoặc thừa dấu `/`; đồng hồ máy lệch quá 30 giây; `CLERK_AZP` khác địa chỉ đang mở frontend (ví dụ `127.0.0.1` thay vì `localhost`) |
| Đăng nhập xong báo "Chưa được cấp quyền" | Thiếu claim `email` (bước 3), hoặc email đăng nhập khác `BOOTSTRAP_ORGANIZER_EMAIL` và chưa được mời |
| "New sign-ups are currently restricted" khi đăng nhập lần đầu | Email chưa được mời trên Clerk. Mở link trong thư mời, hoặc mời tay ở Dashboard → Users → Invitations |
| Không tạo Người tổ chức đầu tiên | Hệ thống đã có Người tổ chức đang hoạt động, hoặc `BOOTSTRAP_ORGANIZER_EMAIL` bị trống |
| Gửi lời mời báo `CLERK_NOT_CONFIGURED` | Thiếu `CLERK_SECRET_KEY` |
| Gửi lời mời báo `CLERK_INVITE_FAILED` | Secret key sai instance, hoặc email đã là người dùng của Clerk |
