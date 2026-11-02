import { useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight, BarChart3, Download, FileText, Lightbulb, Map as MapIcon, MapPin, Minus, PieChart as PieIcon, Plus, ShieldAlert, Sprout, TriangleAlert, Leaf, Calendar } from 'lucide-react'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { toast } from 'sonner'
import { errorMessage } from '../api/client'
import { riskService } from '../api/services'
import { VN_BOUNDS, VN_PROVINCES } from '../data/vnMap'
import { Button, Donut, EmptyState, Page, PageHeader, Panel, Pager, Pill, QueryState, SearchBox, Stat } from '../components/ui'
import { useDiseases, useRiskAreas } from '../hooks/useQueries'
import { dateTime, downloadCsv, RISK, shortTime } from '../lib/admin'
import type { RiskArea } from '../types'

const fold = (s: string) => s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().replace(/^(tinh|thanh pho|tp\.?)\s+/, '').trim()
const provinceOf = new Map(VN_PROVINCES.map(p => [fold(p.name), p]))
const rank = (l: string) => ({ VERY_HIGH: 4, HIGH: 3, MEDIUM: 2, LOW: 1 }[l] ?? 0)
const riskOf = (l: string) => RISK[l as keyof typeof RISK] ?? RISK.LOW
const PAGE = 8
type Box = { x: number; y: number; w: number; h: number }
const ALL: Box = { x: VN_BOUNDS.minX - 40, y: VN_BOUNDS.minY - 30, w: VN_BOUNDS.maxX - VN_BOUNDS.minX + 80, h: VN_BOUNDS.maxY - VN_BOUNDS.minY + 60 }
/** Mặc định zoom vào vùng có ca bệnh; chưa có dữ liệu thì mở Đồng bằng sông Cửu Long như thiết kế. */
const RATIO = 1.68
function focus(areas: RiskArea[]): Box {
  const pts = areas.map(a => provinceOf.get(fold(a.province))).filter(Boolean) as { x: number; y: number }[]
  if (!pts.length) return { x: 10060, y: -1120, w: 500, h: 500 / RATIO }
  const xs = pts.map(p => p.x), ys = pts.map(p => p.y)
  const cx = (Math.min(...xs) + Math.max(...xs)) / 2, cy = (Math.min(...ys) + Math.max(...ys)) / 2
  const h = Math.max(300, (Math.max(...ys) - Math.min(...ys)) * 2, (Math.max(...xs) - Math.min(...xs)) * 2 / RATIO)
  return { x: cx - h * RATIO / 2 + h * .1, y: cy - h * .56, w: h * RATIO, h }
}
/** Đặt nhãn điểm sao cho không đè lên nhau: thử bên phải, bên trái, phía trên, phía dưới. */
function layoutPins(pins: { x: number; y: number; w: number; h: number }[], u: number) {
  const boxes: { x: number; y: number; w: number; h: number }[] = pins.map(p => ({ x: p.x - 12 * u, y: p.y - 12 * u, w: 24 * u, h: 24 * u }))
  return pins.map(p => {
    const options = [[14 * u, -p.h / 2], [-14 * u - p.w, -p.h / 2], [-p.w / 2, -p.h - 16 * u], [-p.w / 2, 16 * u], [14 * u, -p.h - 12 * u], [14 * u, 12 * u]]
    const free = options.find(([dx, dy]) => !boxes.some(b => p.x + dx < b.x + b.w && p.x + dx + p.w > b.x && p.y + dy < b.y + b.h && p.y + dy + p.h > b.y)) ?? options[0]
    boxes.push({ x: p.x + free[0], y: p.y + free[1], w: p.w, h: p.h })
    return free
  })
}

