import { useState } from 'react'
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { campaignService, userService } from '../api/services'
import { errorMessage } from '../api/client'
import { dateTime } from '../lib/admin'
import type { Campaign, CampaignDraft, CampaignPreview } from '../types'

const initial:CampaignDraft={title:'',body:'',audience:'PROVINCE',province:'',userIds:[]}
export function Campaigns() {
  const client=useQueryClient()
  const [draft,setDraft]=useState<CampaignDraft>(initial)
  const [preview,setPreview]=useState<CampaignPreview|null>(null)
  const [saved,setSaved]=useState<Campaign|null>(null)
  const [busy,setBusy]=useState(false)
  const [userSearch,setUserSearch]=useState('')
  const [userQ,setUserQ]=useState('')
  const users=useInfiniteQuery({queryKey:['campaign-users',userQ],queryFn:({pageParam})=>userService.list({q:userQ},pageParam),initialPageParam:null as string|null,getNextPageParam:p=>p.nextCursor,enabled:draft.audience==='SELECTED'})
  const campaigns=useInfiniteQuery({queryKey:['campaigns'],queryFn:({pageParam})=>campaignService.list(pageParam),initialPageParam:null as string|null,getNextPageParam:p=>p.nextCursor,refetchInterval:query=>query.state.data?.pages.some(p=>p.items.some(c=>c.status==='SENDING'))?3000:false})
  function edit(patch:Partial<CampaignDraft>){setDraft(d=>({...d,...patch}));setPreview(null);setSaved(null)}
  function toggle(uid:string){edit({userIds:draft.userIds?.includes(uid)?draft.userIds.filter(x=>x!==uid):[...(draft.userIds||[]),uid]})}
  async function previewDraft(){setBusy(true);try{setPreview(await campaignService.preview(draft))}catch(e){toast.error(errorMessage(e))}finally{setBusy(false)}}
  async function save(){setBusy(true);try{const result=await campaignService.create(draft);setSaved(result);await client.invalidateQueries({queryKey:['campaigns']});toast.success('Đã lưu chiến dịch nháp')}catch(e){toast.error(errorMessage(e))}finally{setBusy(false)}}
  async function send(id:string){if(!confirm('Gửi thông báo cho người nhận của chiến dịch này?'))return;setBusy(true);try{const result=await campaignService.send(id);await client.invalidateQueries({queryKey:['campaigns']});setSaved(result);toast.success('Đã bắt đầu gửi chiến dịch. Theo dõi kết quả trong lịch sử.')}catch(e){toast.error(errorMessage(e))}finally{setBusy(false)}}
  const history=campaigns.data?.pages.flatMap(p=>p.items)??[]
  return <main className="admin-page"><h1>Thông báo từ admin</h1><p>Soạn, xem trước người nhận, lưu nháp và gửi chiến dịch</p><section className="admin-panel"><h2>Soạn thông báo</h2><div className="admin-toolbar"><input aria-label="Tiêu đề" maxLength={120} placeholder="Tiêu đề" value={draft.title} onChange={e=>edit({title:e.target.value})}/><select aria-label="Nhóm người nhận" value={draft.audience} onChange={e=>edit({audience:e.target.value as CampaignDraft['audience']})}><option value="PROVINCE">Theo tỉnh/thành</option><option value="SELECTED">Chọn người dùng</option><option value="ALL">Toàn bộ người dùng</option></select>{draft.audience==='PROVINCE'&&<input aria-label="Tỉnh/thành" placeholder="Tên tỉnh/thành" value={draft.province||''} onChange={e=>edit({province:e.target.value})}/>}</div><textarea aria-label="Nội dung thông báo" maxLength={2000} placeholder="Nội dung thông báo" value={draft.body} onChange={e=>edit({body:e.target.value})}/>
    {draft.audience==='SELECTED'&&<div><h3>Chọn người nhận ({draft.userIds?.length||0})</h3><form className="admin-toolbar" onSubmit={e=>{e.preventDefault();setUserQ(userSearch)}}><input aria-label="Tìm người nhận" placeholder="Tên, email, UID" value={userSearch} onChange={e=>setUserSearch(e.target.value)}/><button>Tìm</button></form>{users.isPending&&<p>Đang tải người dùng...</p>}{users.isError&&<div className="admin-error">{errorMessage(users.error)}</div>}{users.data?.pages.flatMap(p=>p.items).map(u=><label key={u.id} style={{display:'block',margin:'8px 0'}}><input type="checkbox" checked={draft.userIds?.includes(u.id)||false} onChange={()=>toggle(u.id)}/> {u.fullName} · {u.email}</label>)}{users.hasNextPage&&<button onClick={()=>users.fetchNextPage()} disabled={users.isFetchingNextPage}>Tải thêm người dùng</button>}</div>}
    <div className="admin-toolbar"><button disabled={busy||!draft.title.trim()||!draft.body.trim()} onClick={previewDraft}>Xem trước người nhận</button><button disabled={busy||!preview} onClick={save}>Lưu nháp</button>{saved?.status==='DRAFT'&&<button className="primary" disabled={busy} onClick={()=>send(saved.id)}>Gửi chiến dịch đã lưu</button>}</div>
    {preview&&<div className="admin-panel"><strong>{preview.count} người nhận phù hợp</strong><p>{draft.title}</p><p>{draft.body}</p><div className="admin-muted">{preview.recipients.slice(0,20).map(r=><div key={r.id}>{r.name} · {r.province} · {r.id}</div>)}</div>{preview.count>20&&<p>Hiển thị 20 người đầu tiên.</p>}</div>}
  </section><h2>Lịch sử chiến dịch</h2>{campaigns.isPending&&<div className="admin-empty">Đang tải lịch sử...</div>}{campaigns.isError&&<div className="admin-error">{errorMessage(campaigns.error)} <button onClick={()=>campaigns.refetch()}>Thử lại</button></div>}{campaigns.isSuccess&&history.length===0&&<div className="admin-empty">Chưa có chiến dịch</div>}{history.length>0&&<div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Chiến dịch</th><th>Người tạo</th><th>Nhóm nhận</th><th>Trạng thái</th><th>Đã tạo / lỗi</th><th>Thời gian</th><th>Thao tác</th></tr></thead><tbody>{history.map(c=><tr key={c.id}><td>{c.title}<div className="admin-muted">{c.id}</div></td><td>{c.createdBy}</td><td>{c.audience}{c.province?`: ${c.province}`:''}</td><td>{c.status}</td><td>{c.delivered} / {c.failed}</td><td>{dateTime(c.sentAt||c.createdAt)}</td><td>{(c.status==='DRAFT'||c.status==='PARTIAL'||(c.status==='SENDING'&&!!c.startedAt&&Date.now()-c.startedAt>900000))&&<button disabled={busy} onClick={()=>send(c.id)}>{c.status==='DRAFT'?'Gửi':'Thử lại'}</button>}</td></tr>)}</tbody></table></div>}{campaigns.hasNextPage&&<div className="admin-pagination"><button disabled={campaigns.isFetchingNextPage} onClick={()=>campaigns.fetchNextPage()}>Tải tiếp lịch sử</button></div>}
  </main>
}

