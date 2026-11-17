import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { handleFormError } from '@/lib/forms'
import { ALL_ROLES } from '@/lib/roles'
import { useCreateUser, useUpdateUser, type User } from './api'
import { RoleSelect } from './RoleSelect'

const schema = z.object({
  email: z.string().trim().min(1, 'Email không được để trống').email('Email không đúng định dạng').max(254),
  fullName: z.string().trim().min(1, 'Họ tên không được để trống').max(100, 'Họ tên tối đa 100 ký tự'),
  role: z.enum(ALL_ROLES as [User['role'], ...User['role'][]]),
  active: z.boolean(),
  sendInvite: z.boolean(),
})
type Values = z.infer<typeof schema>

/** {@code user = null}: tạo tài khoản mới (gửi lời mời Clerk); có user: sửa họ tên, vai trò, khóa/mở. */
export function UserFormDialog({
  open,
  onOpenChange,
  user,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  user: User | null
}) {
  const create = useCreateUser()
  const update = useUpdateUser()
  const editing = user !== null
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', fullName: '', role: 'MEMBER', active: true, sendInvite: true },
  })

  useEffect(() => {
    if (open) {
      form.reset(
        user
          ? {
              email: user.email,
              fullName: user.fullName,
              role: user.role,
              active: user.active,
              sendInvite: false,
            }
          : { email: '', fullName: '', role: 'MEMBER', active: true, sendInvite: true },
      )
    }
  }, [open, user, form])

  const pending = create.isPending || update.isPending

  async function onSubmit(v: Values) {
    try {
      if (editing) {
        await update.mutateAsync({
          id: user.id,
          body: { fullName: v.fullName, role: v.role, active: v.active },
        })
        toast.success('Đã lưu tài khoản')
      } else {
        await create.mutateAsync({
          email: v.email,
          fullName: v.fullName,
          role: v.role,
          sendInvite: v.sendInvite,
        })
        toast.success(v.sendInvite ? `Đã tạo tài khoản và gửi lời mời tới ${v.email}` : 'Đã tạo tài khoản')
      }
      onOpenChange(false)
    } catch (e) {
      handleFormError(e, form.setError, ['email', 'fullName', 'role'])
    }
  }

  const errors = form.formState.errors
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
          <DialogHeader>
            <DialogTitle>{editing ? 'Sửa tài khoản' : 'Thêm tài khoản'}</DialogTitle>
            <DialogDescription>
              {editing
                ? 'Thay đổi có hiệu lực ngay ở thao tác kế tiếp của người dùng.'
                : 'Người dùng đăng nhập bằng đúng email này (Google hoặc email) để vào hệ thống.'}
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="py-4">
            <Field data-invalid={!!errors.email}>
              <FieldLabel htmlFor="user-email">Email</FieldLabel>
              <Input
                id="user-email"
                type="email"
                autoComplete="off"
                disabled={editing}
                aria-invalid={!!errors.email}
                {...form.register('email')}
              />
              {editing && (
                <FieldDescription>Email gắn với tài khoản Clerk nên không sửa ở đây.</FieldDescription>
              )}
              <FieldError errors={[errors.email]} />
            </Field>
            <Field data-invalid={!!errors.fullName}>
              <FieldLabel htmlFor="user-name">Họ và tên</FieldLabel>
              <Input id="user-name" aria-invalid={!!errors.fullName} {...form.register('fullName')} />
              <FieldError errors={[errors.fullName]} />
            </Field>
            <Field data-invalid={!!errors.role}>
              <FieldLabel htmlFor="user-role">Vai trò</FieldLabel>
              <Controller
                control={form.control}
                name="role"
                render={({ field }) => (
                  <RoleSelect
                    id="user-role"
                    value={field.value}
                    onChange={field.onChange}
                    invalid={!!errors.role}
                  />
                )}
              />
              <FieldDescription>
                Lead được giao ngạch ở mục Ngạch và tiêu chí. Quyền chấm điểm do hội đồng của từng ca quyết
                định.
              </FieldDescription>
              <FieldError errors={[errors.role]} />
            </Field>
            {editing ? (
              <Field orientation="horizontal">
                <Controller
                  control={form.control}
                  name="active"
                  render={({ field }) => (
                    <Switch id="user-active" checked={field.value} onCheckedChange={field.onChange} />
                  )}
                />
                <FieldLabel htmlFor="user-active">Đang hoạt động (tắt để khóa tài khoản)</FieldLabel>
              </Field>
            ) : (
              <Field orientation="horizontal">
                <Controller
                  control={form.control}
                  name="sendInvite"
                  render={({ field }) => (
                    <Switch id="user-invite" checked={field.value} onCheckedChange={field.onChange} />
                  )}
                />
                <FieldLabel htmlFor="user-invite">Gửi email mời ngay</FieldLabel>
              </Field>
            )}
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={pending}>
              Hủy
            </Button>
            <Button type="submit" disabled={pending}>
              {editing ? 'Lưu' : 'Tạo tài khoản'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
