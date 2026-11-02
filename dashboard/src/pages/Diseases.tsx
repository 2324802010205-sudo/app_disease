import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, BarChart3, BrainCircuit, CloudUpload, Cross, Eye, EyeOff, FilePenLine, Image as ImageIcon, Info, Leaf, Lightbulb, Microscope, Plus, ShieldCheck, Thermometer } from 'lucide-react'
import { toast } from 'sonner'
import { errorMessage } from '../api/client'
import { diseaseService } from '../api/services'
import { Button, Modal, Page, PageHeader, Panel, Pill, QueryState, SearchBox } from '../components/ui'
import { useDisease, useDiseases } from '../hooks/useQueries'
import { CAUSES, PARTS } from '../lib/admin'
import type { Disease, DiseaseDraft } from '../types'

const empty: DiseaseDraft = { name: '', scientificName: '', description: '', part: 'LEAF', cause: 'FUNGUS', common: false, symptoms: [], conditions: [], prevention: [], treatment: [] }
const asDraft = (d: Disease): DiseaseDraft => ({ name: d.name, scientificName: d.scientificName || '', description: d.description, part: d.part, cause: d.cause, common: d.common, symptoms: d.symptoms || [], conditions: d.conditions || [], prevention: d.prevention || [], treatment: d.treatment || [] })
const SECTIONS = [
  { key: 'symptoms', title: 'Triệu chứng', icon: Leaf, cls: '', art: 'sec-symptom' },
  { key: 'conditions', title: 'Điều kiện phát sinh', icon: Thermometer, cls: 'warm', art: 'sec-conditions' },
  { key: 'prevention', title: 'Phòng ngừa', icon: ShieldCheck, cls: 'blue', art: 'sec-prevention' },
  { key: 'treatment', title: 'Điều trị', icon: Cross, cls: 'red', art: 'sec-treatment' },
] as const
const FALLBACK: Record<string, string> = { 'dao-on-la': '/rice-blast-leaf.jpg', 'bac-la': '/rice-blight-leaf.jpg', 'dom-nau': '/rice-disease-leaf.jpg' }
const cover = (d: Disease) => d.imageUrl || FALLBACK[d.id]

