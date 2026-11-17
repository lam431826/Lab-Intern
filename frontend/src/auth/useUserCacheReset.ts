import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'

/**
 * Đổi người dùng (đăng xuất, đăng nhập tài khoản khác) thì bỏ dữ liệu của người trước khỏi cache.
 *
 * Chỉ chạy khi trước đó đã có một người dùng thật (userId là chuỗi). Lần tải đầu, userId đi từ
 * undefined (Clerk đang tải) sang id: KHÔNG được dọn, vì lúc đó AccessGate vừa tạo query /api/me;
 * dọn bằng clear() sẽ xóa query khỏi cache và observer chờ mãi mà không gửi request
 * (lỗi đã gặp ở bản build production, nơi Clerk tải chậm hơn bản dev).
 *
 * Dùng resetQueries thay cho clear: dữ liệu cũ bị xóa nhưng các màn hình đang mở vẫn giữ observer
 * và tự tải lại dữ liệu của người dùng mới.
 */
export function useUserCacheReset(userId: string | null | undefined): void {
  const queryClient = useQueryClient()
  const lastUser = useRef(userId)

  useEffect(() => {
    const previous = lastUser.current
    lastUser.current = userId
    if (typeof previous === 'string' && previous !== userId) {
      queryClient.removeQueries({ type: 'inactive' })
      void queryClient.resetQueries()
    }
  }, [userId, queryClient])
}
