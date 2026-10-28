import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { errorMessage } from '../api/client'
import { feedbackService, statsService } from '../api/services'
import { useFeedbacks } from '../hooks/useQueries'
import { collectPages, dateTime, downloadCsv } from '../lib/admin'
import type { AdminFeedback } from '../types'

export function Feedbacks() {
  const client = useQueryClient()
  const [status,setStatus] = useState('ALL')
  const [reason,setReason] = useState('')
  const [disease,setDisease] = useState('')
  const [fromDate,setFromDate] = useState('')
  const [toDate,setToDate] = useState('')
  const [search,setSearch] = useState('')
  const [q,setQ] = useState('')
  const [selected,setSelected] = useState<AdminFeedback | null>(null)
  const [resolution,setResolution] = useState('')
  const [busy,setBusy] = useState(false)
  const filters = {status,reason,q,suggestedDiseaseCode:disease,from:fromDate?new Date(`${fromDate}T00:00:00`).getTime():undefined,to:toDate?new Date(`${toDate}T23:59:59.999`).getTime():undefined}
  const query = useFeedbacks(filters)
  const stats = useQuery({queryKey:['admin-stats'],queryFn:statsService.get})
  const items = query.data?.pages.flatMap(page => page.items) ?? []
  async function save(next: string) {
    if (!selected) return
    setBusy(true)
    try {await feedbackService.update(selected.id,next,resolution); await Promise.all([client.invalidateQueries({queryKey:['feedbacks']}),client.invalidateQueries({queryKey:['admin-stats']})]);setSelected(null);toast.success('Đã lưu kết luận và trạng thái')}
    catch(e){toast.error(errorMessage(e))} finally{setBusy(false)}
  }
  async function exportCsv() {
    setBusy(true)
    try {const all=await collectPages(cursor => feedbackService.list(cursor,filters));downloadCsv('phan-hoi-ai.csv',['ID','Người dùng','Lượt quét','Bệnh AI','Độ tin cậy','Lý do','Bệnh đề xuất','Mô tả','Trạng thái','Kết luận','Ảnh đính kèm','Thời gian'],all.map(f=>[f.id,f.userId,f.scanId,f.scanDiseaseCode,f.scanConfidence,f.reason,f.suggestedDiseaseCode,f.description,f.status,f.resolution,f.attachments?.map(a=>a.url).join(' | '),dateTime(f.createdAt)]));toast.success(`Đã xuất ${all.length} phản hồi`)}
    catch(e){toast.error(errorMessage(e))}finally{setBusy(false)}
  }
  return <main className="admin-page"><h1>Phản hồi AI</h1><p>Kiểm tra phản hồi về kết quả chẩn đoán</p>
    {stats.data && <div className="admin-stats"><div className="admin-stat">Tổng phản hồi<strong>{stats.data.feedbacks.total}</strong></div><div className="admin-stat">Chờ xử lý<strong>{stats.data.feedbacks.pending}</strong></div><div className="admin-stat">Đang xem<strong>{stats.data.feedbacks.reviewing}</strong></div><div className="admin-stat">Đã xử lý<strong>{stats.data.feedbacks.resolved}</strong></div></div>}
    {stats.isError && <div className="admin-error">{errorMessage(stats.error)}</div>}
    <div className="admin-toolbar"><select aria-label="Trạng thái" value={status} onChange={e=>setStatus(e.target.value)}><option value="ALL">Mọi trạng thái</option><option value="NEW">Chờ xử lý</option><option value="REVIEWING">Đang xem</option><option value="RESOLVED">Đã xử lý</option></select><select aria-label="Lý do" value={reason} onChange={e=>setReason(e.target.value)}><option value="">Mọi lý do</option>{['WRONG_RESULT','UNCLEAR_IMAGE','MISSING_INFO','NEED_ADVICE'].map(x=><option key={x}>{x}</option>)}</select><input aria-label="Bệnh đề xuất" placeholder="Mã bệnh đề xuất" value={disease} onChange={e=>setDisease(e.target.value)}/><label>Từ ngày <input type="date" value={fromDate} onChange={e=>setFromDate(e.target.value)}/></label><label>Đến ngày <input type="date" value={toDate} onChange={e=>setToDate(e.target.value)}/></label><form onSubmit={e=>{e.preventDefault();setQ(search)}}><input aria-label="Tìm kiếm" placeholder="Người dùng, bệnh, mô tả..." value={search} onChange={e=>setSearch(e.target.value)}/><button>Tìm</button></form><button onClick={()=>{setStatus('ALL');setReason('');setDisease('');setFromDate('');setToDate('');setSearch('');setQ('')}}>Xóa lọc</button><button disabled={busy} onClick={exportCsv}>Xuất CSV</button></div>
    {query.isPending && <div className="admin-empty">Đang tải phản hồi...</div>}{query.isError && <div className="admin-error">{errorMessage(query.error)} <button onClick={()=>query.refetch()}>Thử lại</button></div>}{query.isSuccess && items.length===0 && <div className="admin-empty">Không có phản hồi phù hợp</div>}
    {items.length>0 && <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Người dùng</th><th>Kết quả AI</th><th>Lý do</th><th>Thời gian</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{items.map(f=><tr key={f.id}><td>{f.userId}<div className="admin-muted">{f.scanId}</div></td><td>{f.scanDiseaseCode || 'Chưa xác định'}<div className="admin-muted">{f.scanConfidence == null ? '—' : `${Math.round(f.scanConfidence*100)}%`}</div></td><td>{f.reason}</td><td>{dateTime(f.createdAt)}</td><td>{f.status}</td><td><button onClick={()=>{setSelected(f);setResolution(f.resolution || '')}}>Xem / xử lý</button></td></tr>)}</tbody></table></div>}
    {query.hasNextPage && <div className="admin-pagination"><button disabled={query.isFetchingNextPage} onClick={()=>query.fetchNextPage()}>{query.isFetchingNextPage?'Đang tải...':'Tải tiếp phản hồi'}</button></div>}
    {selected && <section className="admin-panel" style={{marginTop:20}}><h2>Phản hồi {selected.id}</h2><p>Người dùng: {selected.userId} · Lượt quét: {selected.scanId}</p><p>Lý do: {selected.reason} · Bệnh đề xuất: {selected.suggestedDiseaseCode || '—'}</p><p>{selected.description || 'Không có mô tả'}</p>{selected.imageUrl && <a href={selected.imageUrl} target="_blank" rel="noreferrer"><img className="preview" src={selected.imageUrl} alt="Ảnh lượt quét"/></a>}<div><strong>Ảnh đính kèm</strong><div className="admin-actions">{selected.attachments?.length ? selected.attachments.map((a,i)=><a key={i} href={a.url} target="_blank" rel="noreferrer"><img className="preview" src={a.url} alt={`Ảnh đính kèm ${i+1}`}/></a>) : <p>Không có ảnh đính kèm</p>}</div></div><label htmlFor="fb-resolution">Kết luận</label><textarea id="fb-resolution" maxLength={2000} value={resolution} onChange={e=>setResolution(e.target.value)}/><div className="admin-actions"><button disabled={busy} onClick={()=>save('REVIEWING')}>Đang xem</button><button disabled={busy} onClick={()=>save('RESOLVED')}>Đã xử lý</button><button onClick={()=>setSelected(null)}>Đóng</button></div></section>}
  </main>
}
