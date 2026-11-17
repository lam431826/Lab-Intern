import { Construction } from 'lucide-react'

/** Trang chưa làm: ghi rõ mốc sẽ có, để người thử nghiệm không tưởng là lỗi. */
export function PlaceholderPage({ milestone }: { milestone: string }) {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-3 py-16 text-center">
      <Construction className="size-8 text-muted-foreground" aria-hidden />
      <h2 className="text-lg font-semibold">Màn hình đang được xây dựng</h2>
      <p className="text-sm text-muted-foreground">
        Chức năng này sẽ có ở mốc {milestone} của kế hoạch frontend.
      </p>
    </div>
  )
}
