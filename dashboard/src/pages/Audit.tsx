import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useInfiniteQuery } from '@tanstack/react-query'
import { auditService } from '../api/services'
import { errorMessage } from '../api/client'
import { dateTime } from '../lib/admin'

export function Audit() {
  const [params]=useSearchParams()
  const [actor,setActor]=useState(params.get('actorId')||'')
  const [target,setTarget]=useState(params.get('targetId')||'')
  const [action,setAction]=useState('')
  const [type,setType]=useState('')
  const filters={actorId:actor,targetId:target,action,targetType:type}
  const query=useInfiniteQuery({queryKey:['admin-audit',filters],queryFn:({pageParam})=>auditService.list(filters,pageParam),initialPageParam:null as string|null,getNextPageParam:p=>p.nextCursor})
  const rows=query.data?.pages.flatMap(p=>p.items)??[]
  return <main className="admin-page"><h1>Nhật ký quản trị</h1><p>Lịch sử kiểm duyệt, thay đổi dữ liệu và vận hành từ backend</p><div className="admin-toolbar"><input aria-label="UID người thực hiện" placeholder="UID người thực hiện" value={actor} onChange={e=>setActor(e.target.value)}/><input aria-label="ID đối tượng" placeholder="ID đối tượng" value={target} onChange={e=>setTarget(e.target.value)}/><input aria-label="Thao tác" placeholder="Mã thao tác" value={action} onChange={e=>setAction(e.target.value)}/><select aria-label="Loại đối tượng" value={type} onChange={e=>setType(e.target.value)}><option value="">Mọi đối tượng</option>{['USER','POST','COMMENT','REPORT','FEEDBACK','DISEASE','CAMPAIGN','JOB'].map(x=><option key={x}>{x}</option>)}</select></div>
    {query.isPending&&<div className="admin-empty">Đang tải nhật ký...</div>}{query.isError&&<div className="admin-error">{errorMessage(query.error)} <button onClick={()=>query.refetch()}>Thử lại</button></div>}{query.isSuccess&&rows.length===0&&<div className="admin-empty">Chưa có sự kiện phù hợp</div>}{rows.length>0&&<div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Thời gian</th><th>Người thực hiện</th><th>Thao tác</th><th>Đối tượng</th><th>Chi tiết</th></tr></thead><tbody>{rows.map(row=><tr key={row.id}><td>{dateTime(row.createdAt)}</td><td>{row.actorId}</td><td>{row.action}</td><td>{row.targetType} · {row.targetId}</td><td>{row.detail}</td></tr>)}</tbody></table></div>}{query.hasNextPage&&<div className="admin-pagination"><button disabled={query.isFetchingNextPage} onClick={()=>query.fetchNextPage()}>{query.isFetchingNextPage?'Đang tải...':'Tải tiếp nhật ký'}</button></div>}
  </main>
}
