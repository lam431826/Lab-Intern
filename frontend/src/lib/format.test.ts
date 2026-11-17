import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime, formatScore, formatTime } from './format'

describe('format', () => {
  it('hiển thị Instant UTC theo giờ Việt Nam', () => {
    // 17:30 UTC = 00:30 ngày hôm sau ở Việt Nam
    expect(formatDateTime('2026-11-03T17:30:00Z')).toBe('00:30 04/11/2026')
  })

  it('LocalDate không bị lệch ngày do múi giờ', () => {
    expect(formatDate('2026-11-20')).toBe('20/11/2026')
    expect(formatDate(null)).toBe('')
  })

  it('giờ kiểu file Excel cũ', () => {
    expect(formatTime('10:00:00')).toBe('10h')
    expect(formatTime('16:15')).toBe('16h15')
  })

  it('điểm 2 chữ số, dấu phẩy', () => {
    expect(formatScore(8.1)).toBe('8,10')
    expect(formatScore(7.433)).toBe('7,43')
    expect(formatScore(null)).toBe('—')
  })
})
