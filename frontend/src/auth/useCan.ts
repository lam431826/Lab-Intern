import { useMe } from './useMe'

/**
 * Quy tắc ẩn/hiện nút theo vai trò, khớp với kiểm tra của backend (backend vẫn là nơi quyết định).
 */
export function useCan() {
  const me = useMe()
  const isOrganizer = me.role === 'ORGANIZER'
  const isLecturer = me.role === 'LECTURER'
  const leads = (trackCode: string) => me.role === 'LEAD' && me.leadTrackCodes.includes(trackCode)
  return {
    me,
    isOrganizer,
    isLecturer,
    leads,
    /** Tạo, sửa, khóa tài khoản và gửi lời mời. */
    manageUsers: isOrganizer,
    /** Sửa thông tin ngạch (chỉ tiêu, slot, Lead). */
    editTrack: isOrganizer,
    /** Tiêu chí chung: chỉ Người tổ chức; tiêu chí chuyên môn: thêm Lead của ngạch. */
    editCriteria: (trackCode: string | null) => isOrganizer || (trackCode !== null && leads(trackCode)),
    /** Trọng số, điểm sàn, SLA, độ dài slot. */
    editSettings: isOrganizer || isLecturer,
  }
}
