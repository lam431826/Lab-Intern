#!/usr/bin/env bash
# Sao lưu full database vào ./backup/<db>_<thời gian>.bak, giữ lại KEEP_DAYS ngày (mặc định 30).
# Chạy từ thư mục gốc repo (cạnh docker-compose.yml):   ./infra/backup/backup.sh
# Đặt lịch hằng đêm (crontab -e):  0 2 * * * cd /opt/dxlab-recruitment && ./infra/backup/backup.sh >> backup/backup.log 2>&1
set -euo pipefail

DB_NAME="${DB_NAME:-dxlab_recruitment}"
KEEP_DAYS="${KEEP_DAYS:-30}"
STAMP="$(date +%Y%m%d_%H%M%S)"
FILE="${DB_NAME}_${STAMP}.bak"

# Mật khẩu SA lấy từ biến môi trường bên trong container, không đi qua dòng lệnh của máy chủ
docker compose exec -T -e DB_NAME="$DB_NAME" -e FILE="$FILE" mssql bash -c '
  /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -b -Q "
    BACKUP DATABASE [$DB_NAME] TO DISK = N'"'"'/var/opt/mssql/backup/$FILE'"'"'
    WITH INIT, CHECKSUM, NAME = N'"'"'$DB_NAME full'"'"';
    RESTORE VERIFYONLY FROM DISK = N'"'"'/var/opt/mssql/backup/$FILE'"'"' WITH CHECKSUM;"'

echo "$(date '+%F %T') Đã sao lưu và kiểm tra: backup/$FILE"
find backup -maxdepth 1 -name "${DB_NAME}_*.bak" -mtime "+${KEEP_DAYS}" -print -delete
