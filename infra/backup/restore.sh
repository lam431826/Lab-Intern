#!/usr/bin/env bash
# Khôi phục database từ một file trong ./backup. GHI ĐÈ dữ liệu hiện tại.
#   ./infra/backup/restore.sh dxlab_recruitment_20261103_020000.bak
set -euo pipefail

FILE="${1:?Cách dùng: restore.sh <tên file .bak trong thư mục backup>}"
DB_NAME="${DB_NAME:-dxlab_recruitment}"
DB_USER="${DB_USER:-dxlab_app}"
[ -f "backup/$FILE" ] || { echo "Không thấy backup/$FILE"; exit 1; }

read -r -p "Ghi đè database $DB_NAME bằng $FILE? Gõ đúng tên database để xác nhận: " answer
[ "$answer" = "$DB_NAME" ] || { echo "Đã hủy"; exit 1; }

docker compose stop app
docker compose exec -T -e DB_NAME="$DB_NAME" -e DB_USER="$DB_USER" -e FILE="$FILE" mssql bash -c '
  /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -b -Q "
    IF DB_ID(N'"'"'$DB_NAME'"'"') IS NOT NULL ALTER DATABASE [$DB_NAME] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    RESTORE DATABASE [$DB_NAME] FROM DISK = N'"'"'/var/opt/mssql/backup/$FILE'"'"' WITH REPLACE, CHECKSUM;
    ALTER DATABASE [$DB_NAME] SET MULTI_USER;
    USE [$DB_NAME];
    -- Bản sao lưu từ máy chủ khác: nối lại user trong database với login của máy này
    ALTER USER [$DB_USER] WITH LOGIN = [$DB_USER];"'
docker compose start app
echo "Đã khôi phục $DB_NAME từ $FILE"
