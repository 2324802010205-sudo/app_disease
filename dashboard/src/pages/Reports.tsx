import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { errorMessage } from '../api/client'
import { reportService, statsService } from '../api/services'
import { useReports } from '../hooks/useQueries'
import { collectPages, dateTime, downloadCsv } from '../lib/admin'
import type { AdminReport, ReportStatus } from '../types'

const statuses: Record<string, string> = { ALL: 'Tất cả', NEW: 'Chờ xử lý', RESOLVED: 'Đã xử lý', DISMISSED: 'Đã bác bỏ' }
export function Reports() {
  const client = useQueryClient()
  const [status, setStatus] = useState<ReportStatus | 'ALL'>('NEW')
  const [type, setType] = useState('')
  const [reason, setReason] = useState('')
  const [priority, setPriority] = useState('')
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const filters = { type, reason, priority, q }
  const query = useReports(status, filters)
  const stats = useQuery({ queryKey: ['admin-stats'], queryFn: statsService.get })
  const reports = query.data?.pages.flatMap(page => page.items) ?? []
  const refresh = async () => { await Promise.all([client.invalidateQueries({ queryKey: ['reports'] }), client.invalidateQueries({ queryKey: ['admin-stats'] }), client.invalidateQueries({ queryKey: ['overview'] })]) }
  async function update(id: string, next: 'RESOLVED' | 'DISMISSED' | 'REMOVE') {
    setBusy(true)
    try {
      if (next === 'REMOVE') {
        const result = await reportService.removeContent(id)
        toast.success(result.contentExisted ? `Đã xóa nội dung và xử lý ${result.reportsResolved} báo cáo` : `Nội dung đã bị xóa; đã xử lý ${result.reportsResolved} báo cáo`)
      } else { await reportService.update(id, next); toast.success('Đã cập nhật báo cáo') }
      setSelected(ids => ids.filter(item => item !== id)); await refresh()
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  async function bulk(next: 'RESOLVED' | 'DISMISSED') {
    setBusy(true)
    let success = 0; const failures: string[] = []
    for (const id of selected) {
      try { await reportService.update(id, next); success++ } catch { failures.push(id) }
    }
    await refresh(); setSelected(failures); setBusy(false)
    if (success) toast.success(`Đã cập nhật ${success} báo cáo`)
    if (failures.length) toast.error(`${failures.length} báo cáo chưa cập nhật; vẫn được chọn để thử lại`)
  }
  async function exportList() {
    setBusy(true)
    try {
      const all = await collectPages((cursor) => reportService.list(status, cursor, filters))
      downloadCsv('bao-cao-vi-pham.csv', ['ID','Loại','Bài viết','Nội dung','Người báo cáo','Lý do','Chi tiết','Trạng thái','Thời gian'],
        all.map(r => [r.id,r.type,r.postId,r.targetId,r.reporterId,r.reason,r.detail,r.status,dateTime(r.createdAt)]))
      toast.success(`Đã xuất ${all.length} báo cáo`)
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  return <main className="admin-page"><h1>Báo cáo vi phạm</h1><p>Danh sách và kết quả xử lý từ backend</p>
    {stats.data && <div className="admin-stats"><div className="admin-stat">Chờ xử lý<strong>{stats.data.reports.pending}</strong></div><div className="admin-stat">Đã xử lý hôm nay<strong>{stats.data.reports.resolvedToday}</strong></div><div className="admin-stat">Tổng báo cáo<strong>{stats.data.reports.total}</strong></div><div className="admin-stat">Hôm nay / hôm qua<strong>{stats.data.reports.today} / {stats.data.reports.yesterday}</strong></div></div>}
    {stats.isError && <div className="admin-error">Không tải được thống kê: {errorMessage(stats.error)}</div>}
    <div className="admin-toolbar"><select aria-label="Trạng thái" value={status} onChange={e => {setStatus(e.target.value as ReportStatus | 'ALL');setSelected([])}}>{Object.entries(statuses).map(([key,label]) => <option key={key} value={key}>{label}</option>)}</select>
      <select aria-label="Loại nội dung" value={type} onChange={e => setType(e.target.value)}><option value="">Mọi loại</option><option value="POST">Bài viết</option><option value="COMMENT">Bình luận</option></select>
      <select aria-label="Lý do" value={reason} onChange={e => setReason(e.target.value)}><option value="">Mọi lý do</option>{['SPAM','MISLEADING','OFFENSIVE','OTHER'].map(x => <option key={x}>{x}</option>)}</select>
      <select aria-label="Mức độ ưu tiên" value={priority} onChange={e => setPriority(e.target.value)}><option value="">Mọi mức độ</option><option value="HIGH">Cao</option><option value="MEDIUM">Trung bình</option><option value="LOW">Thấp</option></select>
      <form onSubmit={e => {e.preventDefault();setQ(search)}}><input aria-label="Tìm kiếm" placeholder="ID, người báo cáo, nội dung..." value={search} onChange={e => setSearch(e.target.value)}/><button>Tìm</button></form>
      <button onClick={() => {setType('');setReason('');setPriority('');setSearch('');setQ('');setStatus('NEW')}}>Xóa lọc</button><button onClick={exportList} disabled={busy}>Xuất CSV</button>
    </div>
    {selected.length > 0 && <div className="admin-toolbar"><span>Đã chọn {selected.length}</span><button disabled={busy} onClick={() => bulk('RESOLVED')}>Xử lý hàng loạt</button><button disabled={busy} onClick={() => bulk('DISMISSED')}>Bác bỏ hàng loạt</button></div>}
    {query.isPending && <div className="admin-empty">Đang tải báo cáo...</div>}
    {query.isError && <div className="admin-error">{errorMessage(query.error)} <button onClick={() => query.refetch()}>Thử lại</button></div>}
    {query.isSuccess && reports.length === 0 && <div className="admin-empty">Không có báo cáo phù hợp</div>}
    {reports.length > 0 && <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th><input type="checkbox" aria-label="Chọn tất cả trên trang" checked={reports.every(r => selected.includes(r.id))} onChange={e => setSelected(e.target.checked ? [...new Set([...selected, ...reports.map(r => r.id)])] : selected.filter(id => !reports.some(r => r.id === id)))}/></th><th>Nội dung</th><th>Lý do</th><th>Người báo cáo</th><th>Thời gian</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{reports.map((r: AdminReport) => <tr key={r.id}><td><input type="checkbox" aria-label={`Chọn ${r.id}`} checked={selected.includes(r.id)} onChange={e => setSelected(e.target.checked ? [...selected,r.id] : selected.filter(id => id !== r.id))}/></td><td>{r.type === 'POST' ? 'Bài viết' : 'Bình luận'}<br/><Link className="admin-link" to={`/posts/${encodeURIComponent(r.postId)}`}>{r.targetId}</Link><div className="admin-muted">{r.id}</div></td><td>{r.reason}<div className="admin-muted">{r.detail}</div></td><td>{r.reporterId}</td><td>{dateTime(r.createdAt)}</td><td>{statuses[r.status] ?? r.status}</td><td><div className="admin-actions"><button disabled={busy} onClick={() => update(r.id,'RESOLVED')}>Đã xử lý</button><button disabled={busy} onClick={() => update(r.id,'DISMISSED')}>Bác bỏ</button><button disabled={busy} onClick={() => {if (confirm('Xóa nội dung được báo cáo?')) update(r.id,'REMOVE')}}>Xóa nội dung</button></div></td></tr>)}</tbody></table></div>}
    {query.hasNextPage && <div className="admin-pagination"><button disabled={query.isFetchingNextPage} onClick={() => query.fetchNextPage()}>{query.isFetchingNextPage ? 'Đang tải...' : 'Tải tiếp báo cáo'}</button></div>}
  </main>
}
