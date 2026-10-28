import type { AuthResponse, Session, TokenResponse } from '../types'

const KEY = 'riceguard.admin.session'
const EVENT = 'riceguard:session'

export function readSession(): Session | null {
  try {
    const value = sessionStorage.getItem(KEY)
    if (!value) return null
    const session: Session = JSON.parse(value)
    if (!session.idToken || !session.refreshToken || !session.user) return null
    return session
  } catch { return null }
}

function save(session: Session | null) {
  if (session) sessionStorage.setItem(KEY, JSON.stringify(session))
  else sessionStorage.removeItem(KEY)
  window.dispatchEvent(new Event(EVENT))
}

export function setSession(response: AuthResponse) {
  save({ idToken: response.idToken, refreshToken: response.refreshToken,
    expiresAt: Date.now() + response.expiresIn * 1000, user: response.user })
}

export function updateTokens(response: TokenResponse) {
  const current = readSession()
  if (!current) return
  save({ ...current, idToken: response.idToken, refreshToken: response.refreshToken,
    expiresAt: Date.now() + response.expiresIn * 1000 })
}

export function clearSession() { save(null) }
export function subscribeSession(callback: () => void) {
  window.addEventListener(EVENT, callback)
  return () => window.removeEventListener(EVENT, callback)
}
