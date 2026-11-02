import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { AlertTriangle, ArrowUpRight, Ban, CheckCircle2, Clock3, CheckCheck, Download, FileCode2, FileText, Filter, HelpCircle, Image as ImageIcon, Info, Leaf, MessageSquare, ShieldAlert, Trash2, User } from 'lucide-react'
import { toast } from 'sonner'
import { errorMessage } from '../api/client'
import { postService, reportService, statsService } from '../api/services'
import { Bars, Button, Modal, Page, PageHeader, Panel, Pill, QueryState, SearchBox, Stat, Steps, Tabs, useConfirm, type Tone } from '../components/ui'
import { useReports } from '../hooks/useQueries'
import { ago, collectPages, dateTime, downloadCsv, PRIORITY, REPORT_REASONS, reportPriority, shortTime } from '../lib/admin'
import type { AdminReport, ReportStatus } from '../types'

const STATUS: Record<string, { label: string; tone: Tone }> = { NEW: { label: 'Chưa xử lý', tone: 'warning' }, RESOLVED: { label: 'Đã xử lý', tone: 'success' }, DISMISSED: { label: 'Đã bác bỏ', tone: 'neutral' } }
const DOTS = ['#C42A2F', '#F08A8A', '#F0B429', '#2E8E56', '#8EB99F']

