import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { AlertTriangle, ArrowRight, BarChart3, Download, FileText, Filter, Flag, FolderTree, Image as ImageIcon, Lightbulb, MessageCircle, ScanLine, Settings, Target } from 'lucide-react'
import { toast } from 'sonner'
import { errorMessage } from '../api/client'
import { datasetService, feedbackService, statsService } from '../api/services'
import { Bars, Button, Modal, Page, PageHeader, Panel, Pill, QueryState, SearchBox, Stat, Steps, Tabs, type Tone } from '../components/ui'
import { useFeedbacks } from '../hooks/useQueries'
import { collectPages, dateTime, downloadCsv, FEEDBACK_REASONS, feedbackPriority, LABEL_NAMES, pct, PRIORITY, shortTime } from '../lib/admin'
import type { AdminFeedback } from '../types'

const STATUS: Record<string, { label: string; tone: Tone }> = { NEW: { label: 'Chưa xem', tone: 'warning' }, REVIEWING: { label: 'Đang đánh giá', tone: 'info' }, RESOLVED: { label: 'Đã xử lý', tone: 'success' } }

export function Feedbacks() {
  const client = useQueryClient()
  const [status, setStatus] = useState('ALL')
  const [reason, setReason] = useState('')
  const [disease, setDisease] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [advanced, setAdvanced] = useState(false)
  const [selected, setSelected] = useState<AdminFeedback | null>(null)
  const [resolution, setResolution] = useState('')
  const [busy, setBusy] = useState(false)
  const filters = { status, reason, q, suggestedDiseaseCode: disease, from: fromDate ? new Date(`${fromDate}T00:00:00`).getTime() : undefined, to: toDate ? new Date(`${toDate}T23:59:59.999`).getTime() : undefined }
  const query = useFeedbacks(filters)
  const stats = useQuery({ queryKey: ['admin-stats'], queryFn: statsService.get })
  const dataset = useQuery({ queryKey: ['dataset-summary'], queryFn: datasetService.summary })
  const f = stats.data?.feedbacks
  const names = Object.fromEntries((dataset.data?.folders ?? []).map(x => [x.label, x.name]))
  const nameOf = (code?: string | null) => (code && (names[code] ?? LABEL_NAMES[code] ?? code)) || 'Chưa xác định'
  const items = query.data?.pages.flatMap(page => page.items) ?? []
  const urgent = f?.byReason?.WRONG_RESULT ?? 0
  const reasons = Object.entries(f?.byReason ?? {}).sort((a, b) => b[1] - a[1]).map(([k, v]) => ({ name: FEEDBACK_REASONS[k] ?? k, value: v }))
  const open = (x: AdminFeedback) => { setSelected(x); setResolution(x.resolution || '') }
  async function save(next: string) {
    if (!selected) return
    setBusy(true)
    try {
      await feedbackService.update(selected.id, next, resolution)
      await Promise.all([client.invalidateQueries({ queryKey: ['feedbacks'] }), client.invalidateQueries({ queryKey: ['admin-stats'] })])
      setSelected(null); toast.success('Đã lưu kết luận và trạng thái')
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  async function exportCsv() {
    setBusy(true)
    try {
      const all = await collectPages(cursor => feedbackService.list(cursor, filters))
      downloadCsv('phan-hoi-ai.csv', ['ID', 'Người dùng', 'Lượt quét', 'Bệnh AI', 'Độ tin cậy', 'Lý do', 'Bệnh đề xuất', 'Mô tả', 'Trạng thái', 'Kết luận', 'Ảnh', 'Thời gian'], all.map(x => [x.id, x.userId, x.scanId, x.scanDiseaseCode, x.scanConfidence, x.reason, x.suggestedDiseaseCode, x.description, x.status, x.resolution, x.imageUrl, dateTime(x.createdAt)]))
      toast.success(`Đã xuất ${all.length} phản hồi`)
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  return <Page art="rice">
    <PageHeader title="Phản hồi chẩn đoán AI" description="Đọc góp ý từ người dùng để theo dõi chất lượng kết quả nhận diện." />
    <div className="grid grid-4">
      <Stat icon={MessageCircle} label="Phản hồi mới hôm nay" value={f?.today ?? '—'} sub={`Tổng ${f?.total ?? 0} phản hồi`} />
      <Stat icon={AlertTriangle} tone="danger" label="Báo kết quả sai" value={urgent} sub="Người dùng cho là AI nhận diện sai" subTone={urgent ? 'bad' : undefined} />
      <Stat icon={Target} label="Độ chính xác theo duyệt" value={pct(dataset.data?.accuracy)} sub={dataset.data?.reviewed ? `Từ ${dataset.data.reviewed} ảnh đã duyệt` : 'Chưa có ảnh được duyệt'} />
      <Stat icon={Flag} tone="danger" label="Chưa xem" value={f?.pending ?? '—'} sub={`${f?.reviewing ?? 0} đang đánh giá, ${f?.resolved ?? 0} đã xử lý`} />
    </div>
    <div className="notice info" style={{ marginBottom: 18 }}><ScanLine size={20} color="#1C6340" style={{ flex: 'none' }} /><div><strong style={{ color: 'var(--ink)' }}>Phản hồi nối với kho ảnh AI</strong>Ảnh bị báo sai được đánh dấu trong Kho ảnh AI để bạn đối chiếu và sửa nhãn cho lần huấn luyện sau. <Link className="link" to="/dataset">Mở kho ảnh <ArrowRight size={14} /></Link></div></div>
    <Panel flush>
      <div style={{ padding: '16px 18px 14px' }}>
        <div style={{ marginBottom: 13 }}><Tabs value={status} onChange={setStatus} items={[{ key: 'ALL', label: `Tất cả (${f?.total ?? 0})` }, { key: 'NEW', label: `Chưa xem (${f?.pending ?? 0})` }, { key: 'REVIEWING', label: `Đang đánh giá (${f?.reviewing ?? 0})` }, { key: 'RESOLVED', label: `Đã xử lý (${f?.resolved ?? 0})` }]} /></div>
        <div className="toolbar">
          <select className="field-select" aria-label="Lý do" value={reason} onChange={e => setReason(e.target.value)}><option value="">Tất cả lý do</option>{Object.entries(FEEDBACK_REASONS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
          <SearchBox placeholder="Tìm theo người gửi hoặc mô tả..." value={search} onChange={setSearch} onSubmit={() => setQ(search)} />
          <Button variant="secondary" onClick={() => setAdvanced(v => !v)}><Filter size={16} />Lọc nâng cao</Button>
          <Button disabled={busy} onClick={exportCsv}><Download size={16} />Xuất danh sách</Button>
        </div>
        {advanced && <div className="toolbar" style={{ marginTop: 11 }}><input className="field-input" aria-label="Bệnh đề xuất" placeholder="Mã bệnh đề xuất" value={disease} onChange={e => setDisease(e.target.value)} /><input className="field-input" type="date" aria-label="Từ ngày" value={fromDate} onChange={e => setFromDate(e.target.value)} /><input className="field-input" type="date" aria-label="Đến ngày" value={toDate} onChange={e => setToDate(e.target.value)} /><Button variant="secondary" onClick={() => { setReason(''); setDisease(''); setFromDate(''); setToDate(''); setSearch(''); setQ('') }}>Xóa lọc</Button></div>}
      </div>
      <QueryState query={query} empty={items.length === 0} emptyText="Không có phản hồi phù hợp" />
      {items.length > 0 && <div className="table-wrap"><table className="data-table"><thead><tr><th>Ảnh quét</th><th>Kết quả AI</th><th>Độ tin cậy</th><th>Lý do phản hồi</th><th>Người gửi</th><th>Ngày gửi</th><th>Mức độ</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{items.map(x => {
        const p = PRIORITY[feedbackPriority(x.reason)], st = STATUS[x.status] ?? { label: x.status, tone: 'neutral' as Tone }
        return <tr key={x.id}>
          <td>{x.imageUrl ? <img className="thumb sm" src={x.imageUrl} alt="" loading="lazy" /> : <div className="thumb sm"><ImageIcon size={17} /></div>}</td>
          <td><div className="cell-title">{nameOf(x.scanDiseaseCode)}</div><div className="cell-sub">{x.engine || 'RiceGuard AI'}</div></td>
          <td>{pct(x.scanConfidence)}</td>
          <td style={{ maxWidth: 260 }}><div className="cell-clip">{FEEDBACK_REASONS[x.reason] ?? x.reason}{x.description ? `: ${x.description}` : ''}</div></td>
          <td className="nowrap">{x.userId.slice(0, 12)}</td>
          <td className="nowrap">{shortTime(x.createdAt)}</td>
          <td><Pill tone={p.tone}>{p.label}</Pill></td>
          <td><Pill tone={st.tone}>{st.label}</Pill></td>
          <td><button className="link" onClick={() => open(x)}>Xem chi tiết <ArrowRight size={15} /></button></td>
        </tr>
      })}</tbody></table></div>}
      {query.hasNextPage && <div className="table-foot"><span>Đã tải {items.length} phản hồi</span><Button size="sm" variant="secondary" disabled={query.isFetchingNextPage} onClick={() => query.fetchNextPage()}>Tải thêm</Button></div>}
    </Panel>
    <div className="grid grid-side-main" style={{ marginTop: 18 }}>
      <Panel icon={BarChart3} title="Lý do phản hồi phổ biến" description="Các lý do người dùng thường phản hồi về kết quả chẩn đoán."><Bars rows={reasons} /></Panel>
      <Panel icon={Settings} title="Quy trình xử lý phản hồi AI" description="Các bước xử lý phản hồi để cải thiện chất lượng hệ thống.">
        <Steps items={[{ title: 'Tiếp nhận', text: 'Ghi nhận phản hồi từ người dùng.', icon: FileText }, { title: 'Đối chiếu ảnh', text: 'Kiểm tra lại ảnh và thông tin liên quan.', icon: ImageIcon }, { title: 'Đánh giá kết quả AI', text: 'Phân tích, đối chiếu với chuyên gia.', icon: BarChart3 }, { title: 'Cập nhật cải tiến', text: 'Sửa nhãn trong kho ảnh để huấn luyện lại.', icon: Lightbulb }]} />
      </Panel>
    </div>
    {selected && <Modal open onOpenChange={o => !o && setSelected(null)} icon={MessageCircle} title="Chi tiết phản hồi" description="Đối chiếu ảnh với kết quả AI rồi ghi kết luận." badge={<Pill tone={(STATUS[selected.status] ?? { tone: 'neutral' as Tone }).tone}>{STATUS[selected.status]?.label ?? selected.status}</Pill>}
      footer={<><Link className="btn btn-secondary" to={`/dataset?scan=${encodeURIComponent(selected.scanId)}`}><FolderTree size={16} />Mở trong kho ảnh</Link><Button variant="outline" disabled={busy} onClick={() => save('REVIEWING')}>Đang đánh giá</Button><Button disabled={busy} onClick={() => save('RESOLVED')}>Đánh dấu đã xử lý</Button></>}>
      <div className="dialog-body"><div className="review-layout">
        <div>{selected.imageUrl ? <a href={selected.imageUrl} target="_blank" rel="noreferrer"><img className="review-photo" src={selected.imageUrl} alt="Ảnh quét" /></a> : <div className="review-photo" />}{!!selected.attachments?.length && <div className="photo-grid" style={{ marginTop: 8 }}>{selected.attachments.map((a, i) => <a key={i} href={a.url} target="_blank" rel="noreferrer"><img src={a.url} alt={`Ảnh đính kèm ${i + 1}`} /></a>)}</div>}</div>
        <div>
          <div className="label-compare"><div className="label-box"><small>AI nhận diện</small><b>{nameOf(selected.scanDiseaseCode)}</b></div><div className="label-box"><small>Người dùng đề xuất</small><b>{selected.suggestedDiseaseCode ? nameOf(selected.suggestedDiseaseCode) : 'Không có'}</b></div></div>
          <div className="info-row" style={{ gridTemplateColumns: '110px 1fr', marginBottom: 9 }}>Độ tin cậy<b>{pct(selected.scanConfidence)}</b></div>
          <div className="info-row" style={{ gridTemplateColumns: '110px 1fr', marginBottom: 9 }}>Lý do<b>{FEEDBACK_REASONS[selected.reason] ?? selected.reason}</b></div>
          <div className="info-row" style={{ gridTemplateColumns: '110px 1fr', marginBottom: 12 }}>Ngày gửi<b>{shortTime(selected.createdAt)}</b></div>
          <div className="quote-box" style={{ marginBottom: 12 }}>{selected.description || 'Người dùng không nhập mô tả.'}</div>
          <label className="form-field">Kết luận của quản trị viên<textarea className="field-area" maxLength={2000} value={resolution} onChange={e => setResolution(e.target.value)} placeholder="Ghi lại nhận định sau khi đối chiếu" /></label>
        </div>
      </div></div>
    </Modal>}
  </Page>
}
