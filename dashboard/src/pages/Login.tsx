import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowRight, BarChart3, Eye, EyeOff, Leaf, Lock, LockKeyhole, Mail, RotateCw, ShieldCheck, Users } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { authService } from '../api/services'
import { errorMessage } from '../api/client'
import { readSession, setSession } from '../lib/session'

const loginSchema = z.object({
  email: z.string().email('Nhập email hợp lệ'),
  password: z.string().min(6, 'Mật khẩu phải có ít nhất 6 ký tự'),
})
type LoginValues = z.infer<typeof loginSchema>

function AiNetworkOverlay() {
  return (
    <svg className="login-ai-network" viewBox="0 0 800 600" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <defs>
        <filter id="ai-glow" x="-30%" y="-30%" width="160%" height="160%">
          <feGaussianBlur stdDeviation="4" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        <linearGradient id="ai-line-grad" x1="0%" y1="100%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#34d399" stopOpacity="0.85" />
          <stop offset="50%" stopColor="#6ee7b7" stopOpacity="0.65" />
          <stop offset="100%" stopColor="#10b981" stopOpacity="0.25" />
        </linearGradient>
      </defs>

      {/* Main glowing connection curves */}
      <path d="M 60 520 Q 220 360, 420 420 T 780 370" stroke="url(#ai-line-grad)" strokeWidth="1.8" filter="url(#ai-glow)" />
      <path d="M 30 460 Q 200 400, 360 450 T 640 460" stroke="url(#ai-line-grad)" strokeWidth="1.4" strokeDasharray="6 4" filter="url(#ai-glow)" />
      <path d="M 160 550 Q 320 430, 520 470 T 740 430" stroke="url(#ai-line-grad)" strokeWidth="1.2" filter="url(#ai-glow)" />

      {/* Secondary branch lines */}
      <path d="M 190 395 L 360 450" stroke="rgba(52, 211, 153, 0.45)" strokeWidth="1" strokeDasharray="3 3" />
      <path d="M 420 420 L 520 470" stroke="rgba(52, 211, 153, 0.45)" strokeWidth="1" strokeDasharray="3 3" />

      {/* Glowing Nodes */}
      <g className="node-group">
        <circle cx="190" cy="395" r="10" fill="rgba(52, 211, 153, 0.3)" />
        <circle cx="190" cy="395" r="4.5" fill="#ffffff" stroke="#10b981" strokeWidth="2" />
      </g>
      <g className="node-group">
        <circle cx="360" cy="450" r="11" fill="rgba(52, 211, 153, 0.35)" />
        <circle cx="360" cy="450" r="5" fill="#ffffff" stroke="#10b981" strokeWidth="2.5" />
      </g>
      <g className="node-group">
        <circle cx="420" cy="420" r="13" fill="rgba(52, 211, 153, 0.35)" />
        <circle cx="420" cy="420" r="6" fill="#ffffff" stroke="#10b981" strokeWidth="2.5" />
      </g>
      <g className="node-group">
        <circle cx="520" cy="470" r="10" fill="rgba(52, 211, 153, 0.3)" />
        <circle cx="520" cy="470" r="4.5" fill="#ffffff" stroke="#10b981" strokeWidth="2" />
      </g>
      <g className="node-group">
        <circle cx="680" cy="390" r="10" fill="rgba(52, 211, 153, 0.3)" />
        <circle cx="680" cy="390" r="4.5" fill="#ffffff" stroke="#10b981" strokeWidth="2" />
      </g>
    </svg>
  )
}

