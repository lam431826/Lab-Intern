import { Pencil } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { useCan } from '@/auth/useCan'
import { PageHeader } from '@/components/common/PageHeader'
import { QueryState } from '@/components/common/QueryState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useTracks, type Track } from './api'
import { CriteriaList } from './CriteriaList'
import { TrackFormDialog } from './TrackFormDialog'

const GENERAL_TAB = 'GENERAL'

export function TracksPage() {
  const can = useCan()
  const tracks = useTracks()
  const [params, setParams] = useSearchParams()
  const [editing, setEditing] = useState<Track | null>(null)

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        title="Ngạch và tiêu chí"
        description="Chỉ tiêu, số slot phỏng vấn, Lead phụ trách và bộ tiêu chí chấm của từng ngạch."
      />
      <QueryState query={tracks}>
        {(list) => {
          // Lead mở trang thì vào ngạch mình trước
          const fallback = list.find((t) => can.leads(t.code))?.code ?? list[0]?.code ?? GENERAL_TAB
          const tab = params.get('tab') ?? fallback
          return (
            <Tabs value={tab} onValueChange={(v) => setParams({ tab: String(v) }, { replace: true })}>
              <div className="overflow-x-auto pb-1">
                <TabsList>
                  {list.map((t) => (
                    <TabsTrigger key={t.code} value={t.code}>
                      {t.name}
                    </TabsTrigger>
                  ))}
                  <TabsTrigger value={GENERAL_TAB}>Tiêu chí chung</TabsTrigger>
                </TabsList>
              </div>
              {list.map((t) => (
                <TabsContent key={t.code} value={t.code} className="mt-4 space-y-6">
                  <TrackSummary track={t} onEdit={can.editTrack ? () => setEditing(t) : undefined} />
                  <CriteriaList trackId={t.id} trackName={t.name} editable={can.editCriteria(t.code)} />
                </TabsContent>
              ))}
              <TabsContent value={GENERAL_TAB} className="mt-4">
                <CriteriaList trackId={null} trackName="" editable={can.editCriteria(null)} />
              </TabsContent>
            </Tabs>
          )
        }}
      </QueryState>
      {editing && (
        <TrackFormDialog
          open={editing !== null}
          onOpenChange={(open) => !open && setEditing(null)}
          track={editing}
        />
      )}
    </div>
  )
}

function TrackSummary({ track, onEdit }: { track: Track; onEdit?: () => void }) {
  const stats = [
    { label: 'Chỉ tiêu', value: track.quota },
    { label: 'Slot phỏng vấn', value: `${track.slotMin}–${track.slotMax}` },
    { label: 'Lead', value: track.leadName ?? 'Chưa có' },
  ]
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-2">
          {track.name}
          <Badge variant="outline">{track.code}</Badge>
          {track.requiresPortfolio && <Badge variant="secondary">Bắt buộc Portfolio</Badge>}
        </CardTitle>
        {onEdit && (
          <CardAction>
            <Button size="sm" variant="outline" onClick={onEdit}>
              <Pencil aria-hidden /> Sửa ngạch
            </Button>
          </CardAction>
        )}
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-3 gap-3 text-sm">
          {stats.map((s) => (
            <div key={s.label}>
              <dt className="text-muted-foreground">{s.label}</dt>
              <dd className={`font-medium ${s.value === 'Chưa có' ? 'text-destructive' : ''}`}>{s.value}</dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  )
}
