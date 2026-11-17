import { config } from './config'

const dateTime = new Intl.DateTimeFormat('vi-VN', {
  timeZone: config.timeZone,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

const dateOnly = new Intl.DateTimeFormat('vi-VN', {
  timeZone: config.timeZone,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
})

/** Instant ISO của backend (UTC) → "03/11/2026 14:05" theo giờ Việt Nam. */
export function formatDateTime(iso: string | null | undefined): string {
  return iso ? dateTime.format(new Date(iso)) : ''
}

/** LocalDate "2026-11-20" → "20/11/2026". Không qua Date để tránh lệch ngày do múi giờ. */
export function formatDate(isoDate: string | null | undefined): string {
  if (!isoDate) return ''
  const [y, m, d] = isoDate.split('-')
  return `${d}/${m}/${y}`
}

/** Instant → ngày theo giờ Việt Nam. */
export function formatInstantDate(iso: string | null | undefined): string {
  return iso ? dateOnly.format(new Date(iso)) : ''
}

/** LocalTime "10:15:00" → "10h15"; "10:00" → "10h" (giống file Excel cũ). */
export function formatTime(time: string | null | undefined): string {
  if (!time) return ''
  const [h, m] = time.split(':')
  return m === '00' ? `${Number(h)}h` : `${Number(h)}h${m}`
}

/** Điểm 2 chữ số thập phân, dấu phẩy: 8.1 → "8,10". */
export function formatScore(value: number | null | undefined): string {
  return value == null
    ? '—'
    : value.toLocaleString('vi-VN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 1536 → "1,5 KB"; dùng cho file JD. */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB']
  let v = bytes / 1024
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toLocaleString('vi-VN', { maximumFractionDigits: 1 })} ${units[i]}`
}

const pad = (n: number) => String(n).padStart(2, '0')

/** Giá trị cho ô datetime-local theo giờ máy người dùng: "2026-11-17T14:05". */
export function toDateTimeLocal(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
