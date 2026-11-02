import { useState, useSyncExternalStore } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Activity, Bell, BookOpen, ChevronDown, ChevronLeft, ChevronRight, ClipboardCheck, FolderTree, History, LayoutGrid, LogOut, Menu, MessageSquareWarning, Moon, Search, ShieldCheck, Sprout, Sun, UsersRound, type LucideIcon } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { authService, statsService } from '../api/services'
import { clearSession, readSession, subscribeSession } from '../lib/session'
import { cn, initials } from './ui'

type Link = { to: string; label: string; icon: LucideIcon; exact?: boolean }
const links: Link[] = [
  { to: '/', label: 'Tổng quan', icon: LayoutGrid, exact: true },
  { to: '/reports', label: 'Báo cáo vi phạm', icon: MessageSquareWarning },
  { to: '/posts', label: 'Bài viết cộng đồng', icon: ClipboardCheck },
  { to: '/feedbacks', label: 'Phản hồi AI', icon: Activity },
  { to: '/dataset', label: 'Kho ảnh AI', icon: FolderTree },
  { to: '/diseases', label: 'Thư viện bệnh', icon: BookOpen },
  { to: '/risk', label: 'Vùng nguy cơ', icon: Sprout },
  { to: '/operations', label: 'Vận hành', icon: ShieldCheck },
]
const manage: Link[] = [
  { to: '/users', label: 'Người dùng', icon: UsersRound },
  { to: '/campaigns', label: 'Thông báo', icon: Bell },
  { to: '/audit', label: 'Nhật ký quản trị', icon: History },
]

let snapshot = readSession()
function currentSession() {
  const next = readSession()
  if (JSON.stringify(next) !== JSON.stringify(snapshot)) snapshot = next
  return snapshot
}
export function useSession() { return useSyncExternalStore(subscribeSession, currentSession) }

export function Layout() {
  const [collapsed, setCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [dark, setDark] = useState(localStorage.getItem('riceguard.theme') === 'dark')
  const [search, setSearch] = useState('')
  const navigate = useNavigate()
  const location = useLocation()
  const session = useSession()
  const stats = useQuery({ queryKey: ['admin-stats'], queryFn: statsService.get })
  const pendingCount = stats.data?.reports.pending ?? 0
    const current = [...links, ...manage].find(link => link.exact ? location.pathname === '/' : location.pathname.startsWith(link.to))

  const toggleTheme = () => {
    const next = !dark; setDark(next); localStorage.setItem('riceguard.theme', next ? 'dark' : 'light')
    document.documentElement.dataset.theme = next ? 'dark' : 'light'
  }
  const logout = async () => {
    try { await authService.logout() } catch { toast.error('Không thể thu hồi phiên trên máy chủ. Phiên trên trình duyệt đã được xóa.') }
    clearSession(); navigate('/login', { replace: true })
  }
  const submitSearch = (event: React.FormEvent) => {
    event.preventDefault()
    if (search.trim()) navigate(`/posts?q=${encodeURIComponent(search.trim())}`)
  }
  const item = ({ to, label, icon: Icon, exact }: Link) => <NavLink key={to} to={to} end={exact} onClick={() => setMobileOpen(false)} className={({ isActive }) => cn('nav-item', isActive && 'active')} title={collapsed ? label : undefined}><Icon size={20} /><span>{label}</span>{to === '/reports' && pendingCount > 0 && <b className="nav-count" aria-label={`${pendingCount} báo cáo chờ xử lý`}>{pendingCount}</b>}</NavLink>
  return <div className="app-shell">
    {mobileOpen && <button className="mobile-scrim" onClick={() => setMobileOpen(false)} aria-label="Đóng menu" />}
    <aside className={cn('sidebar', collapsed && 'sidebar-collapsed', mobileOpen && 'sidebar-open')}>
      <div className="brand"><div className="brand-mark"><img src="/riceguard-logo.png" alt="RiceGuard AI" /></div><div className="brand-copy"><strong>RiceGuard<span> AI</span></strong><small>Không gian quản trị</small></div></div>
      <div className="nav-scroll">
        <div className="nav-section-label">Điều hướng</div>
        <nav className="main-nav" aria-label="Điều hướng chính">{links.map(item)}</nav>
        <div className="nav-section-label">Quản lý</div>
        <nav className="main-nav" aria-label="Quản lý">{manage.map(item)}</nav>
      </div>
      <div className="sidebar-bottom">
        <div className="side-note"><Sprout size={20} /><strong>Giữ mùa vụ an lành</strong><p>Dữ liệu đáng tin cậy bắt đầu từ mỗi quyết định kiểm duyệt.</p></div>
        <button className="collapse-button" onClick={() => setCollapsed(v => !v)} aria-label={collapsed ? 'Mở rộng menu' : 'Thu gọn menu'}>{collapsed ? <ChevronRight size={17} /> : <ChevronLeft size={17} />}<span>Thu gọn menu</span></button>
      </div>
    </aside>
    <div className="workspace">
      <header className="topbar">
        <div className="topbar-left">
          <button className="icon-button menu-toggle" onClick={() => setMobileOpen(true)} aria-label="Mở menu"><Menu size={21} /></button>
          <span>RiceGuard AI</span>
          <ChevronRight size={14} />
          <strong>{current?.label || 'Chi tiết'}</strong>
        </div>
        <div className="topbar-right">
          <form className="global-search" onSubmit={submitSearch}>
            <Search size={15} />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Tìm bài viết..." aria-label="Tìm bài viết" />
            <kbd>Enter</kbd>
          </form>
          <button className="icon-button theme-toggle" onClick={toggleTheme} aria-label={dark ? 'Chuyển giao diện sáng' : 'Chuyển giao diện tối'}>{dark ? <Sun size={18} /> : <Moon size={18} />}</button>
          <div className="account">
            <div className="avatar">{initials(session?.user.fullName)}</div>
            <div className="account-copy">
              <strong>{session?.user.fullName || 'Quản trị viên'}</strong>
              <small>Quản trị viên</small>
            </div>
            <ChevronDown size={14} />
          </div>
          <button className="logout-button" onClick={logout} title="Đăng xuất"><LogOut size={18} /><span>Đăng xuất</span></button>
        </div>
      </header>
      <main className="main-content"><Outlet /></main>
    </div>
  </div>
}
