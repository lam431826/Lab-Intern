# Sao lưu trên máy Windows (Docker Desktop). Chạy từ thư mục gốc repo:
#   powershell -File infra\backup\backup.ps1
# Đặt lịch: Task Scheduler, chạy hằng đêm với "Start in" là thư mục gốc repo.
# File lưu dạng UTF-8 có BOM để Windows PowerShell 5.1 đọc đúng tiếng Việt.
$ErrorActionPreference = 'Stop'
$db = if ($env:DB_NAME) { $env:DB_NAME } else { 'dxlab_recruitment' }
$keepDays = if ($env:KEEP_DAYS) { [int]$env:KEEP_DAYS } else { 30 }
$file = "{0}_{1}.bak" -f $db, (Get-Date -Format 'yyyyMMdd_HHmmss')

$sql = "BACKUP DATABASE [$db] TO DISK = N'/var/opt/mssql/backup/$file' WITH INIT, CHECKSUM, NAME = N'$db full';`n" +
       "RESTORE VERIFYONLY FROM DISK = N'/var/opt/mssql/backup/$file' WITH CHECKSUM;"
# Câu SQL đi qua stdin: PowerShell 5.1 không escape dấu " lồng trong tham số của chương trình ngoài.
# Mật khẩu SA lấy từ biến môi trường bên trong container (MSSQL_SA_PASSWORD chỉ gồm chữ và số).
$sql | docker compose exec -T mssql bash -c 'cat > /tmp/backup.sql && exec /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P $MSSQL_SA_PASSWORD -C -b -i /tmp/backup.sql'
if ($LASTEXITCODE -ne 0) { throw "Sao lưu thất bại" }
Write-Output "Đã sao lưu và kiểm tra: backup\$file"

Get-ChildItem backup -Filter "${db}_*.bak" |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$keepDays) } |
    Remove-Item -Verbose
