#!/bin/sh
# Ghi cấu hình lúc chạy cho frontend: cùng một image dùng được cho Clerk development và production.
set -eu

key="${CLERK_PUBLISHABLE_KEY:-}"
# Publishable key chỉ gồm chữ, số, gạch dưới, gạch ngang và $: chặn chèn mã vào config.js
case "$key" in
  *[!A-Za-z0-9_\$-]*) echo "CLERK_PUBLISHABLE_KEY chứa ký tự không hợp lệ" >&2; exit 1 ;;
esac
if [ -z "$key" ]; then
  echo "Cảnh báo: thiếu CLERK_PUBLISHABLE_KEY, trang web sẽ báo thiếu cấu hình đăng nhập" >&2
fi
if [ -z "${CLERK_FRONTEND_API:-}" ]; then
  echo "Cảnh báo: thiếu CLERK_FRONTEND_API, CSP sẽ chặn script của Clerk" >&2
fi

printf 'window.__APP_CONFIG__ = { clerkPublishableKey: "%s" }\n' "$key" > /usr/share/nginx/html/config.js
