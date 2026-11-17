# Frontend — Hệ thống Tuyển dụng DX Lab

React 19 · TypeScript 7 · Vite 8 · Tailwind CSS 4 · shadcn/ui · TanStack Query · React Router · Clerk.
Kế hoạch và danh sách màn hình: [`../docs/frontend-plan.md`](../docs/frontend-plan.md).

## Chạy trên máy dev

Cần Node.js 20.9+ và backend đang chạy ở `localhost:8080` (profile `local`, xem README gốc).

```
npm ci
npm run dev        # http://localhost:5173, proxy /api → localhost:8080
```

Cấu hình đọc từ **`.env` ở gốc repo** (dùng chung với backend). Vite chỉ nhúng các biến có tiền tố `VITE_`
(`VITE_CLERK_PUBLISHABLE_KEY`) vào bundle; khóa bí mật của backend không bao giờ ra tới trình duyệt.
Phải mở đúng `http://localhost:5173`: backend chỉ nhận token có `azp` khớp `CLERK_AZP`.

## Lệnh

| Lệnh | Việc |
|---|---|
| `npm run gen:api` | Sinh `src/api/schema.d.ts` từ OpenAPI của backend đang chạy (`/v3/api-docs`, chỉ có ở profile `local`). Chạy lại mỗi khi DTO backend đổi |
| `npm test` | Vitest |
| `npm run lint` | oxlint |
| `npm run typecheck` | Kiểm tra kiểu |
| `npm run build` | Build vào `dist/` |
| `npm run format` | Prettier |

## Quy ước

- Gọi API qua `useApi()` (`src/api`); lỗi luôn là `ApiError` có `code` ổn định. Tải file dùng `api.download()` + `saveFile()`,
  không dùng `<a href>` vì endpoint cần header Bearer.
- Kiểu dữ liệu lấy từ `Schemas['TenDto']` (sinh tự động), không viết tay.
  Trường DTO backend là nullable trừ khi kiểu nguyên thủy, có `@NotNull`, hoặc record gắn `@NullMarked` (JSpecify).
  Khi làm màn hình dùng DTO nào thì gắn `@NullMarked` (và `@Nullable` cho trường được null) cho DTO đó rồi `gen:api`.
- Component shadcn nằm ở `src/components/ui` (thêm bằng `npx shadcn@4.22.1 add <tên>`), được phép sửa.
- Ngày giờ hiển thị theo `Asia/Ho_Chi_Minh` qua `src/lib/format.ts`.
- Phiên bản thư viện ghim chính xác (`.npmrc` có `save-exact`).
