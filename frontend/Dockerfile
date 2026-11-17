# syntax=docker/dockerfile:1
# Image frontend Hệ thống Tuyển dụng DX Lab: nginx phục vụ giao diện và chuyển /api sang backend.
#   docker build -t dxlab-recruitment-frontend frontend
# Biến lúc chạy: CLERK_PUBLISHABLE_KEY, CLERK_FRONTEND_API (= CLERK_ISSUER), BACKEND_URL.

# ---------- Build ----------
# Debian (glibc) thay vì Alpine: TypeScript 7, Tailwind oxide, rolldown dùng binary native
FROM node:24.18.0-bookworm-slim AS build
WORKDIR /src
COPY package.json package-lock.json .npmrc ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
# Test chạy trong bước build: image không bao giờ được tạo từ mã đang hỏng
RUN npm test && npm run build

# ---------- Runtime ----------
FROM nginxinc/nginx-unprivileged:1.29.3-alpine
# Templates được envsubst lúc khởi động thành /etc/nginx/conf.d/*
COPY nginx/default.conf.template nginx/security-headers.inc.template /etc/nginx/templates/
COPY --chmod=755 nginx/40-app-config.sh /docker-entrypoint.d/40-app-config.sh
# uid 101 (nginx) cần ghi config.js lúc khởi động
COPY --from=build --chown=101:101 /src/dist /usr/share/nginx/html

ENV BACKEND_URL=http://app:8080 \
    CLERK_FRONTEND_API="" \
    CLERK_PUBLISHABLE_KEY=""
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
