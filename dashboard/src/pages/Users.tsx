import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, BadgeCheck, Calendar, Lock, LockOpen, Mail, MapPin, Phone, ScanLine, ShieldCheck, Sprout, TriangleAlert, User, UsersRound } from 'lucide-react'
import { toast } from 'sonner'
import { userService } from '../api/services'
import { errorMessage } from '../api/client'
import { Button, initials, Page, PageHeader, Panel, Pill, QueryState, SearchBox, Stat, useConfirm } from '../components/ui'
import { shortTime } from '../lib/admin'

export function Users() {
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [role, setRole] = useState('')
  const [province, setProvince] = useState('')
  const [verified, setVerified] = useState('ALL')
  const [locked, setLocked] = useState('ALL')
  const filters = { q, role, province, verified: verified === 'ALL' ? undefined : verified === 'YES', locked: locked === 'ALL' ? undefined : locked === 'YES' }
  const query = useInfiniteQuery({ queryKey: ['admin-users', filters], queryFn: ({ pageParam }) => userService.list(filters, pageParam), initialPageParam: null as string | null, getNextPageParam: p => p.nextCursor })
  const rows = query.data?.pages.flatMap(p => p.items) ?? []
  return <Page art="field">
    <PageHeader title="Người dùng" description="Tìm kiếm hồ sơ, xác minh chuyên gia và quản lý tài khoản." />
    <Panel flush>
      <div style={{ padding: '16px 18px' }}><div className="toolbar">
        <SearchBox placeholder="Tên, email hoặc UID" value={search} onChange={setSearch} onSubmit={() => setQ(search)} />
        <input className="field-input" aria-label="Tỉnh" placeholder="Tỉnh / thành" value={province} onChange={e => setProvince(e.target.value)} />
        <select className="field-select" aria-label="Vai trò" value={role} onChange={e => setRole(e.target.value)}><option value="">Mọi vai trò</option><option value="FARMER">Nông dân</option><option value="EXPERT">Chuyên gia</option></select>
        <select className="field-select" aria-label="Xác minh" value={verified} onChange={e => setVerified(e.target.value)}><option value="ALL">Mọi xác minh</option><option value="YES">Đã xác minh</option><option value="NO">Chưa xác minh</option></select>
        <select className="field-select" aria-label="Khóa tài khoản" value={locked} onChange={e => setLocked(e.target.value)}><option value="ALL">Mọi tài khoản</option><option value="YES">Đã khóa</option><option value="NO">Đang mở</option></select>
      </div></div>
      <QueryState query={query} empty={rows.length === 0} emptyText="Không có người dùng phù hợp" />
      {rows.length > 0 && <div className="table-wrap"><table className="data-table"><thead><tr><th>Người dùng</th><th>Vai trò</th><th>Địa bàn</th><th>Chuyên gia</th><th>Tài khoản</th><th>Ngày tạo</th></tr></thead><tbody>{rows.map(u => <tr key={u.id}>
        <td><div style={{ display: 'flex', gap: 12, alignItems: 'center' }}><div className="avatar">{initials(u.fullName)}</div><div><Link className="cell-title" to={`/users/${encodeURIComponent(u.id)}`}>{u.fullName || u.email}</Link><div className="cell-sub">{u.email}</div></div></div></td>
        <td>{u.role === 'EXPERT' ? 'Chuyên gia' : u.role === 'FARMER' ? 'Nông dân' : u.role}</td>
        <td>{[u.district, u.province].filter(Boolean).join(', ') || 'Chưa có'}</td>
        <td>{u.verifiedExpert ? <Pill tone="success">Đã xác minh</Pill> : <Pill>Chưa xác minh</Pill>}</td>
        <td>{u.locked ? <Pill tone="danger">Đã khóa</Pill> : <Pill tone="success">Đang mở</Pill>}</td>
        <td className="nowrap">{shortTime(u.createdAt)}</td>
      </tr>)}</tbody></table></div>}
      {query.hasNextPage && <div className="table-foot"><span>Đã tải {rows.length} người dùng</span><Button size="sm" variant="secondary" disabled={query.isFetchingNextPage} onClick={() => query.fetchNextPage()}>Tải thêm</Button></div>}
    </Panel>
  </Page>
}
export function UserDetail() {
  const { id = '' } = useParams()
  const client = useQueryClient()
  const query = useQuery({ queryKey: ['admin-user', id], queryFn: () => userService.detail(id), enabled: !!id })
  const [busy, setBusy] = useState(false)
  const confirm = useConfirm()
  const u = query.data?.user
  async function act(kind: 'verify' | 'lock', next: boolean) {
    setBusy(true)
    try { if (kind === 'verify') await userService.verify(id, next); else await userService.lock(id, next); await Promise.all(['admin-user', 'admin-users', 'admin-audit'].map(k => client.invalidateQueries({ queryKey: [k] }))); toast.success('Đã lưu thay đổi') }
    catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) }
  }
  const lock = (next: boolean) => confirm.ask({ title: next ? 'Khóa tài khoản này?' : 'Mở khóa tài khoản này?', description: next ? `${u?.fullName || u?.email} sẽ không thể đăng nhập và các phiên hiện có bị thu hồi.` : `${u?.fullName || u?.email} có thể đăng nhập trở lại.`, confirmText: next ? 'Khóa tài khoản' : 'Mở khóa', destructive: next, icon: next ? Lock : LockOpen, onConfirm: () => act('lock', next) })
  return <Page art="field">
    <PageHeader title={u?.fullName || 'Hồ sơ người dùng'} description={u?.email} actions={<Link className="back-chip" to="/users"><ArrowLeft size={16} />Danh sách người dùng</Link>} />
    {!u && <Panel><QueryState query={query} /></Panel>}
    {u && <>
      <div className="grid grid-3">
        <Stat icon={ScanLine} label="Lượt quét" value={query.data?.stats.scans ?? 0} />
        <Stat icon={Sprout} label="Ruộng" value={query.data?.stats.fields ?? 0} />
        <Stat icon={TriangleAlert} tone="warning" label="Cảnh báo chưa đọc" value={query.data?.stats.unreadAlerts ?? 0} />
      </div>
      <div className="grid grid-main-side">
        <Panel icon={User} title="Thông tin hồ sơ"><div className="facts">
          <div className="mod-row"><User size={17} />UID<b>{u.id}</b></div>
          <div className="mod-row"><Mail size={17} />Email<b>{u.email}</b></div>
          <div className="mod-row"><Phone size={17} />Điện thoại<b>{u.phone || 'Chưa có'}</b></div>
          <div className="mod-row"><UsersRound size={17} />Vai trò<b>{u.role === 'EXPERT' ? 'Chuyên gia' : 'Nông dân'}</b></div>
          <div className="mod-row"><MapPin size={17} />Địa bàn<b>{[u.district, u.province].filter(Boolean).join(', ') || 'Chưa có'}</b></div>
          <div className="mod-row"><Calendar size={17} />Ngày tạo<b>{shortTime(u.createdAt)}</b></div>
        </div></Panel>
        <div className="stack">
          <Panel icon={BadgeCheck} title="Xác minh chuyên gia" description={u.verifiedAt ? `Cập nhật ${shortTime(u.verifiedAt)}` : 'Chỉ áp dụng cho tài khoản vai trò chuyên gia'}><div style={{ marginBottom: 12 }}>{u.verifiedExpert ? <Pill tone="success">Đã xác minh</Pill> : <Pill>Chưa xác minh</Pill>}</div><Button block variant="outline" disabled={busy || (!u.verifiedExpert && u.role !== 'EXPERT')} onClick={() => act('verify', !u.verifiedExpert)}>{u.verifiedExpert ? 'Hủy xác minh' : 'Xác minh chuyên gia'}</Button></Panel>
          <Panel icon={ShieldCheck} title="Trạng thái tài khoản" description={u.lockedAt ? `Cập nhật ${shortTime(u.lockedAt)}` : undefined}><div style={{ marginBottom: 12 }}>{u.locked ? <Pill tone="danger">Đã khóa</Pill> : <Pill tone="success">Đang mở</Pill>}</div><Button block variant={u.locked ? 'outline' : 'danger'} disabled={busy} onClick={() => lock(!u.locked)}>{u.locked ? 'Mở khóa tài khoản' : 'Khóa tài khoản'}</Button></Panel>
          <Link className="link" to={`/audit?targetId=${encodeURIComponent(id)}`}>Xem thao tác quản trị đối với tài khoản này</Link>
        </div>
      </div>
    </>}
    {confirm.node}
  </Page>
}
