import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { errorMessage } from '../api/client'
import { postService, statsService } from '../api/services'
import { useComments, usePost, usePosts } from '../hooks/useQueries'
import { dateTime } from '../lib/admin'

export function Posts() {
  const client = useQueryClient()
  const [topic, setTopic] = useState('')
  const [status, setStatus] = useState('ALL')
  const [authorId, setAuthorId] = useState('')
  const [reported, setReported] = useState('ALL')
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [busy, setBusy] = useState(false)
  const query = usePosts(topic,q,status,{authorId,reported:reported==='ALL'?undefined:reported==='YES'})
  const stats = useQuery({ queryKey: ['admin-stats'], queryFn: statsService.get })
  const posts = query.data?.pages[page]?.items ?? []
  async function changePage(index: number) {
    if (index < (query.data?.pages.length ?? 0)) { setPage(index); return }
    if (query.hasNextPage) { await query.fetchNextPage(); setPage(index) }
  }
  async function toggle(id: string, hidden: boolean) {
    setBusy(true)
    try { await postService.visibility(id, hidden); await Promise.all([client.invalidateQueries({queryKey:['posts']}),client.invalidateQueries({queryKey:['post',id]}),client.invalidateQueries({queryKey:['admin-stats']})]); toast.success(hidden ? 'Đã ẩn bài viết' : 'Đã bỏ ẩn bài viết') }
    catch(e) {toast.error(errorMessage(e))} finally {setBusy(false)}
  }
  async function remove(id: string) {
    if (!confirm('Xóa bài viết này?')) return
    setBusy(true)
    try {await postService.remove(id); await Promise.all([client.invalidateQueries({queryKey:['posts']}),client.invalidateQueries({queryKey:['admin-stats']})]); toast.success('Đã xóa bài viết')}
    catch(e) {toast.error(errorMessage(e))} finally {setBusy(false)}
  }
  return <main className="admin-page"><h1>Bài viết cộng đồng</h1><p>Quản trị bài viết trên toàn hệ thống</p>
    {stats.data && <div className="admin-stats"><div className="admin-stat">Tổng bài viết<strong>{stats.data.posts.total}</strong></div><div className="admin-stat">Đang hiển thị<strong>{stats.data.posts.visible}</strong></div><div className="admin-stat">Đã ẩn<strong>{stats.data.posts.hidden}</strong></div><div className="admin-stat">Tạo hôm nay<strong>{stats.data.posts.today}</strong></div></div>}
    {stats.isError && <div className="admin-error">{errorMessage(stats.error)}</div>}
    <div className="admin-toolbar"><select aria-label="Trạng thái" value={status} onChange={e => {setStatus(e.target.value);setPage(0)}}><option value="ALL">Mọi trạng thái</option><option value="VISIBLE">Hiển thị</option><option value="HIDDEN">Đã ẩn</option></select>
      <select aria-label="Chủ đề" value={topic} onChange={e => {setTopic(e.target.value);setPage(0)}}><option value="">Mọi chủ đề</option>{['PEST','EXPERIENCE','VARIETY','WEATHER','WATER'].map(x => <option key={x}>{x}</option>)}</select>
      <input aria-label="UID tác giả" placeholder="UID tác giả" value={authorId} onChange={e => {setAuthorId(e.target.value);setPage(0)}}/>
      <select aria-label="Bị báo cáo" value={reported} onChange={e=>{setReported(e.target.value);setPage(0)}}><option value="ALL">Mọi bài viết</option><option value="YES">Có báo cáo</option><option value="NO">Không có báo cáo</option></select>
      <form onSubmit={e => {e.preventDefault();setQ(search);setPage(0)}}><input aria-label="Tìm kiếm" placeholder="Tiêu đề, nội dung, tác giả" value={search} onChange={e => setSearch(e.target.value)}/><button>Tìm</button></form>
    </div>
    {query.isPending && <div className="admin-empty">Đang tải bài viết...</div>}
    {query.isError && <div className="admin-error">{errorMessage(query.error)} <button onClick={() => query.refetch()}>Thử lại</button></div>}
    {query.isSuccess && posts.length === 0 && <div className="admin-empty">Không có bài viết phù hợp</div>}
    {posts.length > 0 && <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Tiêu đề</th><th>Tác giả</th><th>Chủ đề</th><th>Tương tác</th><th>Thời gian</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{posts.map(p => <tr key={p.id}><td><Link className="admin-link" to={`/posts/${encodeURIComponent(p.id)}`}>{p.title}</Link><div className="admin-muted">{p.id}</div></td><td>{p.authorId}</td><td>{p.topic}</td><td>{p.likeCount} thích · {p.commentCount} bình luận</td><td>{dateTime(p.createdAt)}</td><td>{p.hidden ? 'Đã ẩn' : 'Hiển thị'}</td><td><div className="admin-actions"><button disabled={busy} onClick={() => toggle(p.id,!p.hidden)}>{p.hidden ? 'Bỏ ẩn' : 'Ẩn'}</button><button disabled={busy} onClick={() => remove(p.id)}>Xóa</button></div></td></tr>)}</tbody></table></div>}
    {query.isSuccess && <div className="admin-pagination">{query.data.pages.map((_,index) => <button key={index} className={index === page ? 'primary' : ''} onClick={() => changePage(index)}>{index+1}</button>)}{query.hasNextPage && <button disabled={query.isFetchingNextPage} onClick={() => changePage(query.data.pages.length)}>{query.isFetchingNextPage ? 'Đang tải...' : 'Trang tiếp'}</button>}</div>}
  </main>
}
export function PostDetail() {
  const {id = ''} = useParams()
  const client = useQueryClient()
  const post = usePost(id)
  const comments = useComments(id)
  const [busy,setBusy] = useState(false)
  async function visibility(hidden: boolean) {setBusy(true);try {await postService.visibility(id,hidden);await client.invalidateQueries({queryKey:['post',id]});toast.success('Đã lưu trạng thái')}catch(e){toast.error(errorMessage(e))}finally{setBusy(false)}}
  async function removeComment(commentId: string) {if (!confirm('Xóa bình luận này?')) return;setBusy(true);try {await postService.removeComment(id,commentId);await client.invalidateQueries({queryKey:['comments',id]});toast.success('Đã xóa bình luận')}catch(e){toast.error(errorMessage(e))}finally{setBusy(false)}}
  return <main className="admin-page"><Link to="/posts">← Danh sách bài viết</Link>
    {post.isPending && <div className="admin-empty">Đang tải bài viết...</div>}
    {post.isError && <div className="admin-error">{errorMessage(post.error)}</div>}
    {post.data && <section className="admin-panel"><h1>{post.data.title}</h1><p>{post.data.authorId} · {dateTime(post.data.createdAt)} · {post.data.hidden ? 'Đã ẩn' : 'Hiển thị'}</p><p style={{whiteSpace:'pre-wrap'}}>{post.data.content}</p>{post.data.images?.map((img,i) => <a key={i} href={img.url} target="_blank" rel="noreferrer"><img className="preview" src={img.url} alt={`Ảnh bài viết ${i+1}`}/></a>)}<div className="admin-actions"><button disabled={busy} onClick={() => visibility(!post.data!.hidden)}>{post.data.hidden ? 'Bỏ ẩn' : 'Ẩn bài viết'}</button></div></section>}
    <h2>Bình luận</h2>{comments.isPending && <div className="admin-empty">Đang tải bình luận...</div>}{comments.isError && <div className="admin-error">{errorMessage(comments.error)}</div>}{comments.data?.length === 0 && <div className="admin-empty">Chưa có bình luận</div>}{comments.data?.map(c => <div className="admin-panel" key={c.id}><strong>{c.author?.name}</strong> · {dateTime(c.createdAt)}<p>{c.text}</p><button disabled={busy} onClick={() => removeComment(c.id)}>Xóa bình luận</button></div>)}
  </main>
}
