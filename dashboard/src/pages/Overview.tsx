import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Activity, ArrowRight, BarChart3, BookOpen, ClipboardList, FlaskConical, Leaf, LineChart, LayoutGrid, MapPin, MessageSquareText, PieChart as PieIcon, ScanLine, Sprout } from 'lucide-react'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { statsService } from '../api/services'
import { Donut, ErrorState, Page, PageHeader, Panel, SkeletonRows, Stat } from '../components/ui'

const MIX = ['#1E593A', '#76AB86', '#E2B75C', '#D0D5B8', '#4E946E', '#8EB99F', '#BFA46A']
const DAY = (d: string) => { const n = new Date(`${d}T00:00:00`).getDay(); return n === 0 ? 'CN' : `T${n + 1}` }

/** So hôm nay với hôm qua; chưa có dữ liệu hôm qua thì không nêu phần trăm. */
function change(series: number[]) {
  const [prev, now] = series.slice(-2)
  if (!prev) return now ? 'Mới có dữ liệu hôm nay' : 'Chưa có dữ liệu mới'
  const v = Math.round((now - prev) / prev * 100)
  return `${v > 0 ? '+' : ''}${v}% so với hôm qua`
}

export function Overview() {
  const query = useQuery({ queryKey: ['overview'], queryFn: statsService.overview })
  const data = query.data
  const scans = data?.trend.map(t => t.scans) ?? []
  const cases = data?.trend.map(t => t.cases) ?? []
  const mix = [...(data?.diseaseMix ?? [])].sort((a, b) => b.value - a.value)
  const top = mix.slice(0, 3).concat(mix.length > 3 ? [{ name: 'Khác', value: mix.slice(3).reduce((a, m) => a + m.value, 0) }] : [])
  return <Page art="rice">
    <PageHeader title="Tổng quan hệ thống" description="Không gian theo dõi hoạt động và những việc cần chú ý hôm nay." />
    <section className="hero">
      <div className="hero-body">
        <small>RiceGuard AI / Trung tâm điều hành</small>
        <h2>Chăm mùa lúa bằng dữ liệu<br />và những quyết định kịp thời.</h2>
        <p>Bắt đầu từ báo cáo cần xem xét, phản hồi chẩn đoán và diễn biến nguy cơ theo khu vực.</p>
        <Link className="btn" to="/reports">Xem báo cáo vi phạm <ArrowRight size={16} /></Link>
      </div>
      <div className="hero-side">
        <div className="hero-point"><i><Sprout size={21} /></i><span>{data ? `${data.users.total} người dùng` : 'Người dùng'}<br />{data ? `+${data.users.newToday} hôm nay` : 'đang tải'}</span></div>
        <div className="hero-point"><i><BarChart3 size={21} /></i><span>{data ? `${data.posts.total} bài viết` : 'Cộng đồng'}<br />{data ? `${data.posts.today} bài mới hôm nay` : 'đang tải'}</span></div>
      </div>
      <div className="hero-art" aria-hidden="true" />
    </section>
    {query.isPending && <SkeletonRows />}
    {query.isError && <div className="panel"><ErrorState error={query.error} retry={() => query.refetch()} /></div>}
    {data && <>
      <div className="grid grid-4">
        <Stat icon={ScanLine} label="Tổng lượt quét" value={data.scans.toLocaleString('vi-VN')} sub={change(scans)} spark={scans} />
        <Stat icon={Leaf} tone="danger" label="Ca bệnh hôm nay" value={data.todayCases} sub={change(cases)} spark={cases} />
        <Stat icon={MessageSquareText} tone="warning" label="Báo cáo chờ xử lý" value={data.reports.pending} sub={`${data.reports.today} báo cáo mới hôm nay`} />
        <Stat icon={FlaskConical} label="Phản hồi AI chờ xử lý" value={data.feedbacks.pending} sub={`Trên tổng ${data.feedbacks.total} phản hồi`} />
      </div>
      <div className="grid grid-main-side">
        <Panel icon={LineChart} title="Xu hướng lượt quét" description="7 ngày gần nhất">
          <ResponsiveContainer width="100%" height={210}>
            <AreaChart data={data.trend} margin={{ top: 8, right: 10, left: -18, bottom: 0 }}>
              <defs><linearGradient id="scanFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#76AB86" stopOpacity={.5} /><stop offset="1" stopColor="#76AB86" stopOpacity={0} /></linearGradient></defs>
              <CartesianGrid strokeDasharray="4 4" stroke="var(--line)" />
              <XAxis dataKey="day" tickFormatter={DAY} axisLine={false} tickLine={false} tick={{ fontSize: 12, fill: 'var(--muted)' }} />
              <YAxis allowDecimals={false} axisLine={false} tickLine={false} tick={{ fontSize: 12, fill: 'var(--muted)' }} />
              <Tooltip labelFormatter={v => `Ngày ${String(v).split('-').reverse().join('/')}`} />
              <Area type="monotone" dataKey="scans" name="Lượt quét" stroke="#1E593A" strokeWidth={2} fill="url(#scanFill)" dot={{ r: 3.5, fill: '#1E593A' }} isAnimationActive={false} />
              <Area type="monotone" dataKey="cases" name="Ca bệnh" stroke="#E2B75C" strokeWidth={2} fill="none" isAnimationActive={false} />
            </AreaChart>
          </ResponsiveContainer>
        </Panel>
        <Panel icon={PieIcon} title="Bệnh thường gặp" description="Tỷ lệ theo ca bệnh AI đã chẩn đoán">
          <Donut rows={top} colors={MIX} center={<><span>Tổng ca</span><b>{mix.reduce((a, m) => a + m.value, 0)}</b></>} />
        </Panel>
      </div>
      <Panel icon={LayoutGrid} title="Đi tới công việc" description="Truy cập nhanh các khu vực quản trị đang hoạt động">
        <div className="quick-grid">
          <Quick to="/reports" icon={ClipboardList} title="Xử lý báo cáo" text="Bài viết và bình luận được báo cáo" />
          <Quick to="/feedbacks" icon={Activity} title="Phản hồi chẩn đoán" text="Xem góp ý về kết quả AI" />
          <Quick to="/diseases" icon={BookOpen} title="Thư viện bệnh" text="Tra cứu và cập nhật hình ảnh" warm />
          <Quick to="/risk" icon={MapPin} title="Vùng nguy cơ" text="Theo dõi ca bệnh theo khu vực" />
        </div>
      </Panel>
    </>}
  </Page>
}
function Quick({ to, icon: Icon, title, text, warm }: { to: string; icon: typeof Activity; title: string; text: string; warm?: boolean }) {
  return <Link to={to} className={`quick${warm ? ' warm' : ''}`}><div className={`icon-tile${warm ? ' tone-warning' : ''}`}><Icon size={21} /></div><div className="text"><strong>{title}</strong><span>{text}</span></div><ArrowRight size={16} /></Link>
}
