import type { UseQueryResult } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import type { ReactNode } from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { errorMessage } from '@/lib/errors'

/** Hiển thị khung chờ khi đang tải, lỗi kèm nút thử lại, rồi mới tới nội dung. */
export function QueryState<T>({
  query,
  children,
  skeletonRows = 4,
}: {
  query: UseQueryResult<T>
  children: (data: T) => ReactNode
  skeletonRows?: number
}) {
  if (query.isPending) {
    return (
      <div className="space-y-2" role="status" aria-label="Đang tải">
        {Array.from({ length: skeletonRows }, (_, i) => (
          <Skeleton key={i} className="h-10 w-full" />
        ))}
      </div>
    )
  }
  if (query.isError) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Không tải được dữ liệu</AlertTitle>
        <AlertDescription className="flex flex-wrap items-center gap-3">
          {errorMessage(query.error)}
          <Button size="sm" variant="outline" onClick={() => query.refetch()}>
            <RefreshCw aria-hidden /> Thử lại
          </Button>
        </AlertDescription>
      </Alert>
    )
  }
  return <>{children(query.data)}</>
}
