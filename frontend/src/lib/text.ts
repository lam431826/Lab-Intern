/**
 * Chuẩn hóa để tìm không phân biệt dấu và hoa thường, giống collation CI_AI của backend:
 * "Nguyễn Đức" → "nguyen duc".
 */
export function searchText(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim()
}
