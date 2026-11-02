import { useEffect, useMemo, useState } from 'react'
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { ArrowRight, Ban, CheckCircle2, ClipboardCheck, Download, Flag, Folder, FolderInput, FolderOpen, FolderTree, GitCompareArrows, Images, RefreshCw, RotateCcw, Target, TriangleAlert } from 'lucide-react'
import { toast } from 'sonner'
import { errorMessage } from '../api/client'
import { datasetService, diseaseService } from '../api/services'
import { Button, Modal, Page, PageHeader, Panel, Pill, QueryState, SearchBox, Stat, Tabs, type Tone } from '../components/ui'
import { collectPages, downloadCsv, LABEL_NAMES, pct, shortTime } from '../lib/admin'
import type { DatasetFolder, DatasetImage, ReviewStatus } from '../types'

const REVIEW: Record<ReviewStatus, { label: string; tone: Tone }> = { PENDING: { label: 'Chưa duyệt', tone: 'warning' }, CONFIRMED: { label: 'AI đúng', tone: 'success' }, CORRECTED: { label: 'Đã sửa nhãn', tone: 'info' }, REJECTED: { label: 'Đã loại', tone: 'neutral' } }

function FolderCard({ f, active, onClick }: { f: DatasetFolder; active: boolean; onClick: () => void }) {
  const done = f.confirmed + f.corrected + f.rejected
  const warn = f.label === 'unknown' || f.label === 'pending'
  return <button className={`folder${active ? ' active' : ''}${warn ? ' warn' : ''}${f.total === 0 ? ' muted' : ''}`} onClick={onClick} aria-pressed={active}>
    <div className="folder-top">{active ? <FolderOpen size={24} /> : <Folder size={24} />}<b>{f.total}</b></div>
    <span className="folder-name">{f.name}</span>
    <div className="mini-track" aria-hidden="true"><i style={{ width: f.total ? `${done / f.total * 100}%` : 0 }} /></div>
    <span className="folder-sub">{done}/{f.total} đã duyệt{f.accuracy != null ? `, đúng ${pct(f.accuracy)}` : ''}</span>
  </button>
}

