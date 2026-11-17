import { toast } from 'sonner'
import { ApiError } from '@/api/client'

/** Câu thông báo cho người dùng. Backend đã trả message tiếng Việt; lỗi khác dùng câu chung. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return 'Đã có lỗi xảy ra. Vui lòng thử lại.'
}

/** Hiện thông báo lỗi; STALE_DATA kèm hướng dẫn tải lại (nội dung đang nhập được giữ nguyên). */
export function toastError(error: unknown): void {
  if (error instanceof ApiError && error.isStale) {
    toast.warning(error.message, {
      description: 'Nội dung bạn đang nhập vẫn được giữ. Tải lại dữ liệu mới rồi lưu lại.',
    })
    return
  }
  toast.error(errorMessage(error))
}
