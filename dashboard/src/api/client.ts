import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { clearSession, readSession, updateTokens } from '../lib/session'
import type { ApiErrorBody, TokenResponse } from '../types'

const baseURL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1').replace(/\/$/, '')
export const api = axios.create({ baseURL, timeout: 20000 })
const refreshClient = axios.create({ baseURL, timeout: 20000 })
let refreshPromise: Promise<string> | null = null

api.interceptors.request.use(async config => {
  const session = readSession()
  if (!session || ['/auth/login', '/auth/refresh', '/auth/forgot-password'].includes(config.url || '')) return config
  if (Date.now() >= session.expiresAt - 30000) {
    try { await refreshToken() } catch { clearSession(); throw new Error('Phiên đăng nhập đã hết hạn') }
  }
  const token = readSession()?.idToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(response => response, async (error: AxiosError) => {
  const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
  if (error.response?.status === 403 && original?.url?.startsWith('/admin/')) {
    window.location.assign('/access-denied')
    return Promise.reject(error)
  }
  if (error.response?.status === 401 && original?._retry) {
    clearSession()
    return Promise.reject(error)
  }
  if (error.response?.status !== 401 || !original || original._retry || original.url?.startsWith('/auth/')) {
    return Promise.reject(error)
  }
  original._retry = true
  try {
    const token = await refreshToken()
    original.headers.Authorization = `Bearer ${token}`
    return api(original)
  } catch {
    clearSession()
    return Promise.reject(error)
  }
})

async function refreshToken(): Promise<string> {
  if (!refreshPromise) {
    const refreshToken = readSession()?.refreshToken
    if (!refreshToken) throw new Error('Không có phiên đăng nhập')
    refreshPromise = refreshClient.post<TokenResponse>('/auth/refresh', { refreshToken })
      .then(({ data }) => { updateTokens(data); return data.idToken })
      .finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    if (!error.response) return 'Không kết nối được với máy chủ. Kiểm tra backend rồi thử lại.'
    return error.response.data?.message || `Yêu cầu thất bại (${error.response.status}).`
  }
  return error instanceof Error ? error.message : 'Đã xảy ra lỗi. Vui lòng thử lại.'
}
