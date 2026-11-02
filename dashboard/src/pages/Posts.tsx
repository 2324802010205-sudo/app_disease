import { useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, ArrowLeft, Calendar, CheckCircle2, Clock3, EyeOff, FileText, Filter, Globe, Image as ImageIcon, Info, MapPin, MessageCircle, MessageSquareText, Search, ShieldCheck, ThumbsUp, Trash2, User } from 'lucide-react'
import { toast } from 'sonner'
import { errorMessage } from '../api/client'
import { postService, statsService } from '../api/services'
import { Button, initials, Page, PageHeader, Pager, Panel, Pill, QueryState, SearchBox, Stat, useConfirm } from '../components/ui'
import { useComments, usePost, usePosts } from '../hooks/useQueries'
import { shortTime, TOPICS } from '../lib/admin'
import type { AdminPost, Comment } from '../types'

const statusOf = (p: AdminPost) => p.hidden ? { label: 'Đã ẩn', tone: 'neutral' as const } : p.pendingReports ? { label: 'Cần xem lại', tone: 'warning' as const } : { label: 'Đang hiển thị', tone: 'success' as const }

export function Posts() {
  const [params] = useSearchParams()
  const [topic, setTopic] = useState('')
  const [status, setStatus] = useState('ALL')
  const [reported, setReported] = useState('ALL')
  const [authorId, setAuthorId] = useState('')
  const [advanced, setAdvanced] = useState(false)
  const [search, setSearch] = useState(params.get('q') || '')
  const [q, setQ] = useState(params.get('q') || '')
  const [page, setPage] = useState(0)
  const query = usePosts(topic, q, status, { authorId, reported: reported === 'ALL' ? undefined : reported === 'YES' })
  const stats = useQuery({ queryKey: ['admin-stats'], queryFn: statsService.get })
  const s = stats.data?.posts
  const rows = query.data?.pages[page]?.items ?? []
  const loaded = query.data?.pages.reduce((a, p) => a + p.items.length, 0) ?? 0
  async function changePage(index: number) {
    if (index < (query.data?.pages.length ?? 0)) { setPage(index); return }
    if (query.hasNextPage) { await query.fetchNextPage(); setPage(index) }
  }
  const reset = (fn: () => void) => { fn(); setPage(0) }
  return <Page art="field">
    <PageHeader title="Bài viết cộng đồng" description="Theo dõi thảo luận và giữ nội dung hữu ích cho người trồng lúa." />
    <div className="grid grid-3">
      <Stat icon={FileText} label="Tổng bài viết" value={s?.total ?? '—'} sub={`${s?.visible ?? 0} đang hiển thị, ${s?.hidden ?? 0} đã ẩn`} />
      <Stat icon={MessageSquareText} label="Bài viết mới hôm nay" value={s?.today ?? '—'} sub="Tính theo giờ Việt Nam" />
      <Stat icon={AlertTriangle} tone="warning" label="Bài viết cần chú ý" value={s?.reported ?? '—'} sub="Có báo cáo chưa xử lý" subTone={s?.reported ? 'warn' : undefined} onClick={() => reset(() => setReported('YES'))} />
    </div>
    <Panel flush>
      <div style={{ padding: '16px 18px' }}>
        <div className="toolbar">
          <SearchBox placeholder="Tìm theo tiêu đề hoặc nội dung" value={search} onChange={setSearch} onSubmit={() => reset(() => setQ(search))} style={{ flexBasis: 300 }} />
          <Button onClick={() => reset(() => setQ(search))}><Search size={16} />Tìm</Button>
          <select className="field-select" aria-label="Chủ đề" value={topic} onChange={e => reset(() => setTopic(e.target.value))}><option value="">Tất cả chủ đề</option>{Object.entries(TOPICS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}</select>
          <select className="field-select" aria-label="Trạng thái" value={status} onChange={e => reset(() => setStatus(e.target.value))}><option value="ALL">Mọi trạng thái</option><option value="VISIBLE">Đang hiển thị</option><option value="HIDDEN">Đã ẩn</option></select>
          <Button variant="secondary" onClick={() => setAdvanced(v => !v)}><Filter size={16} />Lọc nâng cao</Button>
          <span className="count-chip toolbar-push">Hiển thị {rows.length} bài viết</span>
        </div>
        {advanced && <div className="toolbar" style={{ marginTop: 11 }}><input className="field-input" aria-label="UID tác giả" placeholder="UID tác giả" value={authorId} onChange={e => reset(() => setAuthorId(e.target.value))} /><select className="field-select" aria-label="Bị báo cáo" value={reported} onChange={e => reset(() => setReported(e.target.value))}><option value="ALL">Mọi bài viết</option><option value="YES">Có báo cáo</option><option value="NO">Không có báo cáo</option></select></div>}
      </div>
      <QueryState query={query} empty={rows.length === 0} emptyText="Không có bài viết phù hợp" />
      {rows.length > 0 && <div className="table-wrap"><table className="data-table"><thead><tr><th>Bài viết</th><th>Tác giả</th><th>Chủ đề</th><th>Ngày đăng</th><th>Hoạt động</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{rows.map(p => {
        const t = TOPICS[p.topic] ?? { label: p.topic, tone: 'info' as const }, st = statusOf(p), img = p.images?.[0]?.url
        return <tr key={p.id}>
          <td><div className="row-media">{img ? <img className="thumb" src={img} alt="" loading="lazy" /> : <div className="thumb"><ImageIcon size={22} /></div>}<div style={{ minWidth: 0, maxWidth: 340 }}><Link className="cell-title" to={`/posts/${encodeURIComponent(p.id)}`}>{p.title}</Link><div className="cell-sub cell-clip">{p.content}</div></div></div></td>
          <td className="nowrap">{p.authorName || p.authorId.slice(0, 10)}</td>
          <td><Pill tone={t.tone}>{t.label}</Pill></td>
          <td className="nowrap">{shortTime(p.createdAt)}</td>
          <td><span className="meta-icons"><span><ThumbsUp size={15} />{p.likeCount}</span><span><MessageCircle size={15} />{p.commentCount}</span></span></td>
          <td><Pill tone={st.tone}>{st.label}</Pill></td>
          <td><Link className="link" to={`/posts/${encodeURIComponent(p.id)}`}>Xem bài viết</Link></td>
        </tr>
      })}</tbody></table></div>}
      {query.isSuccess && rows.length > 0 && <Pager page={page} pages={query.data.pages.length} hasNext={!!query.hasNextPage} loading={query.isFetchingNextPage} onPage={changePage} label={`Đã tải ${loaded} bài viết`} />}
    </Panel>
  </Page>
}

function CommentRow({ c, reply, busy, onRemove }: { c: Comment; reply?: boolean; busy: boolean; onRemove: (c: Comment) => void }) {
  return <div className={`comment${reply ? ' reply' : ''}`}><div className="avatar" style={reply ? { width: 32, height: 32, fontSize: 11 } : undefined}>{initials(c.author?.name)}</div><div className="grow"><div className="comment-head"><strong>{c.author?.name || 'Người dùng'}</strong><small>{shortTime(c.createdAt)}</small>{c.badge === 'EXPERT' && <Pill tone="success" bare>Chuyên gia</Pill>}{c.badge === 'AUTHOR' && <Pill tone="info" bare>Tác giả</Pill>}{c.likeCount > 0 && <span className="meta-icons cell-sub"><span><ThumbsUp size={13} />{c.likeCount}</span></span>}</div><p>{c.text}</p></div><button className="comment-del" disabled={busy} onClick={() => onRemove(c)}><Trash2 size={15} />Xóa bình luận</button></div>
}

export function PostDetail() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const client = useQueryClient()
  const post = usePost(id)
  const comments = useComments(id)
  const [busy, setBusy] = useState(false)
  const confirm = useConfirm()
  const p = post.data
  const refresh = () => Promise.all([client.invalidateQueries({ queryKey: ['post', id] }), client.invalidateQueries({ queryKey: ['posts'] }), client.invalidateQueries({ queryKey: ['admin-stats'] })])
  async function visibility(hidden: boolean) {
    setBusy(true)
    try { await postService.visibility(id, hidden); await refresh(); toast.success(hidden ? 'Đã ẩn bài viết' : 'Bài viết đang hiển thị') }
    catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  const removePost = () => confirm.ask({ title: 'Xóa bài viết này?', description: `Bài viết "${p?.title}" cùng toàn bộ bình luận sẽ bị xóa vĩnh viễn.`, confirmText: 'Xóa bài viết', destructive: true, icon: Trash2, onConfirm: async () => {
    try { await postService.remove(id); await refresh(); toast.success('Đã xóa bài viết'); navigate('/posts') } catch (e) { toast.error(errorMessage(e)) }
  } })
  const removeComment = (c: Comment) => confirm.ask({ title: 'Xóa bình luận này?', description: `Bình luận của ${c.author?.name || 'người dùng'}${c.replies?.length ? ` và ${c.replies.length} trả lời` : ''} sẽ bị xóa.`, confirmText: 'Xóa bình luận', destructive: true, icon: Trash2, onConfirm: async () => {
    try { await postService.removeComment(id, c.id); await Promise.all([client.invalidateQueries({ queryKey: ['comments', id] }), refresh()]); toast.success('Đã xóa bình luận') } catch (e) { toast.error(errorMessage(e)) }
  } })
  const t = p ? TOPICS[p.topic] ?? { label: p.topic, tone: 'info' as const } : null
  const st = p ? statusOf(p) : null
  return <Page art="rice">
    <PageHeader title="Chi tiết bài viết" description="Xem nội dung và thực hiện kiểm duyệt khi cần." actions={<Link className="back-chip" to="/posts"><ArrowLeft size={16} />Danh sách bài viết</Link>} />
    {!p && <Panel><QueryState query={post} /></Panel>}
    {p && <div className="detail-grid">
      <div className="stack">
        <Panel>
          <div className="author-row"><div className="avatar">{initials(p.authorName)}</div><div className="grow"><strong>{p.authorName || p.authorId}</strong><small>{shortTime(p.createdAt)}{p.province ? `  •  ${p.province}` : ''}</small></div>{t && <Pill tone={t.tone}>{t.label}</Pill>}</div>
          <h2 className="article-title">{p.title}</h2>
          {p.images?.[0] && <a href={p.images[0].url} target="_blank" rel="noreferrer"><img className="article-photo" src={p.images[0].url} alt="Ảnh bài viết" /></a>}
          {p.images?.length > 1 && <div className="photo-grid">{p.images.slice(1).map((img, i) => <a key={i} href={img.url} target="_blank" rel="noreferrer"><img src={img.url} alt={`Ảnh ${i + 2}`} /></a>)}</div>}
          <p className="article-body">{p.content}</p>
          <div className="op-tags" style={{ margin: '0 0 14px' }}><Pill bare tone={p.hidden ? 'neutral' : 'success'}><Globe size={14} />{p.hidden ? 'Đang ẩn khỏi cộng đồng' : 'Đã đăng công khai'}</Pill>{!!p.pendingReports && <Pill bare tone="warning"><AlertTriangle size={14} />{p.pendingReports} báo cáo chưa xử lý</Pill>}</div>
          <div className="article-stats"><span><ThumbsUp size={16} />{p.likeCount} lượt thích</span><span><MessageCircle size={16} />{p.commentCount} bình luận</span></div>
        </Panel>
        <Panel icon={MessageSquareText} title="Bình luận" description="Nội dung thảo luận dưới bài viết.">
          <QueryState query={comments} empty={comments.data?.length === 0} emptyText="Chưa có bình luận" />
          {comments.data?.map(c => <div key={c.id}><CommentRow c={c} busy={busy} onRemove={removeComment} />{c.replies?.map(r => <CommentRow key={r.id} c={{ ...r, replies: [] }} reply busy={busy} onRemove={removeComment} />)}</div>)}
        </Panel>
      </div>
      <Panel icon={ShieldCheck} title="Kiểm duyệt" description="Xem xét nội dung bài viết và thực hiện thao tác quản lý phù hợp sau khi đánh giá.">
        <div className="mod-grid">
          <div className="mod-row"><Info size={17} />Trạng thái{st && <Pill tone={st.tone}>{st.label}</Pill>}</div>
          <div className="mod-row"><AlertTriangle size={17} />Báo cáo chờ<Pill tone={p.pendingReports ? 'warning' : 'success'}>{p.pendingReports || 'Không có'}</Pill></div>
          <div className="mod-row"><FileText size={17} />Chủ đề{t && <Pill tone={t.tone}>{t.label}</Pill>}</div>
        </div>
        <div className="divider" />
        <div className="facts">
          <div className="mod-row"><User size={17} />Tác giả<b>{p.authorName || p.authorId}</b></div>
          <div className="mod-row"><Calendar size={17} />Ngày đăng<b>{shortTime(p.createdAt)}</b></div>
          <div className="mod-row"><Clock3 size={17} />Cập nhật<b>{shortTime(p.updatedAt)}</b></div>
          <div className="mod-row"><MapPin size={17} />Địa bàn<b>{p.province || 'Chưa có'}</b></div>
          <div className="mod-row"><ThumbsUp size={17} />Tương tác<b>{p.likeCount} thích, {p.commentCount} bình luận</b></div>
        </div>
        <div className="divider" />
        <div className="stack" style={{ gap: 11 }}>
          <Button block disabled={busy || !p.hidden} onClick={() => visibility(false)}><CheckCircle2 size={18} />Giữ bài viết</Button>
          <Button block variant="outline" disabled={busy || p.hidden} onClick={() => visibility(true)}><EyeOff size={18} />Ẩn tạm thời</Button>
          <Button block variant="danger" disabled={busy} onClick={removePost}><Trash2 size={18} />Xóa bài viết</Button>
        </div>
      </Panel>
    </div>}
    {confirm.node}
  </Page>
}
