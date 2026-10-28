import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { errorMessage } from '../api/client'
import { operationService } from '../api/services'
import { dateTime } from '../lib/admin'

export function Operations() {
  const client=useQueryClient()
  const [test,setTest]=useState(true)
  const [busy,setBusy]=useState(false)
  const status=useQuery({queryKey:['operations'],queryFn:operationService.status})
  const preview=useQuery({queryKey:['reminder-preview'],queryFn:operationService.previewReminders})
  async function run(kind:'reminders'|'risk'|'diseases') {
    if (kind==='reminders' && !confirm(test ? 'Gửi nhắc lịch thử cho tối đa 10 người?' : 'Gửi nhắc lịch cho toàn bộ người nhận hôm nay?')) return
    setBusy(true)
    try {
      if (kind==='reminders') {const result=await operationService.runReminders(test);toast.success(`Đã gửi ${result.sent} nhắc lịch${test?' ở chế độ thử':''}`)}
      if (kind==='risk') {const result=await operationService.refreshRisk();toast.success(`Đã cập nhật ${result.count} vùng nguy cơ`)}
      if (kind==='diseases') {const result=await operationService.syncDiseases();toast.success(`Đã đồng bộ ${result.count} bệnh`)}
      await Promise.all([client.invalidateQueries({queryKey:['operations']}),client.invalidateQueries({queryKey:['reminder-preview']}),client.invalidateQueries({queryKey:['risk']}),client.invalidateQueries({queryKey:['diseases']})])
    } catch(e){toast.error(errorMessage(e))}finally{setBusy(false)}
  }
  return <main className="admin-page"><h1>Vận hành hệ thống</h1><p>Tác vụ và trạng thái từ backend</p>
    {status.isPending && <div className="admin-empty">Đang tải trạng thái...</div>}{status.isError && <div className="admin-error">{errorMessage(status.error)} <button onClick={()=>status.refetch()}>Thử lại</button></div>}
    {status.data && <div className="admin-stats"><div className="admin-stat">Backend<strong>{status.data.backend}</strong></div><div className="admin-stat">Firestore<strong>{status.data.firestore}</strong></div><div className="admin-stat">Bệnh trong thư viện<strong>{status.data.diseases}</strong></div><div className="admin-stat">Vùng nguy cơ<strong>{status.data.riskAreas}</strong></div><div className="admin-stat">Báo cáo<strong>{status.data.reports}</strong></div></div>}
    <div className="admin-grid"><section className="admin-panel"><h2>Nhắc lịch kiểm tra ruộng</h2><p>Người nhận đến hạn hôm nay: {preview.isPending?'Đang tải...':preview.data?.count ?? '—'}</p>{preview.isError && <div className="admin-error">{errorMessage(preview.error)}</div>}<label><input type="checkbox" checked={test} onChange={e=>setTest(e.target.checked)}/> Chế độ thử (tối đa 10 thông báo)</label><div className="admin-toolbar"><button className="primary" disabled={busy || preview.isError} onClick={()=>run('reminders')}>Gửi nhắc lịch</button></div><h3>Danh sách người nhận</h3>{preview.data?.recipients.length===0 && <p>Không có người nhận hôm nay</p>}{preview.data?.recipients.map(r=><p key={`${r.userId}/${r.fieldId}`}>{r.userId} · {r.fieldName || r.fieldId} · {r.district}, {r.province}</p>)}</section><section className="admin-panel"><h2>Tác vụ dữ liệu</h2><p>Các tác vụ chạy trên backend và ghi nhật ký kết quả.</p><div className="admin-actions"><button disabled={busy} onClick={()=>run('risk')}>Làm mới vùng nguy cơ</button><button disabled={busy} onClick={()=>run('diseases')}>Đồng bộ thư viện bệnh</button></div></section></div>
    <h2>Lịch sử tác vụ</h2>{status.data?.jobs.length===0 && <div className="admin-empty">Chưa có tác vụ nào</div>}{status.data && status.data.jobs.length>0 && <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Tác vụ</th><th>Kết quả</th><th>Thời gian</th></tr></thead><tbody>{status.data.jobs.map(j=><tr key={j.id}><td>{j.kind}</td><td>{j.count}</td><td>{dateTime(j.createdAt)}</td></tr>)}</tbody></table></div>}
  </main>
}