function RiceLeavesDeco() {
  return (
    <div className="login-leaves-wrapper" aria-hidden="true">
      <svg className="login-leaves-top" viewBox="0 0 320 420" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M 320 0 C 240 50 160 130 110 240 C 80 300 70 360 65 420 C 80 350 110 280 165 210 C 220 140 280 50 320 0 Z" fill="url(#leaf-g1)" opacity="0.45" />
        <path d="M 320 40 C 230 100 150 190 100 300 C 75 360 68 400 65 420 C 75 380 98 310 150 230 C 205 150 270 70 320 40 Z" fill="url(#leaf-g2)" opacity="0.35" />
        <path d="M 320 100 C 245 170 180 250 145 350 C 130 385 125 410 120 420 C 130 395 155 330 200 260 C 250 180 295 130 320 100 Z" fill="url(#leaf-g1)" opacity="0.25" />
        <defs>
          <linearGradient id="leaf-g1" x1="100%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stopColor="#2e7d52" stopOpacity="0.8" />
            <stop offset="60%" stopColor="#68a982" stopOpacity="0.5" />
            <stop offset="100%" stopColor="#a3c9b1" stopOpacity="0.1" />
          </linearGradient>
          <linearGradient id="leaf-g2" x1="100%" y1="0%" x2="0%" y2="100%">
            <stop offset="0%" stopColor="#3d8b5e" stopOpacity="0.75" />
            <stop offset="50%" stopColor="#7cb894" stopOpacity="0.45" />
            <stop offset="100%" stopColor="#bfe0cc" stopOpacity="0.1" />
          </linearGradient>
        </defs>
      </svg>
      <svg className="login-leaves-bottom" viewBox="0 0 320 420" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M 320 420 C 240 350 160 270 105 160 C 80 100 70 45 65 0 C 80 65 110 130 165 205 C 220 280 280 365 320 420 Z" fill="url(#leaf-g1)" opacity="0.4" />
        <path d="M 320 380 C 230 310 150 215 100 110 C 80 60 70 20 65 0 C 75 45 100 100 150 185 C 205 260 270 335 320 380 Z" fill="url(#leaf-g2)" opacity="0.3" />
      </svg>
    </div>
  )
}

export function Login() {
  const navigate = useNavigate()
  const [showPassword, setShowPassword] = useState(false)
  const [serverError, setServerError] = useState('')
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
  })

  if (readSession()) return <Navigate to={readSession()?.user.admin ? '/' : '/access-denied'} replace />

  const onSubmit = async ({ email, password }: LoginValues) => {
    setServerError('')
    try {
      const response = await authService.login(email, password)
      if (!response.user.admin) {
        setSession(response)
        navigate('/access-denied', { replace: true })
        return
      }
      setSession(response)
      navigate('/', { replace: true })
    } catch (error) {
      setServerError(errorMessage(error))
    }
  }

  return (
    <div className="login-layout">
      {/* Left Story Side */}
      <div className="login-story">
        <div className="login-story-backdrop" aria-hidden="true" />
        <AiNetworkOverlay />

        {/* Top bar with brand & slogan */}
        <div className="login-story-top">
          <div className="login-brand">
            <span className="login-logo">
              <img src="/riceguard-logo.png" alt="RiceGuard Logo" />
            </span>
            <span className="login-brand-name">RiceGuard AI</span>
          </div>

          <div className="login-slogan">
            <span className="login-slogan-line" />
            <div className="login-slogan-text">
              <span>Công nghệ đồng hành</span>
              <span>cùng nhà nông</span>
            </div>
          </div>
        </div>

        {/* Story Body */}
        <div className="login-story-body">
          <div className="story-pill">
            <Leaf size={14} className="story-pill-icon" />
            <span>Hệ thống quản trị RiceGuard AI</span>
          </div>

          <h1>
            Mỗi quyết định tốt hơn,
            <br />
            một mùa vụ an lành hơn.
          </h1>

          <p className="story-desc">
            Nền tảng AI hỗ trợ phát hiện và quản lý bệnh hại lúa,
            <br />
            giúp nông nghiệp thông minh hơn, bền vững hơn.
          </p>

          <div className="story-feature-cards">
            <div className="story-feature-card">
              <div className="feature-icon-box">
                <Users size={20} strokeWidth={2.2} />
              </div>
              <div className="feature-text">
                <strong>Quản trị cộng đồng</strong>
                <p>Kết nối và lan tỏa tri thức về bệnh hại lúa</p>
              </div>
            </div>

            <div className="story-feature-card">
              <div className="feature-icon-box">
                <BarChart3 size={20} strokeWidth={2.2} />
              </div>
              <div className="feature-text">
                <strong>Theo dõi chẩn đoán</strong>
                <p>Quản lý dữ liệu và báo cáo toàn diện</p>
              </div>
            </div>
          </div>
        </div>

        {/* Empty foot for spacing balance */}
        <div className="login-story-foot" />
      </div>

      {/* Right Form Side */}
      <div className="login-form-side">
        <RiceLeavesDeco />

        <div className="login-card">
          <div className="login-emblem">
            <ShieldCheck size={28} strokeWidth={2.2} />
          </div>

          <p className="login-kicker">Dành cho quản trị viên</p>
          <h2>Chào mừng trở lại</h2>
          <p className="login-intro">Đăng nhập để quản lý hệ thống RiceGuard AI.</p>

          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <div className="form-group">
              <label className="form-label" htmlFor="email">Email</label>
              <div className={`input-with-icon ${errors.email ? 'has-error' : ''}`}>
                <Mail size={18} className="input-icon" />
                <input
                  id="email"
                  type="email"
                  autoComplete="username"
                  placeholder="admin@example.com"
                  {...register('email')}
                />
              </div>
              {errors.email && <p className="form-error">{errors.email.message}</p>}
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="password">Mật khẩu</label>
              <div className={`input-with-icon ${errors.password ? 'has-error' : ''}`}>
                <LockKeyhole size={18} className="input-icon" />
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="Nhập mật khẩu"
                  {...register('password')}
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowPassword(v => !v)}
                  aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {errors.password && <p className="form-error">{errors.password.message}</p>}
            </div>

            <div className="login-helper">
              <Link to="/forgot-password" className="login-forgot-link">Quên mật khẩu?</Link>
            </div>

            {serverError && <div className="login-error" role="alert">{serverError}</div>}

            <button type="submit" className="login-submit" disabled={isSubmitting}>
              <span>{isSubmitting ? 'Đang đăng nhập...' : 'Đăng nhập'}</span>
              <ArrowRight size={18} strokeWidth={2.2} />
            </button>
          </form>

          <div className="login-bottom-note">
            <ShieldCheck size={16} className="security-icon" strokeWidth={2} />
            <span>Chỉ tài khoản được cấp quyền quản trị mới có thể truy cập.</span>
          </div>
        </div>
      </div>
    </div>
  )
}

