import { MailPlus, Pencil, Plus, Search } from 'lucide-react'
import { useDeferredValue, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { useCan } from '@/auth/useCan'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryState } from '@/components/common/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { toastError } from '@/lib/errors'
import { ALL_ROLES, ROLE_LABELS } from '@/lib/roles'
import { searchText } from '@/lib/text'
import { useResendInvite, useUsers, type User } from './api'
import { UserFormDialog } from './UserFormDialog'

const ROLE_FILTER = [
  { value: 'ALL', label: 'Mọi vai trò' },
  ...ALL_ROLES.map((r) => ({ value: r, label: ROLE_LABELS[r] })),
]

export function UsersPage() {
  const can = useCan()
  const users = useUsers()
  const resend = useResendInvite()
  const [q, setQ] = useState('')
  const [role, setRole] = useState('ALL')
  const [dialog, setDialog] = useState<{ open: boolean; user: User | null }>({ open: false, user: null })
  const query = useDeferredValue(searchText(q))

  const filtered = useMemo(
    () =>
      (users.data ?? []).filter(
        (u) =>
          (role === 'ALL' || u.role === role) &&
          (!query || searchText(`${u.fullName} ${u.email}`).includes(query)),
      ),
    [users.data, role, query],
  )

  async function onResend(u: User) {
    try {
      await resend.mutateAsync(u.id)
      toast.success(`Đã gửi lại lời mời tới ${u.email}`)
    } catch (e) {
      toastError(e)
    }
  }

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        title="Tài khoản"
        description="Nhân sự lab dùng hệ thống. Ứng viên không có tài khoản."
        actions={
          can.manageUsers && (
            <Button onClick={() => setDialog({ open: true, user: null })}>
              <Plus aria-hidden /> Thêm tài khoản
            </Button>
          )
        }
      />
      <div className="mb-3 flex flex-wrap gap-2">
        <div className="relative min-w-56 flex-1">
          <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-8"
            placeholder="Tìm theo tên hoặc email (không cần dấu)"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            aria-label="Tìm tài khoản"
          />
        </div>
        <Select items={ROLE_FILTER} value={role} onValueChange={(v) => setRole(v ?? 'ALL')}>
          <SelectTrigger className="w-48" aria-label="Lọc theo vai trò">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {ROLE_FILTER.map((i) => (
              <SelectItem key={i.value} value={i.value}>
                {i.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <QueryState query={users}>
        {() => (
          <div className="overflow-x-auto rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Họ tên</TableHead>
                  <TableHead>Email</TableHead>
                  <TableHead>Vai trò</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  {can.manageUsers && <TableHead className="w-0 text-right">Thao tác</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {filtered.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                      Không có tài khoản phù hợp.
                    </TableCell>
                  </TableRow>
                ) : (
                  filtered.map((u) => (
                    <TableRow key={u.id} className={u.active ? undefined : 'text-muted-foreground'}>
                      <TableCell className="font-medium">{u.fullName}</TableCell>
                      <TableCell>{u.email}</TableCell>
                      <TableCell>{ROLE_LABELS[u.role]}</TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-1">
                          {!u.active && <Badge variant="destructive">Đã khóa</Badge>}
                          {u.linked ? (
                            <Badge variant="secondary">Đã đăng nhập</Badge>
                          ) : (
                            <Badge variant="outline">Chưa đăng nhập</Badge>
                          )}
                        </div>
                      </TableCell>
                      {can.manageUsers && (
                        <TableCell className="text-right whitespace-nowrap">
                          {!u.linked && u.active && (
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => onResend(u)}
                              disabled={resend.isPending}
                              aria-label={`Gửi lại lời mời cho ${u.fullName}`}
                            >
                              <MailPlus aria-hidden />{' '}
                              <span className="hidden md:inline">Gửi lại lời mời</span>
                            </Button>
                          )}
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => setDialog({ open: true, user: u })}
                            aria-label={`Sửa ${u.fullName}`}
                          >
                            <Pencil aria-hidden /> <span className="hidden md:inline">Sửa</span>
                          </Button>
                        </TableCell>
                      )}
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        )}
      </QueryState>
      <UserFormDialog
        open={dialog.open}
        user={dialog.user}
        onOpenChange={(open) => setDialog((d) => ({ ...d, open }))}
      />
    </div>
  )
}
