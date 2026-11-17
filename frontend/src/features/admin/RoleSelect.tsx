import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ALL_ROLES, ROLE_LABELS, type Role } from '@/lib/roles'

const ITEMS = ALL_ROLES.map((r) => ({ value: r, label: ROLE_LABELS[r] }))

export function RoleSelect({
  id,
  value,
  onChange,
  invalid,
}: {
  id?: string
  value: Role
  onChange: (role: Role) => void
  invalid?: boolean
}) {
  return (
    <Select items={ITEMS} value={value} onValueChange={(v) => v && onChange(v as Role)}>
      <SelectTrigger id={id} className="w-full" aria-invalid={invalid}>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {ITEMS.map((i) => (
          <SelectItem key={i.value} value={i.value}>
            {i.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