function SproutIcon() {
  return (
    <svg width="18" height="20" viewBox="0 0 18 20" fill="none" xmlns="http://www.w3.org/2000/svg" className="forgot-slogan-icon" aria-hidden="true">
      <path d="M9 19V9" stroke="#52986e" strokeWidth="1.8" strokeLinecap="round" />
      <path d="M9 11C9 7 6 5 2 5C2 9 4 12 9 12Z" fill="#6ba882" stroke="#52986e" strokeWidth="1.2" strokeLinejoin="round" />
      <path d="M9 9C9 5.5 12 3.5 16 3.5C16 7 14 10 9 10Z" fill="#52986e" stroke="#3d8258" strokeWidth="1.2" strokeLinejoin="round" />
    </svg>
  )
}

function BrightAiNetworkOverlay() {
  return (
    <svg className="forgot-ai-network" viewBox="0 0 900 600" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <defs>
        <filter id="bright-glow" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="3.5" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        <linearGradient id="bright-line-grad" x1="0%" y1="100%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#10b981" stopOpacity="0.8" />
          <stop offset="55%" stopColor="#34d399" stopOpacity="0.55" />
          <stop offset="100%" stopColor="#6ee7b7" stopOpacity="0.12" />
        </linearGradient>
      </defs>

      <path d="M 60 520 Q 240 340, 460 400 T 820 350" stroke="url(#bright-line-grad)" strokeWidth="1.8" filter="url(#bright-glow)" />
      <path d="M 30 440 Q 200 380, 380 430 T 680 430" stroke="url(#bright-line-grad)" strokeWidth="1.4" strokeDasharray="6 4" filter="url(#bright-glow)" />
      <path d="M 160 550 Q 320 420, 540 455 T 760 415" stroke="url(#bright-line-grad)" strokeWidth="1.2" filter="url(#bright-glow)" />

      <g className="bright-node-group">
        <circle cx="215" cy="385" r="10" fill="rgba(16, 185, 129, 0.25)" />
        <circle cx="215" cy="385" r="4.5" fill="#ffffff" stroke="#059669" strokeWidth="2" />
      </g>
      <g className="bright-node-group">
        <circle cx="380" cy="430" r="11" fill="rgba(16, 185, 129, 0.28)" />
        <circle cx="380" cy="430" r="5" fill="#ffffff" stroke="#059669" strokeWidth="2.5" />
      </g>
      <g className="bright-node-group">
        <circle cx="460" cy="400" r="12" fill="rgba(16, 185, 129, 0.28)" />
        <circle cx="460" cy="400" r="5.5" fill="#ffffff" stroke="#059669" strokeWidth="2.5" />
      </g>
      <g className="bright-node-group">
        <circle cx="540" cy="455" r="10" fill="rgba(16, 185, 129, 0.22)" />
        <circle cx="540" cy="455" r="4" fill="#ffffff" stroke="#059669" strokeWidth="2" />
      </g>
    </svg>
  )
}

