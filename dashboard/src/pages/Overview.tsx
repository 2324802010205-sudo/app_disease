import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Area, AreaChart, CartesianGrid, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { statsService } from '../api/services'
import { errorMessage } from '../api/client'

export function Overview() {
  const query = useQuery({ queryKey: ['overview'], queryFn: statsService.overview })
  const data = query.data
  return <main className="admin-page">
    <h1>Tổng quan hệ thống</h1><p>Số liệu trực tiếp từ hệ thống</p>
    {query.isPending && <div className="admin-empty">Đang tải dữ liệu...</div>}
    {query.isError && <div className="admin-error">{errorMessage(query.error)} <button onClick={() => query.refetch()}>Thử lại</button></div>}
    {data && <>
      <div className="admin-stats">
        <div className="admin-stat">Lượt quét<strong>{data.scans}</strong></div>
        <div className="admin-stat">Ca bệnh hôm nay<strong>{data.todayCases}</strong></div>
        <div className="admin-stat">Báo cáo chờ xử lý<strong>{data.reports.pending}</strong><Link to="/reports">Xem báo cáo</Link></div>
        <div className="admin-stat">Phản hồi AI chờ xử lý<strong>{data.feedbacks.pending}</strong><Link to="/feedbacks">Xem phản hồi</Link></div>
        <div className="admin-stat">Bài viết<strong>{data.posts.total}</strong><Link to="/posts">Xem bài viết</Link></div>
      </div>
      <div className="admin-grid">
        <section className="admin-panel"><h2>Xu hướng 7 ngày</h2>{data.trend.length ? <ResponsiveContainer width="100%" height={280}><AreaChart data={data.trend}><CartesianGrid strokeDasharray="3 3"/><XAxis dataKey="day"/><YAxis/><Tooltip/><Area dataKey="scans" name="Lượt quét" stroke="#17603f" fill="#b4ddc5"/><Area dataKey="cases" name="Ca bệnh" stroke="#bf7e24" fill="#f6daa9"/></AreaChart></ResponsiveContainer> : <p>Chưa có dữ liệu</p>}</section>
        <section className="admin-panel"><h2>Phân bố bệnh</h2>{data.diseaseMix.length ? <ResponsiveContainer width="100%" height={280}><PieChart><Pie data={data.diseaseMix} dataKey="value" nameKey="name" fill="#17603f" label/><Tooltip/></PieChart></ResponsiveContainer> : <p>Chưa có dữ liệu</p>}</section>
      </div>
    </>}
  </main>
}
