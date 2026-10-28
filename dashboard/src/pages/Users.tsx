import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { userService } from '../api/services'
import { errorMessage } from '../api/client'
import { dateTime } from '../lib/admin'

export function Users() {
  const [search,setSearch]=useState('')
  const [q,setQ]=useState('')
  const [role,setRole]=useState('')
  const [province,setProvince]=useState('')
  const [verified,setVerified]=useState('ALL')
  const [locked,setLocked]=useState('ALL')
  const filters={q,role,province,verified:verified==='ALL'?undefined:verified==='YES',locked:locked==='ALL'?undefined:locked==='YES'}
  const query=useInfiniteQuery({queryKey:['admin-users',filters],queryFn:({pageParam})=>userService.list(filters,pageParam),initialPageParam:null as string|null,getNextPageParam:p=>p.nextCursor})
  const rows=query.data?.pages.flatMap(p=>p.items)??[]
  return <main className="admin-page"><h1>Người dùng</h1><p>Tìm và quản lý hồ sơ trên toàn hệ thống</p><div className="admin-toolbar"><form onSubmit={e=>{e.preventDefault();setQ(search)}}><input aria-label="Tìm người dùng" value={search} onChange={e=>setSearch(e.target.value)} placeholder="Tên, email hoặc UID"/><button>Tìm</button></form><input aria-label="Tỉnh" placeholder="Tỉnh/thành" value={province} onChange={e=>setProvince(e.target.value)}/><select aria-label="Vai trò" value={role} onChange={e=>setRole(e.target.value)}><option value="">Mọi vai trò</option><option value="FARMER">Nông dân</option><option value="EXPERT">Chuyên gia</option></select><select aria-label="Xác minh chuyên gia" value={verified} onChange={e=>setVerified(e.target.value)}><option value="ALL">Mọi xác minh</option><option value="YES">Đã xác minh</option><option value="NO">Chưa xác minh</option></select><select aria-label="Khóa tài khoản" value={locked} onChange={e=>setLocked(e.target.value)}><option value="ALL">Mọi tài khoản</option><option value="YES">Đã khóa</option><option value="NO">Đang mở</option></select></div>
    {query.isPending && <div className="admin-empty">Đang tải người dùng...</div>}{query.isError && <div className="admin-error">{errorMessage(query.error)} <button onClick={()=>query.refetch()}>Thử lại</button></div>}{query.isSuccess && rows.length===0 && <div className="admin-empty">Không có người dùng phù hợp</div>}
    {rows.length>0 && <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Người dùng</th><th>Vai trò</th><th>Địa bàn</th><th>Chuyên gia</th><th>Tài khoản</th><th>Ngày tạo</th></tr></thead><tbody>{rows.map(u=><tr key={u.id}><td><Link className="admin-link" to={`/users/${encodeURIComponent(u.id)}`}>{u.fullName||u.email}</Link><div className="admin-muted">{u.email}<br/>{u.id}</div></td><td>{u.role}</td><td>{u.district}, {u.province}</td><td>{u.verifiedExpert?'Đã xác minh':'Chưa xác minh'}</td><td>{u.locked?'Đã khóa':'Đang mở'}</td><td>{dateTime(u.createdAt)}</td></tr>)}</tbody></table></div>}{query.hasNextPage && <div className="admin-pagination"><button disabled={query.isFetchingNextPage} onClick={()=>query.fetchNextPage()}>{query.isFetchingNextPage?'Đang tải...':'Tải tiếp'}</button></div>}
  </main>
}
export function UserDetail() {
  const {id=''}=useParams()
  const client=useQueryClient()
  const query=useQuery({queryKey:['admin-user',id],queryFn:()=>userService.detail(id),enabled:!!id})
  const [busy,setBusy]=useState(false)
  async function act(kind:'verify'|'lock',next:boolean) {
    if (kind==='lock' && !confirm(next?'Khóa tài khoản này? Người dùng sẽ không thể đăng nhập.':'Mở khóa tài khoản này?'))return
    setBusy(true)
    try {if(kind==='verify')await userService.verify(id,next);else await userService.lock(id,next);await Promise.all([client.invalidateQueries({queryKey:['admin-user',id]}),client.invalidateQueries({queryKey:['admin-users']}),client.invalidateQueries({queryKey:['admin-audit']})]);toast.success('Đã lưu thay đổi')}
    catch(e){toast.error(errorMessage(e))}finally{setBusy(false)}
  }
  const u=query.data?.user
  return <main className="admin-page"><Link to="/users">← Danh sách người dùng</Link>{query.isPending && <div className="admin-empty">Đang tải hồ sơ...</div>}{query.isError && <div className="admin-error">{errorMessage(query.error)} <button onClick={()=>query.refetch()}>Thử lại</button></div>}{u && <><h1>{u.fullName||u.email}</h1><div className="admin-grid"><section className="admin-panel"><p><strong>UID:</strong> {u.id}</p><p><strong>Email:</strong> {u.email}</p><p><strong>Điện thoại:</strong> {u.phone||'—'}</p><p><strong>Vai trò:</strong> {u.role}</p><p><strong>Địa bàn:</strong> {u.district}, {u.province}</p><p><strong>Tạo:</strong> {dateTime(u.createdAt)}</p></section><section className="admin-panel"><h2>Hoạt động</h2><p>Lượt quét: {query.data?.stats.scans}</p><p>Ruộng: {query.data?.stats.fields}</p><p>Cảnh báo chưa đọc: {query.data?.stats.unreadAlerts}</p></section></div><div className="admin-grid"><section className="admin-panel"><h2>Xác minh chuyên gia</h2><p>{u.verifiedExpert?'Đã xác minh':'Chưa xác minh'}</p><p className="admin-muted">Người thực hiện: {u.verifiedBy||'—'} · {dateTime(u.verifiedAt)}</p><button disabled={busy||(!u.verifiedExpert&&u.role!=='EXPERT')} onClick={()=>act('verify',!u.verifiedExpert)}>{u.verifiedExpert?'Hủy xác minh':'Xác minh chuyên gia'}</button></section><section className="admin-panel"><h2>Trạng thái tài khoản</h2><p>{u.locked?'Đã khóa':'Đang mở'}</p><p className="admin-muted">Người thực hiện: {u.lockedBy||'—'} · {dateTime(u.lockedAt)}</p><button disabled={busy} onClick={()=>act('lock',!u.locked)}>{u.locked?'Mở khóa':'Khóa tài khoản'}</button></section></div><p><Link to={`/audit?targetId=${encodeURIComponent(id)}`}>Xem thao tác quản trị đối với tài khoản này</Link></p></>}
  </main>
}

