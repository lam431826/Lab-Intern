import type { HistoryEntry } from './api'

const FIELD_LABELS: Record<string, string> = {
  screeningStatus: 'Sàng lọc CV',
  track: 'Ngạch',
  slot: 'Lịch phỏng vấn',
  attendance: 'Điểm danh',
  decision: 'Kết luận',
  approved: 'Phê duyệt',
  emailStatus: 'Email kết quả',
  offerStatus: 'Offer',
}

const VALUE_LABELS: Record<string, string> = {
  PENDING: 'Chờ',
  PASSED: 'Đạt',
  REJECTED: 'Loại',
  ATTENDED: 'Có mặt',
  ABSENT: 'Vắng mặt',
  HIRED: 'Trúng tuyển',
  WAITLIST: 'Dự bị',
  TRANSFER: 'Chuyển ngạch',
  NOT_SENT: 'Chưa gửi',
  SENT: 'Đã gửi',
  NONE: 'Chưa phản hồi',
  ACCEPTED: 'Nhận',
  DECLINED: 'Từ chối',
  true: 'Có',
  false: 'Không',
}

export function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? field
}

/** Giá trị trong lịch sử là chuỗi của backend (enum, "PV1 2026-11-20 10h - 10h15"...): dịch enum, giữ nguyên phần còn lại. */
export function valueLabel(value: string | null): string {
  if (value === null) return '—'
  return VALUE_LABELS[value] ?? value
}

export function describe(h: HistoryEntry): string {
  if (h.oldValue === null) return `${fieldLabel(h.field)}: ${valueLabel(h.newValue)}`
  return `${fieldLabel(h.field)}: ${valueLabel(h.oldValue)} → ${valueLabel(h.newValue)}`
}
