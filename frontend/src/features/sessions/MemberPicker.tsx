import { useMemo } from 'react'
import { useUsers } from '@/features/admin/api'
import { ROLE_LABELS } from '@/lib/roles'
import { searchText } from '@/lib/text'

/**
 * Chọn thành viên hội đồng luân chuyển (mọi tài khoản đang hoạt động, trừ người đã là thành viên cố định).
 * Hội đồng chuẩn 6 người: Giảng viên, Người tổ chức, Lead ngạch và 3 thành viên luân chuyển.
 */
export function MemberPicker({
  value,
  onChange,
  excludeIds = [],
  filter = '',
}: {
  value: number[]
  onChange: (ids: number[]) => void
  excludeIds?: number[]
  filter?: string
}) {
  const users = useUsers()
  const q = searchText(filter)
  const options = useMemo(
    () =>
      (users.data ?? []).filter(
        (u) =>
          u.active &&
          !excludeIds.includes(u.id) &&
          (!q || searchText(`${u.fullName} ${u.email}`).includes(q)),
      ),
    [users.data, excludeIds, q],
  )

  if (users.isPending) return <p className="text-sm text-muted-foreground">Đang tải danh sách…</p>
  if (options.length === 0)
    return <p className="text-sm text-muted-foreground">Không có tài khoản phù hợp.</p>

  return (
    <ul
      className="max-h-56 space-y-1 overflow-y-auto rounded-md border p-2"
      aria-label="Thành viên luân chuyển"
    >
      {options.map((u) => {
        const checked = value.includes(u.id)
        return (
          <li key={u.id}>
            <label className="flex cursor-pointer items-center gap-2 rounded px-1 py-1 text-sm hover:bg-muted">
              <input
                type="checkbox"
                className="size-4 accent-primary"
                checked={checked}
                onChange={() => onChange(checked ? value.filter((id) => id !== u.id) : [...value, u.id])}
              />
              <span className="flex-1">{u.fullName}</span>
              <span className="text-xs text-muted-foreground">{ROLE_LABELS[u.role]}</span>
            </label>
          </li>
        )
      })}
    </ul>
  )
}
