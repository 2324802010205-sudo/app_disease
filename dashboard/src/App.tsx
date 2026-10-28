import { Component, ErrorInfo, lazy, ReactNode, Suspense, useSyncExternalStore } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Toaster } from 'sonner'
import { Layout } from './components/Layout'
import { clearSession, readSession, subscribeSession } from './lib/session'
import { SkeletonRows } from './components/ui'

class ErrorBoundary extends Component<{ children: ReactNode }, { hasError: boolean; error: Error | null }> {
  state: { hasError: boolean; error: Error | null } = { hasError: false, error: null }
  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error }
  }
  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('App ErrorBoundary caught:', error, info)
  }
  render() {
    if (this.state.hasError) {
      return (
        <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: '#f8faf8', padding: 24 }}>
          <div style={{ maxWidth: 440, width: '100%', background: '#ffffff', border: '1px solid #fee2e2', borderRadius: 16, padding: '24px 28px', textAlign: 'center', boxShadow: '0 8px 30px rgba(0,0,0,0.06)' }}>
            <h2 style={{ fontSize: 18, color: '#991b1b', margin: '0 0 8px', fontWeight: 700 }}>Đã có lỗi xảy ra</h2>
            <p style={{ fontSize: 12.5, color: '#dc2626', margin: '0 0 16px', wordBreak: 'break-word', background: '#fef2f2', padding: '10px 12px', borderRadius: 8 }}>
              {this.state.error?.message}
            </p>
            <button
              onClick={() => window.location.reload()}
              style={{ padding: '9px 20px', background: '#15472f', color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 13 }}
            >
              Tải lại trang
            </button>
          </div>
        </div>
      )
    }
    return this.props.children
  }
}

const Login = lazy(() => import('./pages/Login').then(m => ({ default: m.Login })))
const ForgotPassword = lazy(() => import('./pages/Login').then(m => ({ default: m.ForgotPassword })))
const Overview = lazy(() => import('./pages/Overview').then(m => ({ default: m.Overview })))
const Reports = lazy(() => import('./pages/Reports').then(m => ({ default: m.Reports })))
const Posts = lazy(() => import('./pages/Posts').then(m => ({ default: m.Posts })))
const PostDetail = lazy(() => import('./pages/Posts').then(m => ({ default: m.PostDetail })))
const Feedbacks = lazy(() => import('./pages/Feedbacks').then(m => ({ default: m.Feedbacks })))
const Diseases = lazy(() => import('./pages/Diseases').then(m => ({ default: m.Diseases })))
const DiseaseDetail = lazy(() => import('./pages/Diseases').then(m => ({ default: m.DiseaseDetail })))
const Risk = lazy(() => import('./pages/Risk').then(m => ({ default: m.Risk })))
const Operations = lazy(() => import('./pages/Operations').then(m => ({ default: m.Operations })))
const Users = lazy(() => import('./pages/Users').then(m => ({ default: m.Users })))
const UserDetail = lazy(() => import('./pages/Users').then(m => ({ default: m.UserDetail })))
const DiseaseCreate = lazy(() => import('./pages/Diseases').then(m => ({ default: m.DiseaseCreate })))
const Campaigns = lazy(() => import('./pages/Campaigns').then(m => ({ default: m.Campaigns })))
const Audit = lazy(() => import('./pages/Audit').then(m => ({ default: m.Audit })))

let snapshot = readSession()
function currentSession() {
  const next = readSession()
  if (JSON.stringify(next) !== JSON.stringify(snapshot)) snapshot = next
  return snapshot
}
function ProtectedRoute() {
  const session = useSyncExternalStore(subscribeSession, currentSession)
  return !session ? <Navigate to="/login" replace /> : session.user.admin ? <Layout /> : <Navigate to="/access-denied" replace />
}
function AccessDenied() {
  return <main className="admin-page"><h1>Không có quyền truy cập</h1><p>Tài khoản này không có quyền quản trị dashboard.</p><button onClick={() => { clearSession(); window.location.assign('/login') }}>Đăng nhập bằng tài khoản khác</button></main>
}
export default function App() {
  return (
    <BrowserRouter>
      <ErrorBoundary>
        <Suspense fallback={<div className="route-loading"><SkeletonRows count={5} /></div>}>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/forgot-password" element={<ForgotPassword />} />
            <Route path="/access-denied" element={<AccessDenied />} />
            <Route element={<ProtectedRoute />}>
              <Route path="/" element={<Overview />} />
              <Route path="/reports" element={<Reports />} />
              <Route path="/posts" element={<Posts />} />
              <Route path="/posts/:id" element={<PostDetail />} />
              <Route path="/feedbacks" element={<Feedbacks />} />
              <Route path="/diseases" element={<Diseases />} />
              <Route path="/diseases/new" element={<DiseaseCreate />} />
              <Route path="/diseases/:id" element={<DiseaseDetail />} />
              <Route path="/users" element={<Users />} />
              <Route path="/users/:id" element={<UserDetail />} />
              <Route path="/campaigns" element={<Campaigns />} />
              <Route path="/audit" element={<Audit />} />
              <Route path="/risk" element={<Risk />} />
              <Route path="/operations" element={<Operations />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
      </ErrorBoundary>
      <Toaster richColors position="top-right" />
    </BrowserRouter>
  )
}

