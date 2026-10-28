import { api } from './client'
import type { AdminFeedback, AdminPost, AdminReport, AdminStats, AdminUser, AdminUserDetail, AuditEntry, AuthResponse, Campaign, CampaignDraft, CampaignPreview, Comment, Disease, DiseaseDraft, PageResult, ReportStatus, RiskArea, RiskAreaInfo } from '../types'

const unwrap = async <T>(request: Promise<{ data: T }>) => (await request).data
const pageParams = (cursor?: string | null, limit = 20) => ({ ...(cursor ? { cursor } : {}), limit })

export const authService = {
  login: (email: string, password: string) => unwrap(api.post<AuthResponse>('/auth/login', { email, password })),
  forgotPassword: (email: string) => unwrap(api.post<{ message: string }>('/auth/forgot-password', { email })),
  logout: () => unwrap(api.post<void>('/auth/logout')),
}
export const reportService = {
  list: (status: ReportStatus | 'ALL', cursor?: string | null, filters: { type?: string; reason?: string; priority?: string; q?: string } = {}) =>
    unwrap(api.get<PageResult<AdminReport>>('/admin/reports', { params: { status, ...filters, ...pageParams(cursor) } })),
  update: (id: string, status: Exclude<ReportStatus, 'NEW'>) =>
    unwrap(api.put<void>(`/admin/reports/${encodeURIComponent(id)}`, { status })),
  removeContent: (id: string) => unwrap(api.post<{ contentExisted: boolean; reportsResolved: number }>(`/admin/reports/${encodeURIComponent(id)}/remove-content`)),
}
export const postService = {
  list: (topic: string, q: string, cursor?: string | null, status = 'ALL', filters: { authorId?: string; reported?: boolean } = {}) =>
    unwrap(api.get<PageResult<AdminPost>>('/admin/posts', { params: { topic, q, status, ...filters, ...pageParams(cursor) } })),
  detail: (id: string) => unwrap(api.get<AdminPost>(`/admin/posts/${encodeURIComponent(id)}`)),
  comments: (id: string) => unwrap(api.get<Comment[]>(`/admin/posts/${encodeURIComponent(id)}/comments`)),
  remove: (id: string) => unwrap(api.delete<void>(`/admin/posts/${encodeURIComponent(id)}`)),
  visibility: (id: string, hidden: boolean) => unwrap(api.put<void>(`/admin/posts/${encodeURIComponent(id)}/visibility`, { hidden })),
  removeComment: (postId: string, id: string) => unwrap(api.delete<void>(`/admin/posts/${encodeURIComponent(postId)}/comments/${encodeURIComponent(id)}`)),
}
export const feedbackService = {
  list: (cursor?: string | null, filters: { status?: string; reason?: string; q?: string; suggestedDiseaseCode?: string; from?: number; to?: number } = {}) => unwrap(api.get<PageResult<AdminFeedback>>('/admin/feedbacks', { params: { ...filters, ...pageParams(cursor) } })),
  update: (id: string, status: string, resolution: string) => unwrap(api.put<void>(`/admin/feedbacks/${encodeURIComponent(id)}`, { status, resolution })),
}
export const statsService = {
  get: () => unwrap(api.get<AdminStats>('/admin/stats')),
  overview: () => unwrap(api.get<import('./overviewAdapter').OverviewData>('/admin/overview')),
}
export const diseaseService = {
  list: (params: { part?: string; cause?: string; common?: boolean; q?: string }) =>
    unwrap(api.get<Disease[]>('/diseases', { params })),
  detail: (id: string) => unwrap(api.get<Disease>(`/admin/diseases/${encodeURIComponent(id)}`)),
  adminList: () => unwrap(api.get<Disease[]>('/admin/diseases')),
  create: (id: string, draft: DiseaseDraft) => unwrap(api.post<Disease>(`/admin/diseases/${encodeURIComponent(id)}`, draft)),
  update: (id: string, draft: DiseaseDraft) => unwrap(api.put<Disease>(`/admin/diseases/${encodeURIComponent(id)}`, draft)),
  visibility: (id: string, hidden: boolean) => unwrap(api.put<void>(`/admin/diseases/${encodeURIComponent(id)}/visibility`, { hidden })),
  uploadImage: (id: string, file: File) => {
    const body = new FormData(); body.append('file', file)
    return unwrap(api.post<{ imageUrl: string }>(`/admin/diseases/${encodeURIComponent(id)}/image`, body))
  },
}
export const userService = {
  list: (filters: { q?: string; role?: string; province?: string; verified?: boolean; locked?: boolean }, cursor?: string | null) =>
    unwrap(api.get<PageResult<AdminUser>>('/admin/users', { params: { ...filters, ...pageParams(cursor) } })),
  detail: (id: string) => unwrap(api.get<AdminUserDetail>(`/admin/users/${encodeURIComponent(id)}`)),
  verify: (id: string, verified: boolean) => unwrap(api.put<void>(`/admin/users/${encodeURIComponent(id)}/verified-expert`, { verified })),
  lock: (id: string, locked: boolean) => unwrap(api.put<void>(`/admin/users/${encodeURIComponent(id)}/lock`, { locked })),
}
export const campaignService = {
  preview: (draft: CampaignDraft) => unwrap(api.post<CampaignPreview>('/admin/campaigns/preview', draft)),
  create: (draft: CampaignDraft) => unwrap(api.post<Campaign>('/admin/campaigns', draft)),
  list: (cursor?: string | null) => unwrap(api.get<PageResult<Campaign>>('/admin/campaigns', { params: pageParams(cursor) })),
  detail: (id: string) => unwrap(api.get<Campaign>(`/admin/campaigns/${encodeURIComponent(id)}`)),
  send: (id: string) => unwrap(api.post<Campaign>(`/admin/campaigns/${encodeURIComponent(id)}/send`)),
}
export const auditService = {
  list: (filters: { actorId?: string; action?: string; targetType?: string; targetId?: string }, cursor?: string | null) =>
    unwrap(api.get<PageResult<AuditEntry>>('/admin/audit', { params: { ...filters, ...pageParams(cursor) } })),
}
export const riskService = {
  areas: (params: { province?: string; disease?: string; days: number; sort: string }) =>
    unwrap(api.get<RiskArea[]>('/risk/areas', { params })),
  detail: (province: string, district: string, days: number) =>
    unwrap(api.get<RiskAreaInfo>('/risk/areas/info', { params: { province, district, days } })),
}
export const operationService = {
  runReminders: (test = false) => unwrap(api.post<{ sent: number }>('/admin/jobs/reminders', null, { params: { test } })),
  previewReminders: () => unwrap(api.get<{ count: number; recipients: { userId: string; fieldId: string; fieldName: string; province?: string; district?: string }[] }>('/admin/jobs/reminders/preview')),
  status: () => unwrap(api.get<{ backend: string; firestore: string; diseases: number; riskAreas: number; reports: number; jobs: { id: string; kind: string; count: number; createdAt: number }[] }>('/admin/operations')),
  refreshRisk: () => unwrap(api.post<{ count: number }>('/admin/jobs/risk-refresh')),
  syncDiseases: () => unwrap(api.post<{ count: number }>('/admin/jobs/disease-sync')),
}
