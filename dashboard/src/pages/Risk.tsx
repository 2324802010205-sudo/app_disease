import { useState } from 'react'
import { toast } from 'sonner'
import { errorMessage } from '../api/client'
import { useRiskAreas } from '../hooks/useQueries'
import { dateTime, downloadCsv } from '../lib/admin'
import type { RiskArea } from '../types'

export function Risk() {
  const [province,setProvince]=useState('')
  const [disease,setDisease]=useState('')
  const [days,setDays]=useState(7)
  const [sort,setSort]=useState('risk')
  const [selected,setSelected]=useState<RiskArea | null>(null)
  const query=useRiskAreas({province,disease,days,sort})
  const rows=query.data ?? []
  function exportCsv(){try{downloadCsv('vung-nguy-co.csv',['Tỉnh','Huyện','Ca bệnh','Mức độ','Bệnh','Cập nhật'],rows.map(r=>[r.province,r.district,r.cases,r.level,r.diseases.map(d=>`${d.name}: ${d.cases}`).join(' | '),dateTime(r.lastAt)]));toast.success(`Đã xuất ${rows.length} khu vực`)}catch(e){toast.error(errorMessage(e))}}
  return <main className="admin-page"><h1>Vùng nguy cơ</h1><p>Địa bàn phát hiện ca bệnh từ API</p><div className="admin-toolbar"><input aria-label="Tỉnh" placeholder="Tỉnh / thành phố" value={province} onChange={e=>setProvince(e.target.value)}/><input aria-label="Mã bệnh" placeholder="Mã bệnh" value={disease} onChange={e=>setDisease(e.target.value)}/><select aria-label="Khoảng thời gian" value={days} onChange={e=>setDays(Number(e.target.value))}><option value={7}>7 ngày</option><option value={30}>30 ngày</option><option value={90}>90 ngày</option></select><select aria-label="Sắp xếp" value={sort} onChange={e=>setSort(e.target.value)}><option value="risk">Mức nguy cơ</option><option value="count">Số ca</option></select><button onClick={exportCsv} disabled={query.isPending || query.isError}>Xuất CSV</button></div>
    {query.isPending && <div className="admin-empty">Đang tải vùng nguy cơ...</div>}{query.isError && <div className="admin-error">{errorMessage(query.error)} <button onClick={()=>query.refetch()}>Thử lại</button></div>}{query.isSuccess && rows.length===0 && <div className="admin-empty">Không có khu vực nguy cơ trong thời gian đã chọn</div>}
    {rows.length>0 && <div className="admin-grid"><div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Khu vực</th><th>Số ca</th><th>Nguy cơ</th><th>Bệnh phổ biến</th><th>Cập nhật</th></tr></thead><tbody>{rows.map(r=><tr key={`${r.province}/${r.district}`} onClick={()=>setSelected(r)} style={{cursor:'pointer'}}><td>{r.district}, {r.province}</td><td>{r.cases}</td><td>{r.level}</td><td>{r.diseases.map(d=>d.name).join(', ')}</td><td>{dateTime(r.lastAt)}</td></tr>)}</tbody></table></div><div className="admin-panel"><h2>Bản đồ khu vực</h2>{selected ? <><p>{selected.district}, {selected.province}</p><iframe title="Bản đồ khu vực" width="100%" height="400" loading="lazy" referrerPolicy="no-referrer-when-downgrade" src={`https://maps.google.com/maps?q=${encodeURIComponent(`${selected.district}, ${selected.province}, Việt Nam`)}&output=embed`}/></> : <p>Chọn một khu vực từ bảng để xem trên bản đồ.</p>}</div></div>}
  </main>
}