function RiskMap({ areas, onPick }: { areas: RiskArea[]; onPick: (a: RiskArea) => void }) {
  const initial = useMemo(() => focus(areas), [areas])
  const [box, setBox] = useState<Box | null>(null)
  const view = box ?? initial
  const drag = useRef<{ x: number; y: number; box: Box } | null>(null)
  const svg = useRef<SVGSVGElement>(null)
  const byProvince = new Map<string, RiskArea[]>()
  areas.forEach(a => { const k = fold(a.province); byProvince.set(k, [...(byProvince.get(k) ?? []), a]) })
  const outside = areas.filter(a => !provinceOf.has(fold(a.province))).length
  const zoom = (f: number) => setBox({ x: view.x + view.w * (1 - f) / 2, y: view.y + view.h * (1 - f) / 2, w: Math.min(view.w * f, ALL.w * 1.5), h: Math.min(view.h * f, ALL.w * 1.5 * view.h / view.w) })
  const unit = view.w / 790   // đổi px màn hình sang đơn vị bản đồ để nhãn giữ cỡ khi zoom
  const pins = [...byProvince.entries()].flatMap(([key, list]) => {
    const p = provinceOf.get(key); if (!p) return []
    const area = [...list].sort((x, y) => y.cases - x.cases)[0], total = list.reduce((s, r) => s + r.cases, 0)
    return [{ key, area, total, x: p.x, y: p.y, color: riskOf(area.level).color, w: Math.max(84, area.district.length * 8 + 24) * unit, h: 45 * unit }]
  }).sort((a, b) => b.total - a.total)
  const spots = layoutPins(pins, unit)
  return <div style={{ position: 'relative' }}>
    <svg ref={svg} className="map-svg" viewBox={`${view.x} ${view.y} ${view.w} ${view.h}`} role="img" aria-label="Bản đồ Việt Nam theo vùng nguy cơ"
      onPointerDown={e => { drag.current = { x: e.clientX, y: e.clientY, box: view }; e.currentTarget.setPointerCapture(e.pointerId) }}
      onPointerMove={e => { const d = drag.current; if (!d || !svg.current) return; const r = view.w / svg.current.clientWidth; setBox({ ...d.box, x: d.box.x - (e.clientX - d.x) * r, y: d.box.y - (e.clientY - d.y) * r }) }}
      onPointerUp={() => { drag.current = null }}>
      {VN_PROVINCES.map(p => {
        const here = byProvince.get(fold(p.name))
        const lv = here?.reduce((a, r) => rank(r.level) > rank(a) ? r.level : a, 'LOW')
        return <path key={p.name} d={p.d} className={lv ? `lv-${lv.toLowerCase()}` : undefined}><title>{p.name}{here ? `: ${here.reduce((a, r) => a + r.cases, 0)} ca` : ''}</title></path>
      })}
      {view.w < 1000 && VN_PROVINCES.filter(p => !byProvince.has(fold(p.name))).map(p => <text key={p.name} x={p.x} y={p.y} textAnchor="middle" fontSize={11 * unit}>{p.name}</text>)}
      <text x={9960} y={-1075} textAnchor="middle" fontSize={15 * unit} style={{ fill: '#6FA9C9' }}>Biển Tây</text>
      <text x={10470} y={-945} textAnchor="middle" fontSize={15 * unit} style={{ fill: '#6FA9C9' }}>Biển Đông</text>
      {pins.map((p, i) => {
        const [dx, dy] = spots[i]
        return <g key={p.key} className="pin" onClick={() => onPick(p.area)} transform={`translate(${p.x} ${p.y})`}>
          <title>{p.area.district}, {p.area.province}</title>
          <circle r={10 * unit} fill="#fff" stroke={p.color} strokeWidth={3 * unit} /><circle r={4.5 * unit} fill={p.color} />
          <rect x={dx} y={dy} width={p.w} height={p.h} rx={9 * unit} fill="#fff" stroke="rgba(10,50,35,.12)" strokeWidth={unit} />
          <text x={dx + 11 * unit} y={dy + 18 * unit} fontSize={12.5 * unit} fontWeight={700} style={{ fill: '#033227' }}>{p.area.district}</text>
          <text x={dx + 11 * unit} y={dy + 35 * unit} fontSize={12 * unit} fontWeight={600} style={{ fill: p.color }}>{p.total} ca</text>
        </g>
      })}
    </svg>
    <div className="map-title"><div className="panel-title"><div className="icon-tile"><MapIcon size={21} /></div><div><h2>Bản đồ khu vực theo dõi</h2><p>Phân bố các khu vực có ca bệnh nổi bật</p></div></div></div>
    <div className="map-legend">{(['HIGH', 'MEDIUM', 'LOW'] as const).map(l => <span key={l}><i className="bar-dot" style={{ background: RISK[l].color }} />{RISK[l].label}</span>)}</div>
    <div className="map-zoom"><button aria-label="Phóng to" onClick={() => zoom(.72)}><Plus size={17} /></button><button aria-label="Thu nhỏ" onClick={() => zoom(1.4)}><Minus size={17} /></button></div>
    {outside > 0 && <div className="map-note">{outside} khu vực có tên tỉnh mới, chưa có trên bản đồ 63 tỉnh.</div>}
  </div>
}