function DiseaseForm({ value, onChange, busy }: { value: DiseaseDraft; onChange: (next: DiseaseDraft) => void; busy: boolean }) {
  const update = (patch: Partial<DiseaseDraft>) => onChange({ ...value, ...patch })
  return <div className="form-grid">
    <label className="form-field">Tên bệnh<input className="field-input" value={value.name} onChange={e => update({ name: e.target.value })} disabled={busy} /></label>
    <label className="form-field">Tên khoa học<input className="field-input" value={value.scientificName} onChange={e => update({ scientificName: e.target.value })} disabled={busy} /></label>
    <label className="form-field">Bộ phận<select className="field-select" value={value.part} onChange={e => update({ part: e.target.value })} disabled={busy}>{Object.entries(PARTS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
    <label className="form-field">Tác nhân<select className="field-select" value={value.cause} onChange={e => update({ cause: e.target.value })} disabled={busy}>{Object.entries(CAUSES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
    <label className="check-row form-field full" style={{ display: 'flex' }}><input className="check" type="checkbox" checked={value.common} onChange={e => update({ common: e.target.checked })} disabled={busy} />Bệnh thường gặp</label>
    <label className="form-field full">Mô tả<textarea className="field-area" value={value.description} onChange={e => update({ description: e.target.value })} disabled={busy} /></label>
    {SECTIONS.map(s => <label className="form-field" key={s.key}>{s.title} (mỗi dòng một mục)<textarea className="field-area" value={value[s.key].join('\n')} onChange={e => update({ [s.key]: e.target.value.split('\n') })} onBlur={e => update({ [s.key]: e.target.value.split('\n').map(x => x.trim()).filter(Boolean) })} disabled={busy} /></label>)}
  </div>
}

export function Diseases() {
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [part, setPart] = useState('')
  const [cause, setCause] = useState('')
  const [visibility, setVisibility] = useState('ALL')
  const [ai, setAi] = useState('')
  const query = useDiseases({})
  const rows = query.data?.filter(d => (!q || `${d.name} ${d.description} ${d.id}`.toLowerCase().includes(q.toLowerCase())) && (!part || d.part === part) && (!cause || d.cause === cause) && (visibility === 'ALL' || (visibility === 'HIDDEN') === !!d.hidden) && (!ai || (ai === 'YES') === !!d.aiDetectable)) ?? []
  return <Page art="rice">
    <PageHeader title="Thư viện bệnh" description="Thông tin chuyên môn trong thư viện bệnh RiceGuard AI." actions={<Link className="btn btn-primary" to="/diseases/new"><Plus size={17} />Thêm bệnh</Link>} />
    <Panel className="toolbar-card"><div className="toolbar">
      <SearchBox placeholder="Tìm theo tên, mã hoặc mô tả" value={search} onChange={setSearch} onSubmit={() => setQ(search)} />
      <select className="field-select" aria-label="Bộ phận" value={part} onChange={e => setPart(e.target.value)}><option value="">Mọi bộ phận</option>{Object.entries(PARTS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
      <select className="field-select" aria-label="Tác nhân" value={cause} onChange={e => setCause(e.target.value)}><option value="">Mọi tác nhân</option>{Object.entries(CAUSES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
      <select className="field-select" aria-label="AI nhận diện" value={ai} onChange={e => setAi(e.target.value)}><option value="">AI và không AI</option><option value="YES">AI nhận diện được</option><option value="NO">Chỉ để tra cứu</option></select>
      <select className="field-select" aria-label="Hiển thị" value={visibility} onChange={e => setVisibility(e.target.value)}><option value="ALL">Mọi trạng thái</option><option value="VISIBLE">Đang hiện</option><option value="HIDDEN">Đã ẩn</option></select>
    </div></Panel>
    <QueryState query={query} empty={rows.length === 0} emptyText="Không có bệnh phù hợp" />
    <div className="disease-grid">{rows.map(d => <Link to={`/diseases/${encodeURIComponent(d.id)}`} className="panel disease-card" key={d.id}>
      <div className="disease-cover">{cover(d) ? <img src={cover(d)!} alt={d.name} loading="lazy" /> : <Leaf size={40} />}<div className="cover-pills"><span className="tag"><Leaf size={13} />{PARTS[d.part] ?? d.part}</span><span className="tag blue">{CAUSES[d.cause] ?? d.cause}</span></div></div>
      <div className="disease-card-body"><h3>{d.name}</h3>{d.scientificName && <em>{d.scientificName}</em>}<p>{d.description}</p><div className="disease-card-foot">{d.aiDetectable && <Pill tone="success" bare><BrainCircuit size={13} />AI nhận diện</Pill>}{d.common && <Pill tone="warning" bare>Thường gặp</Pill>}{d.hidden && <Pill bare>Đã ẩn</Pill>}</div></div>
    </Link>)}</div>
  </Page>
}
export function DiseaseCreate() {
  const navigate = useNavigate(), client = useQueryClient()
  const [id, setId] = useState(''), [draft, setDraft] = useState<DiseaseDraft>(empty), [busy, setBusy] = useState(false)
  async function save() { setBusy(true); try { const d = await diseaseService.create(id, draft); await client.invalidateQueries({ queryKey: ['diseases'] }); toast.success('Đã thêm bệnh'); navigate(`/diseases/${encodeURIComponent(d.id)}`) } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) } }
  return <Page art="rice"><PageHeader title="Thêm bệnh" description="Tạo mục mới trong thư viện bệnh." actions={<Link className="back-chip" to="/diseases"><ArrowLeft size={16} />Thư viện bệnh</Link>} />
    <Panel><label className="form-field" style={{ marginBottom: 14 }}>Mã bệnh<input className="field-input" value={id} onChange={e => setId(e.target.value.toLowerCase())} placeholder="vi-du-benh-lua" /></label><DiseaseForm value={draft} onChange={setDraft} busy={busy} /><div style={{ marginTop: 16 }}><Button disabled={busy || !id || !draft.name.trim() || !draft.description.trim()} onClick={save}>Lưu bệnh</Button></div></Panel>
  </Page>
}
export function DiseaseDetail() {
  const { id = '' } = useParams(), client = useQueryClient()
  const query = useDisease(id)
  const [draft, setDraft] = useState<DiseaseDraft>(empty), [busy, setBusy] = useState(false), [edit, setEdit] = useState(false)
  const file = useRef<HTMLInputElement>(null)
  const d = query.data
  useEffect(() => { if (d) setDraft(asDraft(d)) }, [d])
  const refresh = () => Promise.all([client.invalidateQueries({ queryKey: ['disease', id] }), client.invalidateQueries({ queryKey: ['diseases'] })])
  async function save() { setBusy(true); try { await diseaseService.update(id, draft); await refresh(); setEdit(false); toast.success('Đã lưu bệnh') } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) } }
  async function toggle(hidden: boolean) { setBusy(true); try { await diseaseService.visibility(id, hidden); await refresh(); toast.success(hidden ? 'Đã ẩn bệnh' : 'Đã bỏ ẩn bệnh') } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) } }
  async function upload(f?: File) { if (!f || !d) return; setBusy(true); try { await diseaseService.uploadImage(id, f); await refresh(); toast.success('Đã thay ảnh minh họa') } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false); if (file.current) file.current.value = '' } }
  return <Page art="landscape">
    <PageHeader title={d?.name ?? 'Thư viện bệnh'} description="Thông tin chuyên môn trong thư viện bệnh RiceGuard AI." actions={<Link className="back-chip" to="/diseases"><ArrowLeft size={16} />Thư viện bệnh</Link>} />
    {!d && <Panel><QueryState query={query} /></Panel>}
    {d && <div className="detail-grid">
      <div className="stack" style={{ gap: 12 }}>
        <section className="panel disease-hero">
          <div className="disease-photo">{cover(d) ? <img src={cover(d)!} alt={d.name} /> : <Leaf size={56} />}<div className="cover-pills"><span className="tag"><Leaf size={14} />{PARTS[d.part] ?? d.part}</span><span className="tag blue"><Microscope size={14} />{CAUSES[d.cause] ?? d.cause}</span>{d.common && <span className="tag amber"><BarChart3 size={14} />Thường gặp</span>}</div></div>
          <h2>{d.name}</h2>{d.scientificName && <em>{d.scientificName}</em>}<p>{d.description}</p>
        </section>
        {SECTIONS.map(s => <section className="panel sec-card" key={s.key}><div className={`sec-tile ${s.cls}`}><s.icon size={30} /></div><div><h3>{s.title}</h3>{d[s.key]?.length ? <ul>{d[s.key].map((x, i) => <li key={i}>{x}</li>)}</ul> : <p className="cell-sub">Chưa có nội dung</p>}</div><img src={`/art/${s.art}.png`} alt="" /></section>)}
      </div>
      <div className="stack">
        <Panel icon={ImageIcon} title="Ảnh minh họa">
          <p className="cell-sub" style={{ fontSize: 13.5 }}>Chọn ảnh mới để thay ảnh đang hiển thị trong thư viện bệnh.</p>
          <div className="preview-img">{cover(d) ? <img src={cover(d)!} alt="" /> : <ImageIcon size={32} />}</div>
          <input ref={file} type="file" accept="image/*" hidden onChange={e => upload(e.target.files?.[0])} />
          <Button block disabled={busy} onClick={() => file.current?.click()}><CloudUpload size={18} />Thay ảnh minh họa</Button>
        </Panel>
        <Panel icon={Info} title="Thông tin nhanh">
          <div className="facts">
            <div className="mod-row"><BarChart3 size={18} />Mức độ phổ biến<Pill tone={d.common ? 'danger' : 'success'}>{d.common ? 'Cao' : 'Thấp'}</Pill></div>
            <div className="mod-row"><Microscope size={18} />Nhóm bệnh<Pill tone="info">{CAUSES[d.cause] ?? d.cause}</Pill></div>
            <div className="mod-row"><Leaf size={18} />Bộ phận ảnh hưởng<Pill tone="success">{PARTS[d.part] ?? d.part}</Pill></div>
            <div className="mod-row"><BrainCircuit size={18} />Mô hình AI<Pill tone={d.aiDetectable ? 'success' : 'neutral'}>{d.aiDetectable ? 'Nhận diện được' : 'Chưa hỗ trợ'}</Pill></div>
            <div className="mod-row"><Eye size={18} />Hiển thị<Pill tone={d.hidden ? 'neutral' : 'success'}>{d.hidden ? 'Đã ẩn' : 'Đang hiện'}</Pill></div>
            <div className="mod-row"><FilePenLine size={18} />Nguồn nội dung<b>{d.adminEdited ? 'Admin biên tập' : 'Dữ liệu gốc'}</b></div>
          </div>
          <div className="divider" />
          <div className="stack" style={{ gap: 10 }}>
            <Button block variant="outline" disabled={busy} onClick={() => setEdit(true)}><FilePenLine size={17} />Sửa nội dung</Button>
            <Button block variant="secondary" disabled={busy} onClick={() => toggle(!d.hidden)}>{d.hidden ? <Eye size={17} /> : <EyeOff size={17} />}{d.hidden ? 'Bỏ ẩn bệnh' : 'Ẩn bệnh'}</Button>
          </div>
        </Panel>
        <div className="hint-card"><h3><i><Lightbulb size={20} /></i>Khuyến nghị</h3><p>Thường xuyên thăm đồng, theo dõi tình hình phát sinh bệnh. Áp dụng biện pháp canh tác phù hợp và tham khảo ý kiến cán bộ chuyên môn trước khi xử lý.</p></div>
      </div>
    </div>}
    {d && <Modal open={edit} onOpenChange={setEdit} icon={FilePenLine} title="Sửa nội dung bệnh" description={`Sau khi lưu, đồng bộ dữ liệu gốc sẽ không ghi đè ${d.name}.`} footer={<><Button variant="secondary" disabled={busy} onClick={() => setEdit(false)}>Hủy</Button><Button disabled={busy || !draft.name.trim() || !draft.description.trim()} onClick={save}>Lưu nội dung</Button></>}><div className="dialog-body"><DiseaseForm value={draft} onChange={setDraft} busy={busy} /></div></Modal>}
  </Page>
}