function FloatingLeaves() {
  return (
    <div className="forgot-floating-leaves" aria-hidden="true">
      {/* Top Left Leaf */}
      <svg className="floating-leaf leaf-top-left" viewBox="0 0 70 70" fill="none">
        <path d="M 8 45 C 12 22 40 12 65 8 C 60 30 38 60 8 45 Z" fill="#6ba882" />
        <path d="M 8 45 Q 38 28 65 8" stroke="#4d8f66" strokeWidth="1.5" />
      </svg>
      {/* Center Leaf (between story and card) */}
      <svg className="floating-leaf leaf-center" viewBox="0 0 65 65" fill="none">
        <path d="M 6 40 C 10 20 36 10 58 6 C 54 26 34 52 6 40 Z" fill="#5a9e73" />
        <path d="M 6 40 Q 34 24 58 6" stroke="#438258" strokeWidth="1.3" />
      </svg>
      {/* Far Right Upper Leaf */}
      <svg className="floating-leaf leaf-right-upper" viewBox="0 0 75 75" fill="none">
        <path d="M 10 50 C 15 25 45 15 70 10 C 65 35 45 65 10 50 Z" fill="#62a37a" />
        <path d="M 10 50 Q 40 32 70 10" stroke="#47855d" strokeWidth="1.5" />
      </svg>
      {/* Bottom Right Leaf */}
      <svg className="floating-leaf leaf-bottom-right" viewBox="0 0 85 85" fill="none">
        <path d="M 12 55 C 18 28 50 16 78 10 C 72 38 48 72 12 55 Z" fill="#72b38b" />
        <path d="M 12 55 Q 45 34 78 10" stroke="#52946b" strokeWidth="1.5" />
      </svg>
      {/* Subtle curved background lines on bottom right */}
      <svg className="forgot-deco-curves" viewBox="0 0 500 400" fill="none">
        <path d="M 500 400 C 400 330 300 240 220 120 C 190 70 175 30 168 0" stroke="rgba(80, 160, 105, 0.2)" strokeWidth="1.5" />
        <path d="M 500 330 C 420 270 340 190 275 90 C 255 55 245 25 240 0" stroke="rgba(80, 160, 105, 0.14)" strokeWidth="1.2" />
        <path d="M 500 260 C 440 210 380 145 330 60 C 318 35 310 15 306 0" stroke="rgba(80, 160, 105, 0.09)" strokeWidth="1" />
      </svg>
    </div>
  )
}