function AreaDetail({ area, days }: { area: RiskArea; days: number }) {
  const info = useQuery({ queryKey: ['risk-info', area.province, area.district, days], queryFn: () => riskService.detail(area.province, area.district, days) })
  const d = info.data
  const top = area.diseases[0]
  const r = riskOf(area.level)
  const meter = { LOW: 25, MEDIUM: 50, HIGH: 75, VERY_HIGH: 100 }[area.level] ?? 25
  return <Panel icon={MapPin} title={`${area.district}, ${area.province}`} description={`Chi tiết trong ${days} ngày gần nhất`} action={<span className="cell-sub" style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}><Calendar size={14} />Cập nhật: {shortTime(area.lastAt)}</span>}>
    <div className="detail-stats">
      <div className="detail-stat"><div className="icon-tile"><FileText size={21} /></div><div><small>Số ca ghi nhận</small><b>{area.cases}</b></div></div>
      <div className="detail-stat"><div className="icon-tile tone-danger"><TriangleAlert size={21} /></div><div style={{ flex: 1 }}><small>Mức nguy cơ</small><div className="risk-meter"><div className="track"><div className="fill" style={{ width: `${meter}%`, background: r.color }} /></div><Pill tone={r.tone} bare>{r.label}</Pill></div></div></div>
      <div className="detail-stat"><div className="icon-tile"><Sprout size={21} /></div><div><small>Giai đoạn lúa nổi bật</small><b>{area.dominantStage || 'Chưa có'}</b></div></div>
      <div className="detail-stat"><div className="icon-tile"><Leaf size={21} /></div><div><small>Bệnh nổi bật</small><b>{top ? `${top.name} (${top.cases})` : 'Chưa có'}</b></div></div>
    </div>
    {d?.daily && d.daily.length > 1 && <ResponsiveContainer width="100%" height={110}><AreaChart data={d.daily} margin={{ top: 6, right: 6, left: -26, bottom: 0 }}><CartesianGrid strokeDasharray="4 4" stroke="var(--line)" vertical={false} /><XAxis dataKey="date" tickFormatter={v => String(v).slice(8)} tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: 'var(--muted)' }} minTickGap={24} /><YAxis allowDecimals={false} tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: 'var(--muted)' }} /><Tooltip labelFormatter={v => String(v).split('-').reverse().join('/')} /><Area type="monotone" dataKey="cases" name="Số ca" stroke="#1E593A" strokeWidth={2} fill="#BFE0CB" isAnimationActive={false} /></AreaChart></ResponsiveContainer>}
    <div className="notice info" style={{ marginTop: 12 }}><Lightbulb size={20} color="#1C6340" style={{ flex: 'none' }} /><div><strong style={{ color: 'var(--ink)' }}>Khuyến nghị</strong>{d?.advice ?? 'Đang tải...'}{d?.weather?.condition && ` Thời tiết: ${d.weather.condition.label}, ${Math.round(d.weather.tempC ?? 0)}°C, độ ẩm ${d.weather.humidity}%.`}</div></div>
  </Panel>
}

