import { ArrowLeft, CheckCircle2, Download, FileSpreadsheet, Upload } from 'lucide-react'
import { useRef, useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { saveFile } from '@/api/client'
import { useApi } from '@/api/useApi'
import { PageHeader } from '@/components/common/PageHeader'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { toastError } from '@/lib/errors'
import { formatBytes } from '@/lib/format'
import { useImportCandidates, type ImportReport } from './api'

/** Giới hạn upload của backend (spring.servlet.multipart.max-file-size). */
const MAX_BYTES = 5 * 1024 * 1024

export function checkImportFile(file: File): string | null {
  if (!file.name.toLowerCase().endsWith('.xlsx'))
    return 'Chỉ nhận file Excel .xlsx (lưu lại file .xls bằng Excel nếu cần)'
  if (file.size > MAX_BYTES) return 'File tối đa 5 MB'
  return null
}

export function ImportPage() {
  const api = useApi()
  const importer = useImportCandidates()
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [report, setReport] = useState<ImportReport | null>(null)

  async function downloadTemplate() {
    try {
      saveFile(await api.download('/api/candidates/import-template', 'mau-nhap-ung-vien.xlsx'))
    } catch (e) {
      toastError(e)
    }
  }

  async function run(dryRun: boolean, f: File | null = file) {
    if (!f) return
    try {
      const r = await importer.mutateAsync({ file: f, dryRun })
      setReport(r)
      if (r.applied) {
        toast.success(
          `Đã nhập ${r.totalRows} dòng: ${r.created} mới, ${r.updated} cập nhật, ${r.unchanged} không đổi`,
        )
      }
    } catch (e) {
      toastError(e)
    }
  }

  function onPick(f: File | undefined) {
    if (input.current) input.current.value = ''
    if (!f) return
    const problem = checkImportFile(f)
    if (problem) {
      toast.error(problem)
      return
    }
    setFile(f)
    setReport(null)
    void run(true, f)
  }

  const canApply =
    file !== null && report !== null && report.dryRun && report.errors.length === 0 && report.totalRows > 0

  return (
    <div className="mx-auto max-w-4xl">
      <Button
        variant="ghost"
        size="sm"
        className="mb-2 -ml-2"
        nativeButton={false}
        render={<Link to="/candidates" />}
      >
        <ArrowLeft aria-hidden /> Danh sách hồ sơ
      </Button>
      <PageHeader
        title="Nhập hồ sơ từ Excel"
        description="Nhận được cả file kiểu cũ của lab: tự tìm dòng tiêu đề, nhận nhiều cách đặt tên cột và tên ngạch. Trùng Mã SV thì cập nhật thông tin, giữ nguyên kết quả sàng lọc."
      />
      <div className="space-y-4">
        <Card size="sm">
          <CardHeader>
            <CardTitle>1. Tải file mẫu (không bắt buộc)</CardTitle>
            <CardDescription>
              File mẫu có sẵn danh sách ngạch và định dạng cột Mã SV, SĐT dạng chữ để giữ số 0 ở đầu.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="outline" onClick={downloadTemplate}>
              <Download aria-hidden /> Tải file mẫu
            </Button>
          </CardContent>
        </Card>

        <Card size="sm">
          <CardHeader>
            <CardTitle>2. Chọn file và kiểm tra</CardTitle>
            <CardDescription>
              Hệ thống đọc thử toàn bộ file và báo lỗi theo số dòng Excel. Chưa có gì được lưu ở bước này.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap items-center gap-3">
            <input
              ref={input}
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              className="sr-only"
              aria-label="Chọn file Excel danh sách ứng viên"
              onChange={(e) => onPick(e.target.files?.[0])}
            />
            <Button onClick={() => input.current?.click()} disabled={importer.isPending}>
              <Upload aria-hidden /> {file ? 'Chọn file khác' : 'Chọn file .xlsx'}
            </Button>
            {file && (
              <span className="flex items-center gap-1 text-sm">
                <FileSpreadsheet className="size-4 text-muted-foreground" aria-hidden />
                {file.name} <span className="text-muted-foreground">({formatBytes(file.size)})</span>
              </span>
            )}
            {importer.isPending && <span className="text-sm text-muted-foreground">Đang đọc file…</span>}
          </CardContent>
        </Card>

        {report && <ReportView report={report} />}

        <Card size="sm">
          <CardHeader>
            <CardTitle>3. Nhập vào hệ thống</CardTitle>
            <CardDescription>
              Chỉ nhập được khi bước 2 không còn lỗi. Có lỗi ở bất kỳ dòng nào thì không dòng nào được lưu.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap items-center gap-3">
            <Button onClick={() => run(false)} disabled={!canApply || importer.isPending}>
              <CheckCircle2 aria-hidden /> Nhập {report?.dryRun ? report.totalRows : ''} hồ sơ
            </Button>
            {report?.applied && (
              <Button variant="outline" nativeButton={false} render={<Link to="/candidates" />}>
                Xem danh sách hồ sơ
              </Button>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function ReportView({ report: r }: { report: ImportReport }) {
  const stats = [
    { label: 'Số dòng', value: r.totalRows },
    { label: 'Hồ sơ mới', value: r.created },
    { label: 'Cập nhật', value: r.updated },
    { label: 'Không đổi', value: r.unchanged },
  ]
  return (
    <div className="space-y-3" aria-live="polite">
      {r.errors.length > 0 ? (
        <Alert variant="destructive">
          <AlertTitle>File có {r.errors.length} lỗi, chưa nhập được</AlertTitle>
          <AlertDescription>Sửa các dòng dưới đây trong Excel rồi chọn lại file.</AlertDescription>
        </Alert>
      ) : r.applied ? (
        <Alert>
          <CheckCircle2 aria-hidden />
          <AlertTitle>Đã nhập xong</AlertTitle>
          <AlertDescription>Hồ sơ mới ở trạng thái Chờ duyệt, Lead các ngạch sẽ thấy ngay.</AlertDescription>
        </Alert>
      ) : (
        <Alert>
          <CheckCircle2 aria-hidden />
          <AlertTitle>File hợp lệ</AlertTitle>
          <AlertDescription>Kiểm tra số liệu bên dưới rồi bấm Nhập ở bước 3.</AlertDescription>
        </Alert>
      )}
      <dl className="grid grid-cols-4 gap-2 text-center">
        {stats.map((s) => (
          <div key={s.label} className="rounded-lg border p-2">
            <dd className="text-xl font-semibold tabular-nums">{s.value}</dd>
            <dt className="text-xs text-muted-foreground">{s.label}</dt>
          </div>
        ))}
      </dl>
      {r.errors.length > 0 && (
        <div className="max-h-96 overflow-auto rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-20">Dòng</TableHead>
                <TableHead className="w-32">Mã SV</TableHead>
                <TableHead>Lỗi</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {r.errors.map((e, i) => (
                <TableRow key={i}>
                  <TableCell className="tabular-nums">{e.row}</TableCell>
                  <TableCell className="font-mono text-xs">{e.studentCode ?? '—'}</TableCell>
                  <TableCell>{e.message}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  )
}