export function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(false)
  const [errorMessageStr, setErrorMessageStr] = useState('')

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setLoading(true)
    setMessage('')
    setErrorMessageStr('')
    try {
      setMessage((await authService.forgotPassword(email)).message)
    } catch (error) {
      setErrorMessageStr(errorMessage(error))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="forgot-layout">
      {/* Background layer with sunny rice terraces fading gracefully */}
      <div className="forgot-bg-layer" aria-hidden="true" />
      <BrightAiNetworkOverlay />
      <FloatingLeaves />

      {/* Top Header Bar */}
      <header className="forgot-topbar">
        <div className="forgot-brand">
          <span className="forgot-logo">
            <img src="/riceguard-logo.png" alt="RiceGuard Logo" />
          </span>
          <span className="forgot-brand-name">RiceGuard AI</span>
        </div>

        <div className="forgot-slogan">
          <SproutIcon />
          <div className="forgot-slogan-text">
            <span>Công nghệ đồng hành</span>
            <span>cùng nhà nông</span>
          </div>
        </div>
      </header>

      {/* Main Content Layout */}
      <main className="forgot-main">
        {/* Left Side: Brand Story & Mission */}
        <div className="forgot-story">
          <p className="forgot-kicker">HỆ THỐNG QUẢN TRỊ RICEGUARD</p>
          <h1 className="forgot-title">
            Nông nghiệp thông minh
            <br />
            vì một mùa vụ bền vững.
          </h1>
          <p className="forgot-desc">
            Ứng dụng công nghệ để quản trị dữ liệu, theo dõi đồng ruộng
            <br />
            và bảo vệ cây lúa, đồng hành cùng nhà nông Việt Nam.
          </p>

          <div className="forgot-features-row">
            <div className="forgot-feature-item">
              <div className="feature-item-circle">
                <Users size={18} strokeWidth={2.2} />
              </div>
              <div className="feature-item-label">
                <span>Quản trị</span>
                <span>cộng đồng</span>
              </div>
            </div>

            <div className="feature-item-divider" aria-hidden="true" />

            <div className="forgot-feature-item">
              <div className="feature-item-circle">
                <Leaf size={18} strokeWidth={2.2} />
              </div>
              <div className="feature-item-label">
                <span>Theo dõi</span>
                <span>chẩn đoán</span>
              </div>
            </div>
          </div>
        </div>

        {/* Right Side: Floating Reset Card */}
        <div className="forgot-card-wrapper">
          <div className="forgot-card">
            {/* Top Emblem with Envelope and Cycle Badge */}
            <div className="forgot-emblem">
              <Mail size={34} strokeWidth={1.8} className="forgot-emblem-mail" />
              <span className="forgot-emblem-badge" aria-hidden="true">
                <RotateCw size={11} strokeWidth={2.6} />
              </span>
            </div>

            <h2>Đặt lại mật khẩu</h2>
            <p className="forgot-card-sub">
              Nhập email tài khoản. Nếu email tồn tại, hệ thống sẽ
              <br />
              gửi hướng dẫn đặt lại mật khẩu.
            </p>

            <form onSubmit={submit} noValidate>
              <div className="form-group">
                <label className="form-label" htmlFor="reset-email">Email</label>
                <div className="input-with-icon">
                  <Mail size={18} className="input-icon" />
                  <input
                    id="reset-email"
                    type="email"
                    required
                    placeholder="Nhập email của bạn"
                    value={email}
                    onChange={e => setEmail(e.target.value)}
                  />
                </div>
              </div>

              {message && <div className="reset-message">{message}</div>}
              {errorMessageStr && <div className="login-error" role="alert">{errorMessageStr}</div>}

              <button type="submit" className="forgot-submit-btn" disabled={loading}>
                <span>{loading ? 'Đang gửi...' : 'Gửi hướng dẫn'}</span>
                <ArrowRight size={18} strokeWidth={2.2} />
              </button>

              {/* Back to login divider with lines */}
              <div className="back-to-login-divider">
                <span className="divider-line" />
                <Link to="/login" className="back-to-login-btn">
                  Quay lại đăng nhập
                </Link>
                <span className="divider-line" />
              </div>
            </form>
          </div>

          {/* Security Note under card */}
          <div className="forgot-bottom-note">
            <Lock size={15} className="forgot-lock-icon" strokeWidth={1.8} />
            <p>
              Thông tin của bạn được bảo mật và chỉ sử dụng
              <br />
              cho mục đích đặt lại mật khẩu.
            </p>
          </div>
        </div>
      </main>
    </div>
  )
}