export function Reports() {
  const client = useQueryClient()
  const [status, setStatus] = useState<ReportStatus | 'ALL'>('NEW')
  const [type, setType] = useState('')
  const [reason, setReason] = useState('')
  const [priority, setPriority] = useState('')
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [picked, setPicked] = useState<string[]>([])
  const [open, setOpen] = useState<AdminReport | null>(null)
  const [busy, setBusy] = useState(false)
  const [advanced, setAdvanced] = useState(false)
  const confirm = useConfirm()
  const filters = { type, reason, priority, q }
  const query = useReports(status, filters)
  const stats = useQuery({ queryKey: ['admin-stats'], queryFn: statsService.get })
  const s = stats.data?.reports
  const reports = query.data?.pages.flatMap(page => page.items) ?? []
  const urgent = reports.filter(r => r.status === 'NEW' && reportPriority(r.reason) === 'HIGH').length
  const reasons = Object.entries(s?.byReason ?? {}).sort((a, b) => b[1] - a[1]).map(([k, v]) => ({ name: REPORT_REASONS[k] ?? k, value: v }))
  const refresh = () => Promise.all([client.invalidateQueries({ queryKey: ['reports'] }), client.invalidateQueries({ queryKey: ['admin-stats'] }), client.invalidateQueries({ queryKey: ['overview'] })])
  async function update(id: string, next: 'RESOLVED' | 'DISMISSED') {
    setBusy(true)
    try { await reportService.update(id, next); toast.success(next === 'RESOLVED' ? 'Đã đánh dấu đã xử lý' : 'Đã bác bỏ báo cáo'); setOpen(null); setPicked(p => p.filter(x => x !== id)); await refresh() }
    catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  function remove(r: AdminReport) {
    confirm.ask({ title: `Xóa ${r.type === 'POST' ? 'bài viết' : 'bình luận'} bị báo cáo?`, description: 'Nội dung sẽ bị xóa vĩnh viễn và mọi báo cáo liên quan được đánh dấu đã xử lý.', confirmText: 'Xóa nội dung', destructive: true, icon: Trash2, onConfirm: async () => {
      try {
        const result = await reportService.removeContent(r.id)
        toast.success(result.contentExisted ? `Đã xóa nội dung và xử lý ${result.reportsResolved} báo cáo` : `Nội dung đã bị xóa trước đó; đã xử lý ${result.reportsResolved} báo cáo`)
        setOpen(null); await refresh()
      } catch (e) { toast.error(errorMessage(e)) }
    } })
  }
  async function bulk(next: 'RESOLVED' | 'DISMISSED') {
    setBusy(true)
    let ok = 0; const failed: string[] = []
    for (const id of picked) { try { await reportService.update(id, next); ok++ } catch { failed.push(id) } }
    await refresh(); setPicked(failed); setBusy(false)
    if (ok) toast.success(`Đã cập nhật ${ok} báo cáo`)
    if (failed.length) toast.error(`${failed.length} báo cáo chưa cập nhật, vẫn được chọn để thử lại`)
  }
  async function exportList() {
    setBusy(true)
    try {
      const all = await collectPages(cursor => reportService.list(status, cursor, filters))
      downloadCsv('bao-cao-vi-pham.csv', ['ID', 'Loại', 'Bài viết', 'Nội dung', 'Người báo cáo', 'Lý do', 'Chi tiết', 'Trạng thái', 'Thời gian'], all.map(r => [r.id, r.type, r.postId, r.targetId, r.reporterName || r.reporterId, r.reason, r.detail, r.status, dateTime(r.createdAt)]))
      toast.success(`Đã xuất ${all.length} báo cáo`)
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  const delta = s ? s.today - s.yesterday : 0
  return <Page art="rice">
    <PageHeader title="Báo cáo vi phạm" description="Kiểm tra nội dung bị báo cáo và đưa ra quyết định kiểm duyệt." />
    <div className="grid grid-4">
      <Stat icon={Clock3} label="Chờ xử lý" value={s?.pending ?? '—'} sub={`${s?.today ?? 0} báo cáo mới hôm nay`} />
      <Stat icon={CheckCircle2} label="Đã xử lý hôm nay" value={s?.resolvedToday ?? '—'} sub="Tính cả báo cáo bị bác bỏ" subTone="up" />
      <Stat icon={AlertTriangle} tone="danger" label="Mức độ ưu tiên cao" value={urgent} sub="Trong danh sách đang xem" subTone={urgent ? 'bad' : undefined} />
      <Stat icon={FileText} label="Tổng báo cáo" value={s?.total ?? '—'} sub={`${delta > 0 ? '+' : ''}${delta} so với hôm qua`} />
    </div>
    <Panel flush className="grid-gap">
      <div style={{ padding: '16px 18px 14px' }}>
        <div className="toolbar" style={{ marginBottom: 14 }}>
          <Tabs value={status} onChange={v => { setStatus(v); setPicked([]) }} items={[{ key: 'NEW', label: 'Chưa xử lý', count: s?.pending }, { key: 'RESOLVED', label: 'Đã xử lý' }, { key: 'DISMISSED', label: 'Đã bác bỏ' }, { key: 'ALL', label: 'Tất cả' }]} />
          {urgent > 0 && <span className="pill pill-danger bare toolbar-push"><AlertTriangle size={14} />{urgent} báo cáo cần ưu tiên</span>}
        </div>
        <div className="toolbar">
          <select className="field-select" aria-label="Loại nội dung" value={type} onChange={e => setType(e.target.value)}><option value="">Tất cả loại nội dung</option><option value="POST">Bài viết</option><option value="COMMENT">Bình luận</option></select>
          <select className="field-select" aria-label="Mức độ" value={priority} onChange={e => setPriority(e.target.value)}><option value="">Tất cả mức độ</option><option value="HIGH">Cao</option><option value="MEDIUM">Trung bình</option><option value="LOW">Thấp</option></select>
          <SearchBox placeholder="Tìm kiếm theo mã, người báo cáo, mô tả..." value={search} onChange={setSearch} onSubmit={() => setQ(search)} />
          <Button variant="secondary" onClick={() => setAdvanced(v => !v)}><Filter size={16} />Lọc nâng cao</Button>
          <Button disabled={busy} onClick={exportList}><Download size={16} />Xuất danh sách</Button>
        </div>
        {advanced && <div className="toolbar" style={{ marginTop: 11 }}><select className="field-select" aria-label="Lý do" value={reason} onChange={e => setReason(e.target.value)}><option value="">Mọi lý do</option>{Object.entries(REPORT_REASONS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select><Button variant="secondary" onClick={() => { setType(''); setReason(''); setPriority(''); setSearch(''); setQ('') }}>Xóa lọc</Button></div>}
        {picked.length > 0 && <div className="toolbar" style={{ marginTop: 11 }}><span className="count-chip">Đã chọn {picked.length}</span><Button size="sm" disabled={busy} onClick={() => bulk('RESOLVED')}>Đánh dấu đã xử lý</Button><Button size="sm" variant="secondary" disabled={busy} onClick={() => bulk('DISMISSED')}>Bác bỏ</Button></div>}
      </div>
      <QueryState query={query} empty={reports.length === 0} emptyText="Không có báo cáo phù hợp" />
      {reports.length > 0 && <div className="table-wrap"><table className="data-table"><thead><tr>
        <th><input className="check" type="checkbox" aria-label="Chọn tất cả" checked={reports.every(r => picked.includes(r.id))} onChange={e => setPicked(e.target.checked ? [...new Set([...picked, ...reports.map(r => r.id)])] : picked.filter(id => !reports.some(r => r.id === id)))} /></th>
        <th>Nội dung</th><th>Lý do</th><th>Người báo cáo</th><th>Ngày gửi</th><th>Mức độ</th><th>Trạng thái</th><th>Thao tác</th>
      </tr></thead><tbody>{reports.map(r => {
        const p = PRIORITY[reportPriority(r.reason)], st = STATUS[r.status] ?? { label: r.status, tone: 'neutral' as Tone }, Icon = r.type === 'POST' ? FileText : MessageSquare
        return <tr key={r.id} className={r.status === 'NEW' && reportPriority(r.reason) === 'HIGH' ? 'row-hot' : undefined}>
          <td><input className="check" type="checkbox" aria-label={`Chọn ${r.id}`} checked={picked.includes(r.id)} onChange={e => setPicked(e.target.checked ? [...picked, r.id] : picked.filter(id => id !== r.id))} /></td>
          <td><div style={{ display: 'flex', gap: 12, alignItems: 'center' }}><Icon size={20} color="#1C6340" /><div><div className="cell-title">{r.type === 'POST' ? 'Bài viết' : 'Bình luận'}</div><div className="cell-sub">#{r.targetId.slice(0, 10)}</div></div></div></td>
          <td><div className="cell-title">{REPORT_REASONS[r.reason] ?? r.reason}</div>{r.detail && <div className="cell-sub cell-clip">{r.detail}</div>}</td>
          <td><div className="cell-title">{r.reporterName || 'Người dùng'}</div><div className="cell-sub">{r.reporterId.slice(0, 12)}</div></td>
          <td className="nowrap"><div>{shortTime(r.createdAt)}</div><div className="cell-sub">{ago(r.createdAt)}</div></td>
          <td><Pill tone={p.tone}>{p.label}</Pill></td>
          <td><Pill tone={st.tone}>{st.label}</Pill></td>
          <td><button className="link" onClick={() => setOpen(r)}>Xem chi tiết</button></td>
        </tr>
      })}</tbody></table></div>}
      {query.hasNextPage && <div className="table-foot"><span>Đã tải {reports.length} báo cáo</span><Button size="sm" variant="secondary" disabled={query.isFetchingNextPage} onClick={() => query.fetchNextPage()}>Tải thêm</Button></div>}
    </Panel>
    <div className="grid grid-2" style={{ marginTop: 18 }}>
      <Panel title="Lý do vi phạm thường gặp" description={`Thống kê dựa trên ${s?.total ?? 0} báo cáo.`}><Bars rows={reasons} dots={DOTS} /></Panel>
      <Panel title="Quy trình xử lý báo cáo" description="Các bước kiểm duyệt nội dung vi phạm trên RiceGuard AI.">
        <Steps items={[{ title: 'Tiếp nhận báo cáo', text: 'Hệ thống ghi nhận và phân loại' }, { title: 'Xem xét nội dung', text: 'Đánh giá mức độ vi phạm' }, { title: 'Đưa ra quyết định', text: 'Giữ, xóa hoặc bác bỏ' }, { title: 'Cập nhật kết quả', text: 'Lưu trạng thái và nhật ký' }]} />
      </Panel>
    </div>
    {open && <ReportDetail report={open} busy={busy} onClose={() => setOpen(null)} onUpdate={update} onRemove={remove} />}
    {confirm.node}
  </Page>
}

function ReportDetail({ report: r, busy, onClose, onUpdate, onRemove }: { report: AdminReport; busy: boolean; onClose: () => void; onUpdate: (id: string, s: 'RESOLVED' | 'DISMISSED') => void; onRemove: (r: AdminReport) => void }) {
  const post = useQuery({ queryKey: ['post', r.postId], queryFn: () => postService.detail(r.postId), retry: false })
  const comments = useQuery({ queryKey: ['comments', r.postId], queryFn: () => postService.comments(r.postId), retry: false, enabled: r.type === 'COMMENT' })
  const flat = comments.data?.flatMap(c => [c, ...c.replies]) ?? []
  const target = flat.find(c => c.id === r.targetId)
  const p = PRIORITY[reportPriority(r.reason)], st = STATUS[r.status] ?? { label: r.status, tone: 'neutral' as Tone }
  const img = post.data?.images?.[0]?.url
  return <Modal open onOpenChange={o => !o && onClose()} icon={FileText} title="Chi tiết báo cáo" description="Xem thông tin chi tiết và đưa ra quyết định xử lý báo cáo này." badge={<Pill tone={p.tone}>Mức độ: {p.label}</Pill>}
    footer={<>
      <Button variant="secondary" disabled={busy || r.status !== 'NEW'} onClick={() => onUpdate(r.id, 'DISMISSED')}><Ban size={16} />Bác bỏ</Button>
      <Button disabled={busy || r.status !== 'NEW'} onClick={() => onUpdate(r.id, 'RESOLVED')}><CheckCheck size={16} />Đánh dấu đã xử lý</Button>
      <Button variant="danger" disabled={busy} onClick={() => onRemove(r)}><Trash2 size={16} />Xóa nội dung</Button>
    </>}>
    <div className="dialog-body"><div className="dialog-card">
      <div className="section-title"><i><Info size={16} /></i>Thông tin báo cáo</div>
      <div className="info-grid">
        <div className="info-row"><FileText size={17} />Loại báo cáo<b>{r.type === 'POST' ? 'Bài viết' : 'Bình luận'}</b></div>
        <div className="info-row"><Clock3 size={17} />Trạng thái<b><Pill tone={st.tone}>{st.label}</Pill></b></div>
        <div className="info-row"><HelpCircle size={17} />Lý do<b>{REPORT_REASONS[r.reason] ?? r.reason}</b></div>
        <div className="info-row"><Clock3 size={17} />Thời gian<b>{shortTime(r.createdAt)}</b></div>
        <div className="info-row"><User size={17} />Người báo cáo<b>{r.reporterName || r.reporterId}</b></div>
        <div className="info-row"><FileCode2 size={17} />Mã nội dung<b>{r.targetId}</b></div>
      </div>
      <div className="divider" />
      <div className="section-title"><FileText size={19} />Mô tả từ người báo cáo</div>
      <div className="quote-box">{r.detail || 'Người báo cáo không nhập mô tả.'}</div>
      <div className="divider" />
      <div className="section-title"><i><Leaf size={16} /></i>Nội dung được báo cáo</div>
      {post.isPending && <div className="quote-box">Đang tải nội dung...</div>}
      {post.isError && <div className="notice"><ShieldAlert size={18} />Nội dung đã bị xóa hoặc không còn tồn tại. Bạn vẫn có thể kết luận báo cáo.</div>}
      {post.data && <div className="embed-card">{img ? <img src={img} alt="" /> : <div className="thumb" style={{ width: 120, height: 104 }}><ImageIcon size={26} /></div>}<div style={{ minWidth: 0 }}>
        <h4>{post.data.title}</h4>
        <p className="cell-clip">{r.type === 'COMMENT' ? (target ? `${target.author?.name}: ${target.text}` : 'Bình luận đã bị xóa.') : post.data.content}</p>
        <Link className="link" to={`/posts/${encodeURIComponent(r.postId)}`}>Mở trang bài viết <ArrowUpRight size={15} /></Link>
      </div></div>}
    </div></div>
  </Modal>
}