export function Risk() {
  const [province, setProvince] = useState('')
  const [disease, setDisease] = useState('')
  const [days, setDays] = useState(30)
  const [sort, setSort] = useState('risk')
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [picked, setPicked] = useState<RiskArea | null>(null)
  const [page, setPage] = useState(0)
  const query = useRiskAreas({ province, disease, days, sort })
  const diseases = useDiseases({})
  const all = query.data ?? []
  const provinces = [...new Set(all.map(r => r.province))].sort()
  const rows = all.filter(r => !q || fold(`${r.province} ${r.district}`).includes(fold(q)))
  const shown = rows.slice(page * PAGE, (page + 1) * PAGE)
  const high = rows.filter(r => r.level === 'HIGH' || r.level === 'VERY_HIGH').length
  const cases = rows.reduce((a, r) => a + r.cases, 0)
  const top = [...rows].sort((a, b) => b.cases - a.cases).slice(0, 5)
  const worst = rows.reduce((a, r) => rank(r.level) > rank(a) ? r.level : a, '')
  const mix = (['HIGH', 'MEDIUM', 'LOW'] as const).map(l => ({ name: RISK[l].label, value: rows.filter(r => l === 'HIGH' ? rank(r.level) >= 3 : r.level === l).length }))
  const max = Math.max(1, ...top.map(t => t.cases))
  function exportCsv() {
    try { downloadCsv('vung-nguy-co.csv', ['Tỉnh', 'Huyện', 'Ca bệnh', 'Mức độ', 'Bệnh', 'Cập nhật'], rows.map(r => [r.province, r.district, r.cases, riskOf(r.level).label, r.diseases.map(d => `${d.name}: ${d.cases}`).join(' | '), dateTime(r.lastAt)])); toast.success(`Đã xuất ${rows.length} khu vực`) }
    catch (e) { toast.error(errorMessage(e)) }
  }
  return <Page art="landscape">
    <PageHeader title="Vùng nguy cơ" description="Số ca bệnh tổng hợp ẩn danh theo khu vực; đây không phải dự báo AI." />
    <div className="grid grid-4">
      <Stat icon={MapPin} label="Tỉnh đang theo dõi" value={new Set(rows.map(r => r.province)).size} sub={`${rows.length} khu vực có ca bệnh`} />
      <Stat icon={TriangleAlert} tone="danger" label="Khu vực nguy cơ cao" value={high} sub={`Trong ${days} ngày gần nhất`} subTone={high ? 'bad' : undefined} />
      <Stat icon={FileText} label="Tổng số ca ghi nhận" value={cases} sub={`Trong ${days} ngày gần nhất`} />
      <Stat icon={ShieldAlert} tone={worst ? riskOf(worst).tone as 'danger' | 'warning' : undefined} label="Mức cảnh báo hiện tại" value={worst ? riskOf(worst).label : 'Ổn định'} word valueDanger={rank(worst) >= 3} sub={worst ? 'Theo khu vực có nguy cơ cao nhất' : 'Chưa ghi nhận ca bệnh'} />
    </div>
    <Panel className="toolbar-card"><div className="toolbar">
      <select className="field-select" aria-label="Tỉnh / thành phố" value={province} onChange={e => { setProvince(e.target.value); setPage(0) }}><option value="">Tỉnh / thành phố</option>{provinces.map(p => <option key={p}>{p}</option>)}</select>
      <select className="field-select" aria-label="Bệnh" value={disease} onChange={e => { setDisease(e.target.value); setPage(0) }}><option value="">Tất cả bệnh</option>{diseases.data?.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select>
      <select className="field-select" aria-label="Khoảng thời gian" value={days} onChange={e => { setDays(Number(e.target.value)); setPage(0) }}><option value={7}>7 ngày</option><option value={30}>30 ngày</option><option value={90}>90 ngày</option></select>
      <select className="field-select" aria-label="Sắp xếp" value={sort} onChange={e => setSort(e.target.value)}><option value="risk">Theo nguy cơ</option><option value="count">Theo số ca</option></select>
      <SearchBox placeholder="Tìm theo khu vực..." value={search} onChange={setSearch} onSubmit={() => { setQ(search); setPage(0) }} />
      <Button disabled={!rows.length} onClick={exportCsv}><Download size={16} />Xuất dữ liệu</Button>
    </div></Panel>
    <div className="grid grid-main-side">
      <Panel className="map-card"><RiskMap areas={rows} onPick={setPicked} /></Panel>
      <div className="stack">
        <Panel icon={BarChart3} title="Khu vực nhiều ca">
          {top.length ? <div className="area-bars">{top.map(a => <div className="area-bar" key={`${a.province}/${a.district}`}><span className="nowrap" style={{ overflow: 'hidden', textOverflow: 'ellipsis' }} title={a.district}>{a.district}</span><div className="rail"><div className="fill" style={{ width: `${a.cases / max * 100}%`, background: riskOf(a.level).color }} /></div><b>{a.cases}</b></div>)}</div> : <div className="empty-chart">Chưa có ca bệnh</div>}
        </Panel>
        <Panel icon={PieIcon} title="Phân bố mức nguy cơ"><Donut size={130} rows={mix} colors={['#E35457', '#FABF55', '#2E8E56']} center={<><b>{rows.length}</b><span>khu vực</span></>} /></Panel>
      </div>
    </div>
    <div className="grid grid-main-side">
      <Panel flush icon={FileText} title="Danh sách khu vực nổi bật">
        <QueryState query={query} empty={rows.length === 0} emptyText={`Không có ca bệnh trong ${days} ngày đã chọn`} />
        {rows.length > 0 && <><div className="table-wrap"><table className="data-table"><thead><tr><th>Tỉnh / thành phố</th><th>Huyện</th><th>Số ca</th><th>Nguy cơ</th><th>Cập nhật</th><th>Thao tác</th></tr></thead><tbody>{shown.map(r => {
          const x = riskOf(r.level)
          return <tr key={`${r.province}/${r.district}`}><td>{r.province}</td><td className="cell-title">{r.district}</td><td>{r.cases}</td><td><Pill tone={x.tone}>{x.label}</Pill></td><td className="nowrap">{shortTime(r.lastAt)}</td><td><button className="link" onClick={() => setPicked(r)}>Xem chi tiết <ArrowRight size={15} /></button></td></tr>
        })}</tbody></table></div><Pager page={page} pages={Math.ceil(rows.length / PAGE)} hasNext={false} onPage={setPage} label={`Hiển thị ${shown.length} trong ${rows.length} khu vực`} /></>}
      </Panel>
      <div className="hint-card"><h3><i><Lightbulb size={20} /></i>Gợi ý theo dõi</h3><p>Ưu tiên kiểm tra các khu vực có số ca tăng nhanh sau mưa và các địa bàn có lịch sử phát sinh bệnh lặp lại.</p></div>
    </div>
    {picked ? <AreaDetail area={picked} days={days} /> : rows.length > 0 && <Panel><EmptyState title="Chọn một khu vực" description="Bấm vào điểm trên bản đồ hoặc dòng trong bảng để xem chi tiết và diễn biến số ca." /></Panel>}
  </Page>
}
