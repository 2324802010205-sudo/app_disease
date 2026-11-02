import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useInfiniteQuery } from '@tanstack/react-query'
import { auditService } from '../api/services'
import { Button, Page, PageHeader, Panel, QueryState } from '../components/ui'
import { shortTime } from '../lib/admin'

export function Audit() {
  const [params] = useSearchParams()
  const [actor, setActor] = useState(params.get('actorId') || '')
  const [target, setTarget] = useState(params.get('targetId') || '')
  const [action, setAction] = useState('')
  const [type, setType] = useState('')
  const filters = { actorId: actor, targetId: target, action, targetType: type }
  const query = useInfiniteQuery({ queryKey: ['admin-audit', filters], queryFn: ({ pageParam }) => auditService.list(filters, pageParam), initialPageParam: null as string | null, getNextPageParam: p => p.nextCursor })
  const rows = query.data?.pages.flatMap(p => p.items) ?? []
  return <Page art="field">
    <PageHeader title="Nhật ký quản trị" description="Lịch sử kiểm duyệt, thay đổi dữ liệu và vận hành." />
    <Panel flush>
      <div style={{ padding: '16px 18px' }}><div className="toolbar">
        <input className="field-input" aria-label="UID người thực hiện" placeholder="UID người thực hiện" value={actor} onChange={e => setActor(e.target.value)} />
        <input className="field-input" aria-label="ID đối tượng" placeholder="ID đối tượng" value={target} onChange={e => setTarget(e.target.value)} />
        <input className="field-input" aria-label="Thao tác" placeholder="Mã thao tác" value={action} onChange={e => setAction(e.target.value)} />
        <select className="field-select" aria-label="Loại đối tượng" value={type} onChange={e => setType(e.target.value)}><option value="">Mọi đối tượng</option>{['USER', 'POST', 'COMMENT', 'REPORT', 'FEEDBACK', 'DISEASE', 'CAMPAIGN', 'SCAN', 'JOB'].map(x => <option key={x}>{x}</option>)}</select>
      </div></div>
      <QueryState query={query} empty={rows.length === 0} emptyText="Chưa có sự kiện phù hợp" />
      {rows.length > 0 && <div className="table-wrap"><table className="data-table"><thead><tr><th>Thời gian</th><th>Người thực hiện</th><th>Thao tác</th><th>Đối tượng</th><th>Chi tiết</th></tr></thead><tbody>{rows.map(r => <tr key={r.id}><td className="nowrap">{shortTime(r.createdAt)}</td><td>{r.actorId.slice(0, 14)}</td><td className="cell-title">{r.action}</td><td>{r.targetType}: {r.targetId}</td><td>{r.detail}</td></tr>)}</tbody></table></div>}
      {query.hasNextPage && <div className="table-foot"><span>Đã tải {rows.length} sự kiện</span><Button size="sm" variant="secondary" disabled={query.isFetchingNextPage} onClick={() => query.fetchNextPage()}>Tải thêm</Button></div>}
    </Panel>
  </Page>
}
