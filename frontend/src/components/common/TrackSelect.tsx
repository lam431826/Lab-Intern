import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

/** Chọn ngạch; {@code allLabel} thêm lựa chọn "tất cả" (value = "ALL") cho bộ lọc. */
export function TrackSelect({
  id,
  value,
  onChange,
  options,
  invalid,
  allLabel,
}: {
  id?: string
  value: string
  onChange: (value: string) => void
  options: { id: number; name: string }[]
  invalid?: boolean
  /** Có giá trị thì thêm lựa chọn "tất cả" (dùng cho bộ lọc), value = "ALL". */
  allLabel?: string
}) {
  const items = [
    ...(allLabel ? [{ value: 'ALL', label: allLabel }] : []),
    ...options.map((t) => ({ value: String(t.id), label: t.name })),
  ]
  return (
    <Select items={items} value={value} onValueChange={(v) => v && onChange(v)}>
      <SelectTrigger id={id} className="w-full" aria-invalid={invalid} aria-label={id ? undefined : 'Ngạch'}>
        <SelectValue placeholder="Chọn ngạch" />
      </SelectTrigger>
      <SelectContent>
        {items.map((i) => (
          <SelectItem key={i.value} value={i.value}>
            {i.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
