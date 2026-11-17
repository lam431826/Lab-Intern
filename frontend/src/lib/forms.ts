import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { ApiError } from '@/api/client'
import { toastError } from './errors'

/**
 * Gắn lỗi 422 VALIDATION_FAILED của backend vào đúng ô form; lỗi khác (nghiệp vụ, 409...) hiện thông báo.
 * Nội dung người dùng đã nhập luôn được giữ nguyên.
 */
export function handleFormError<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): void {
  if (error instanceof ApiError && error.fieldErrors.length > 0) {
    let attached = false
    for (const fe of error.fieldErrors) {
      if ((fields as readonly string[]).includes(fe.field)) {
        setError(fe.field as Path<T>, { type: 'server', message: fe.message })
        attached = true
      }
    }
    if (attached) return
  }
  toastError(error)
}

/** Chuỗi rỗng hoặc chỉ khoảng trắng → null, để backend lưu NULL thay vì chuỗi rỗng. */
export function blankToNull(value: string | null | undefined): string | null {
  const v = value?.trim()
  return v ? v : null
}
