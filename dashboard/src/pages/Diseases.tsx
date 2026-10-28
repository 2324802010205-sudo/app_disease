import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { errorMessage } from '../api/client'
import { diseaseService } from '../api/services'
import { useDisease, useDiseases } from '../hooks/useQueries'
import type { Disease, DiseaseDraft } from '../types'

const empty: DiseaseDraft={name:'',scientificName:'',description:'',part:'LEAF',cause:'FUNGUS',common:false,symptoms:[],conditions:[],prevention:[],treatment:[]}
const asDraft=(d:Disease):DiseaseDraft=>({name:d.name,scientificName:d.scientificName||'',description:d.description,part:d.part,cause:d.cause,common:d.common,symptoms:d.symptoms||[],conditions:d.conditions||[],prevention:d.prevention||[],treatment:d.treatment||[]})
function DiseaseForm({value,onChange,busy}:{value:DiseaseDraft;onChange:(next:DiseaseDraft)=>void;busy:boolean}) {
  const update=(patch:Partial<DiseaseDraft>)=>onChange({...value,...patch})
  return <div><div className="admin-toolbar"><input aria-label="Tên bệnh" placeholder="Tên bệnh" value={value.name} onChange={e=>update({name:e.target.value})} disabled={busy}/><input aria-label="Tên khoa học" placeholder="Tên khoa học" value={value.scientificName} onChange={e=>update({scientificName:e.target.value})} disabled={busy}/><select aria-label="Bộ phận" value={value.part} onChange={e=>update({part:e.target.value})}><option value="LEAF">Lá</option><option value="STEM">Thân</option><option value="GRAIN">Hạt</option></select><select aria-label="Tác nhân" value={value.cause} onChange={e=>update({cause:e.target.value})}><option value="FUNGUS">Nấm</option><option value="BACTERIA">Vi khuẩn</option><option value="VIRUS">Virus</option><option value="OTHER">Khác</option></select><label><input type="checkbox" checked={value.common} onChange={e=>update({common:e.target.checked})}/> Phổ biến</label></div><label>Mô tả<textarea value={value.description} onChange={e=>update({description:e.target.value})} disabled={busy}/></label>{(['symptoms','conditions','prevention','treatment'] as const).map(key=><label key={key} style={{display:'block',marginTop:14}}>{({symptoms:'Triệu chứng',conditions:'Điều kiện phát sinh',prevention:'Phòng ngừa',treatment:'Điều trị'})[key]} — mỗi dòng một mục<textarea value={value[key].join('\n')} onChange={e=>update({[key]:e.target.value.split('\n').map(x=>x.trim()).filter(Boolean)})} disabled={busy}/></label>)}</div>
}
export function Diseases() {
  const [search,setSearch]=useState('')
  const [part,setPart]=useState('')
  const [cause,setCause]=useState('')
  const [visibility,setVisibility]=useState('ALL')
  const query=useDiseases({})
  const rows=query.data?.filter(d=>(!search||`${d.name} ${d.description} ${d.id}`.toLowerCase().includes(search.toLowerCase()))&&(!part||d.part===part)&&(!cause||d.cause===cause)&&(visibility==='ALL'||(visibility==='HIDDEN')===!!d.hidden))??[]
  return <main className="admin-page"><h1>Thư viện bệnh lúa</h1><p>Thêm, biên tập và quản lý trạng thái hiển thị</p><div className="admin-toolbar"><Link className="admin-action" to="/diseases/new">Thêm bệnh</Link><input aria-label="Tìm bệnh" placeholder="Tên, mã hoặc mô tả" value={search} onChange={e=>setSearch(e.target.value)}/><select aria-label="Bộ phận" value={part} onChange={e=>setPart(e.target.value)}><option value="">Mọi bộ phận</option><option value="LEAF">Lá</option><option value="STEM">Thân</option><option value="GRAIN">Hạt</option></select><select aria-label="Tác nhân" value={cause} onChange={e=>setCause(e.target.value)}><option value="">Mọi tác nhân</option><option value="FUNGUS">Nấm</option><option value="BACTERIA">Vi khuẩn</option><option value="VIRUS">Virus</option><option value="OTHER">Khác</option></select><select aria-label="Hiển thị" value={visibility} onChange={e=>setVisibility(e.target.value)}><option value="ALL">Tất cả</option><option value="VISIBLE">Đang hiện</option><option value="HIDDEN">Đã ẩn</option></select></div>{query.isPending&&<div className="admin-empty">Đang tải thư viện...</div>}{query.isError&&<div className="admin-error">{errorMessage(query.error)} <button onClick={()=>query.refetch()}>Thử lại</button></div>}{query.isSuccess&&rows.length===0&&<div className="admin-empty">Không có bệnh phù hợp</div>}<div className="admin-grid">{rows.map(d=><section className="admin-panel" key={d.id}>{d.imageUrl&&<img className="preview" src={d.imageUrl} alt={d.name}/>}<h2><Link to={`/diseases/${encodeURIComponent(d.id)}`}>{d.name}</Link></h2><p>{d.description}</p><p>{d.part} · {d.cause} · {d.hidden?'Đã ẩn':'Đang hiện'}</p><small>{d.adminEdited?'Do admin biên tập':'Từ seed'}</small></section>)}</div></main>
}
export function DiseaseCreate() {
  const navigate=useNavigate(),client=useQueryClient()
  const [id,setId]=useState(''),[draft,setDraft]=useState<DiseaseDraft>(empty),[busy,setBusy]=useState(false)
  async function save(){setBusy(true);try{const d=await diseaseService.create(id,draft);await client.invalidateQueries({queryKey:['diseases']});toast.success('Đã thêm bệnh');navigate(`/diseases/${encodeURIComponent(d.id)}`)}catch(e){toast.error(errorMessage(e))}finally{setBusy(false)}}
  return <main className="admin-page"><Link to="/diseases">← Thư viện bệnh</Link><h1>Thêm bệnh</h1><section className="admin-panel"><label>Mã bệnh <input value={id} onChange={e=>setId(e.target.value.toLowerCase())} placeholder="vi-du-benh-lua"/></label><DiseaseForm value={draft} onChange={setDraft} busy={busy}/><button className="primary" disabled={busy||!id||!draft.name.trim()||!draft.description.trim()} onClick={save}>Lưu bệnh</button></section></main>
}
export function DiseaseDetail() {
  const {id=''}=useParams(),client=useQueryClient()
  const query=useDisease(id)
  const [draft,setDraft]=useState<DiseaseDraft>(empty),[busy,setBusy]=useState(false)
  useEffect(()=>{if(query.data)setDraft(asDraft(query.data))},[query.data])
  async function save(){setBusy(true);try{await diseaseService.update(id,draft);await Promise.all([client.invalidateQueries({queryKey:['disease',id]}),client.invalidateQueries({queryKey:['diseases']})]);toast.success('Đã lưu bệnh')}catch(e){toast.error(errorMessage(e))}finally{setBusy(false)}}
  async function toggle(hidden:boolean){setBusy(true);try{await diseaseService.visibility(id,hidden);await Promise.all([client.invalidateQueries({queryKey:['disease',id]}),client.invalidateQueries({queryKey:['diseases']})]);toast.success(hidden?'Đã ẩn bệnh':'Đã bỏ ẩn bệnh')}catch(e){toast.error(errorMessage(e))}finally{setBusy(false)}}
  async function upload(file?:File){if(!file||!query.data)return;setBusy(true);try{await diseaseService.uploadImage(id,file);await Promise.all([client.invalidateQueries({queryKey:['disease',id]}),client.invalidateQueries({queryKey:['diseases']})]);toast.success('Đã thay ảnh bệnh')}catch(e){toast.error(errorMessage(e))}finally{setBusy(false)}}
  return <main className="admin-page"><Link to="/diseases">← Thư viện bệnh</Link>{query.isPending&&<div className="admin-empty">Đang tải bệnh...</div>}{query.isError&&<div className="admin-error">{errorMessage(query.error)}</div>}{query.data&&<><h1>{query.data.name}</h1><p>Mã: {id} · {query.data.hidden?'Đã ẩn':'Đang hiện'} · {query.data.adminEdited?'Do admin biên tập':'Từ seed'}</p><section className="admin-panel">{query.data.imageUrl&&<img className="preview" src={query.data.imageUrl} alt={query.data.name}/>}<p><label>Thay ảnh <input type="file" accept="image/*" disabled={busy} onChange={e=>upload(e.target.files?.[0])}/></label></p><DiseaseForm value={draft} onChange={setDraft} busy={busy}/><div className="admin-actions"><button className="primary" disabled={busy||!draft.name.trim()||!draft.description.trim()} onClick={save}>Lưu nội dung</button><button disabled={busy} onClick={()=>toggle(!query.data!.hidden)}>{query.data.hidden?'Bỏ ẩn':'Ẩn bệnh'}</button></div></section></>}</main>
}
