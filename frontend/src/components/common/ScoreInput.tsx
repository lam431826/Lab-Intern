import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

const SCORES = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] as const

/** Màu gợi ý theo thang chấm: 1–6 Trung bình, 7–8 Khá – Đạt, 9–10 Xuất sắc. */
function band(score: number): string {
  if (score <= 6) return 'data-[selected=true]:bg-amber-500 data-[selected=true]:border-amber-500'
  if (score <= 8) return 'data-[selected=true]:bg-sky-600 data-[selected=true]:border-sky-600'
  return 'data-[selected=true]:bg-emerald-600 data-[selected=true]:border-emerald-600'
}

/**
 * Chọn điểm 1–10 bằng một dãy nút (dễ bấm trên điện thoại hơn ô nhập số).
 * Dùng cho 5 mục chấm CV (F4) và phiếu chấm phỏng vấn (F6).
 */
export function ScoreInput({
  value,
  onChange,
  label,
  disabled = false,
  clearable = true,
}: {
  value: number | null
  onChange: (value: number | null) => void
  label: string
  disabled?: boolean
  clearable?: boolean
}) {
  return (
    <div className="flex flex-wrap items-center gap-1" role="radiogroup" aria-label={label}>
      {SCORES.map((s) => {
        const selected = value === s
        return (
          <button
            key={s}
            type="button"
            role="radio"
            aria-checked={selected}
            aria-label={`${label}: ${s} điểm`}
            data-selected={selected}
            disabled={disabled}
            onClick={() => onChange(s)}
            className={cn(
              'size-9 rounded-md border text-sm font-medium tabular-nums transition-colors sm:size-8',
              'hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none',
              'disabled:cursor-not-allowed disabled:opacity-50 data-[selected=true]:text-white',
              band(s),
            )}
          >
            {s}
          </button>
        )
      })}
      {clearable && value !== null && !disabled && (
        <button
          type="button"
          onClick={() => onChange(null)}
          aria-label={`Xóa điểm ${label}`}
          className="ml-1 rounded-md p-1.5 text-muted-foreground hover:bg-muted"
        >
          <X className="size-4" aria-hidden />
        </button>
      )}
    </div>
  )
}