export function Dataset() {
  const client = useQueryClient()
  const [params] = useSearchParams()
  const [label, setLabel] = useState('')
  const [review, setReview] = useState<ReviewStatus | 'ALL'>('ALL')
  const [confidence, setConfidence] = useState('')
  const [reportedOnly, setReportedOnly] = useState(false)
  const [search, setSearch] = useState(params.get('scan') || '')
  const [q, setQ] = useState(params.get('scan') || '')
  const [open, setOpen] = useState<DatasetImage | null>(null)
  const [busy, setBusy] = useState(false)
  const summary = useQuery({ queryKey: ['dataset-summary'], queryFn: datasetService.summary })
  const diseases = useQuery({ queryKey: ['diseases-all'], queryFn: diseaseService.adminList })
  const filters = useMemo(() => ({ label, review, confidence, q, reported: reportedOnly ? true : undefined }), [label, review, confidence, q, reportedOnly])
  const query = useInfiniteQuery({ queryKey: ['dataset', filters], queryFn: ({ pageParam }) => datasetService.list(filters, pageParam), initialPageParam: null as string | null, getNextPageParam: p => p.nextCursor })
  const items = query.data?.pages.flatMap(p => p.items) ?? []
  const s = summary.data
  const names = useMemo(() => Object.fromEntries((s?.folders ?? []).map(f => [f.label, f.name])), [s])
  const nameOf = (l?: string | null) => (l && (names[l] ?? LABEL_NAMES[l] ?? l)) || '—'
  const sum = s?.total ?? 0
  useEffect(() => { if (params.get('scan') && items.length === 1 && !open) setOpen(items[0]) }, [items.length]) // eslint-disable-line react-hooks/exhaustive-deps
  const refresh = () => Promise.all([client.invalidateQueries({ queryKey: ['dataset'] }), client.invalidateQueries({ queryKey: ['dataset-summary'] })])
  async function exportCsv() {
    setBusy(true)
    try {
      const all = await collectPages(cursor => datasetService.list({ review: 'ALL' }, cursor, 60))
      const usable = all.filter(d => d.reviewStatus === 'CONFIRMED' || d.reviewStatus === 'CORRECTED')
      downloadCsv('du-lieu-huan-luyen.csv', ['Ảnh', 'Nhãn đúng', 'Nhãn AI', 'Độ tin cậy', 'Trạng thái duyệt', 'Mã lượt quét'], usable.map(d => [d.imageUrl, d.trueLabel, d.label, d.confidence, d.reviewStatus, d.id]))
      toast.success(`Đã xuất ${usable.length} ảnh đã duyệt`)
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  async function backfill() {
    setBusy(true)
    try { const r = await datasetService.backfill(); await refresh(); toast.success(r.count ? `Đã đưa ${r.count} lượt quét cũ vào kho` : 'Mọi lượt quét đã có trong kho') }
    catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  return <Page art="landscape">
    <PageHeader title="Kho ảnh AI" description="Ảnh người dùng tải lên được xếp vào thư mục theo bệnh AI dự đoán để đối chiếu và cải tiến mô hình." actions={<Button variant="secondary" disabled={busy} onClick={backfill}><RefreshCw size={16} />Đồng bộ lượt quét cũ</Button>} />
    <div className="grid grid-4">
      <Stat icon={Images} label="Tổng ảnh trong kho" value={sum.toLocaleString('vi-VN')} sub={`${(s?.folders.find(f => f.label === 'pending')?.total) ?? 0} ảnh chờ phân tích`} />
      <Stat icon={ClipboardCheck} label="Đã duyệt" value={s?.reviewed ?? '—'} sub={sum ? `${Math.round((s?.reviewed ?? 0) / sum * 100)}% tổng số ảnh` : 'Chưa có ảnh'} />
      <Stat icon={Target} label="Độ chính xác AI" value={pct(s?.accuracy)} sub={s?.accuracy == null ? 'Cần duyệt ảnh để tính' : `${s.confirmed} đúng, ${s.corrected} sai`} subTone="up" />
      <Stat icon={TriangleAlert} tone="warning" label="Không xác định" value={s?.folders.find(f => f.label === 'unknown')?.total ?? '—'} sub="Nguồn ảnh cần gán nhãn mới" onClick={() => setLabel('unknown')} />
    </div>
    <Panel icon={FolderTree} title="Thư mục theo dự đoán của AI" description="Chọn một thư mục để xem ảnh. Trùng với thư mục dataset trên Cloudinary." action={label ? <button className="panel-link" onClick={() => setLabel('')}>Xem tất cả</button> : undefined}>
      <QueryState query={summary} />
      {s && <div className="folder-grid">{s.folders.map(f => <FolderCard key={f.label} f={f} active={label === f.label} onClick={() => setLabel(label === f.label ? '' : f.label)} />)}</div>}
    </Panel>
    <div className="grid grid-main-side" style={{ marginTop: 18 }}>
      <Panel flush>
        <div style={{ padding: '16px 18px' }}>
          <div className="toolbar" style={{ marginBottom: 12 }}><Tabs value={review} onChange={setReview} items={[{ key: 'ALL', label: 'Tất cả' }, { key: 'PENDING', label: 'Chưa duyệt' }, { key: 'CONFIRMED', label: 'AI đúng' }, { key: 'CORRECTED', label: 'Đã sửa nhãn' }, { key: 'REJECTED', label: 'Đã loại' }]} /></div>
          <div className="toolbar">
            <select className="field-select" aria-label="Độ tin cậy" value={confidence} onChange={e => setConfidence(e.target.value)}><option value="">Mọi độ tin cậy</option><option value="LOW">Thấp (dưới 60%)</option><option value="MID">Trung bình (60–85%)</option><option value="HIGH">Cao (từ 85%)</option></select>
            <SearchBox placeholder="Tìm theo mã, người dùng, khu vực..." value={search} onChange={setSearch} onSubmit={() => setQ(search)} />
            <label className="check-row"><input className="check" type="checkbox" checked={reportedOnly} onChange={e => setReportedOnly(e.target.checked)} />Bị báo sai</label>
            <Button disabled={busy} onClick={exportCsv}><Download size={16} />Xuất ảnh đã duyệt</Button>
          </div>
        </div>
        <QueryState query={query} empty={items.length === 0} emptyText={label ? `Thư mục ${nameOf(label)} chưa có ảnh phù hợp` : 'Chưa có ảnh trong kho'} />
        {items.length > 0 && <div className="image-grid">{items.map(d => {
          const r = REVIEW[d.reviewStatus] ?? REVIEW.PENDING
          return <button className="scan-card" key={d.id} onClick={() => setOpen(d)}>
            <div className="scan-photo"><img src={d.thumbUrl || d.imageUrl} alt={`Ảnh ${nameOf(d.label)}`} loading="lazy" />{d.status !== 'PENDING' && <span className="conf">{pct(d.confidence)}</span>}{d.reportedWrong && <span className="flag" title="Người dùng báo kết quả sai"><Flag size={14} /></span>}</div>
            <div className="scan-body"><strong>{nameOf(d.label)}</strong><small>{shortTime(d.createdAt)}{d.province ? `, ${d.province}` : ''}</small><Pill tone={r.tone}>{r.label}</Pill></div>
          </button>
        })}</div>}
        {query.hasNextPage && <div className="table-foot"><span>Đã tải {items.length} ảnh</span><Button size="sm" variant="secondary" disabled={query.isFetchingNextPage} onClick={() => query.fetchNextPage()}>Tải thêm</Button></div>}
      </Panel>
      <div className="stack">
        <Panel icon={GitCompareArrows} title="AI hay nhầm" description="Những cặp nhãn bị sửa nhiều nhất khi duyệt.">
          {s?.confusion.length ? s.confusion.slice(0, 6).map(c => <div className="confusion-row" key={`${c.ai}-${c.actual}`}><span>{c.aiName}</span><ArrowRight size={15} /><span>{c.actualName}</span><b>{c.count}</b></div>) : <div className="empty-chart" style={{ minHeight: 90 }}>Chưa có ảnh nào bị sửa nhãn</div>}
        </Panel>
        <Panel title="Quy trình cải tiến mô hình" description="Từ ảnh người dùng đến bản best.pt mới.">
          <div className="stack" style={{ gap: 10 }}>{[['Tải ảnh', 'Ảnh vào thư mục theo dự đoán của AI.'], ['Duyệt nhãn', 'Xác nhận đúng, sửa nhãn hoặc loại ảnh.'], ['Xuất dữ liệu', 'Tải danh sách ảnh đã duyệt kèm nhãn đúng.'], ['Huấn luyện lại', 'Dùng dữ liệu mới để cải thiện best.pt.']].map(([t, d]) => <div className="cond" style={{ marginTop: 0 }} key={t}><div><b>{t}</b><div className="cell-sub">{d}</div></div></div>)}</div>
        </Panel>
      </div>
    </div>
    {open && <ReviewDialog image={open} folders={[...(s?.folders ?? [])]} diseases={diseases.data ?? []} nameOf={nameOf} onClose={() => setOpen(null)} onChanged={async next => { setOpen(next); await refresh() }} />}
  </Page>
}

function ReviewDialog({ image: d, folders, diseases, nameOf, onClose, onChanged }: { image: DatasetImage; folders: DatasetFolder[]; diseases: { id: string; name: string }[]; nameOf: (l?: string | null) => string; onClose: () => void; onChanged: (d: DatasetImage | null) => Promise<void> }) {
  const [trueLabel, setTrueLabel] = useState(d.trueLabel || d.suggestedLabel || '')
  const [note, setNote] = useState(d.reviewNote || '')
  const [busy, setBusy] = useState(false)
  const options = useMemo(() => {
    const m = new Map<string, string>()
    folders.filter(f => f.label !== 'pending').forEach(f => m.set(f.label, f.name))
    diseases.forEach(x => m.set(x.id, x.name))
    return [...m.entries()]
  }, [folders, diseases])
  const r = REVIEW[d.reviewStatus] ?? REVIEW.PENDING
  async function act(action: 'CONFIRM' | 'CORRECT' | 'REJECT' | 'RESET') {
    if (action === 'CORRECT' && !trueLabel) { toast.error('Chọn nhãn đúng trước khi lưu'); return }
    if (action === 'CORRECT' && trueLabel === d.label) { toast.error('Nhãn đúng trùng với AI, hãy chọn "AI đúng"'); return }
    setBusy(true)
    try { await onChanged(await datasetService.review(d.id, action, action === 'CORRECT' ? trueLabel : undefined, note)); toast.success({ CONFIRM: 'Đã xác nhận AI đúng', CORRECT: 'Đã sửa nhãn', REJECT: 'Đã loại ảnh khỏi dữ liệu huấn luyện', RESET: 'Đã đưa ảnh về chưa duyệt' }[action]) }
    catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  async function rediagnose() {
    setBusy(true)
    try { await onChanged(await datasetService.rediagnose(d.id)); toast.success('Đã chẩn đoán lại') }
    catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  const canRediagnose = d.status !== 'DIAGNOSED'
  return <Modal open onOpenChange={o => !o && onClose()} icon={FolderInput} title="Duyệt ảnh" description="So sánh ảnh với dự đoán của AI rồi ghi nhãn đúng." badge={<Pill tone={r.tone}>{r.label}</Pill>}
    footer={<>
      {canRediagnose && <Button variant="secondary" disabled={busy} onClick={rediagnose}><RefreshCw size={16} />Chẩn đoán lại</Button>}
      {d.reviewStatus !== 'PENDING' && <Button variant="secondary" disabled={busy} onClick={() => act('RESET')}><RotateCcw size={16} />Duyệt lại</Button>}
      <Button variant="danger" disabled={busy} onClick={() => act('REJECT')}><Ban size={16} />Loại ảnh</Button>
      <Button variant="outline" disabled={busy} onClick={() => act('CORRECT')}>Lưu nhãn đúng</Button>
      <Button disabled={busy || d.label === 'pending' || d.label === 'unknown'} onClick={() => act('CONFIRM')}><CheckCircle2 size={16} />AI đúng</Button>
    </>}>
    <div className="dialog-body"><div className="review-layout">
      <a href={d.imageUrl} target="_blank" rel="noreferrer"><img className="review-photo" src={d.imageUrl} alt="Ảnh người dùng quét" /></a>
      <div>
        <div className="label-compare">
          <div className="label-box"><small>AI dự đoán ({d.status === 'PENDING' ? 'chưa có' : pct(d.confidence)})</small><b>{nameOf(d.label)}</b></div>
          <div className="label-box"><small>Nhãn đúng</small><b>{d.trueLabel ? nameOf(d.trueLabel) : 'Chưa có'}</b></div>
        </div>
        {d.reportedWrong && <div className="notice danger" style={{ marginBottom: 12 }}><Flag size={17} style={{ flex: 'none' }} />Người dùng báo kết quả sai{d.suggestedLabel ? `, đề xuất: ${nameOf(d.suggestedLabel)}` : ''}.</div>}
        <div className="info-row" style={{ gridTemplateColumns: '100px 1fr', marginBottom: 8 }}>Khu vực<b>{[d.district, d.province].filter(Boolean).join(', ') || 'Chưa có'}</b></div>
        <div className="info-row" style={{ gridTemplateColumns: '100px 1fr', marginBottom: 8 }}>Giai đoạn<b>{d.stage || 'Chưa có'}</b></div>
        <div className="info-row" style={{ gridTemplateColumns: '100px 1fr', marginBottom: 8 }}>Mô hình<b>{d.engine || 'Chưa có'}</b></div>
        <div className="info-row" style={{ gridTemplateColumns: '100px 1fr', marginBottom: 14 }}>Ngày quét<b>{shortTime(d.createdAt)}</b></div>
        <label className="form-field" style={{ marginBottom: 11 }}>Nhãn đúng nếu AI sai<select className="field-select" value={trueLabel} onChange={e => setTrueLabel(e.target.value)}><option value="">Chọn bệnh</option>{options.map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
        <label className="form-field">Ghi chú<textarea className="field-area" style={{ minHeight: 70 }} maxLength={500} value={note} onChange={e => setNote(e.target.value)} placeholder="Ví dụ: ảnh mờ, nhiều bệnh trên một lá" /></label>
      </div>
    </div></div>
  </Modal>
}
