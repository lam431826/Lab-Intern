import { describe, expect, it } from 'vitest'
import { searchText } from './text'

describe('searchText', () => {
  it('bỏ dấu, đ thành d, chữ thường, gộp khoảng trắng', () => {
    expect(searchText('  Nguyễn   Đức Hòa ')).toBe('nguyen duc hoa')
    expect(searchText('ĐẶNG THỊ BÌNH')).toBe('dang thi binh')
  })
})
