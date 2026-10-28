import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { diseaseService, feedbackService, postService, reportService, riskService } from '../api/services'
import type { ReportStatus } from '../types'

export const useReports = (status: ReportStatus | 'ALL', filters: { type?: string; reason?: string; priority?: string; q?: string } = {}) => useInfiniteQuery({
  queryKey: ['reports', status, filters], queryFn: ({ pageParam }) => reportService.list(status, pageParam, filters),
  initialPageParam: null as string | null, getNextPageParam: page => page.nextCursor,
})
export const usePosts = (topic: string, q: string, status = 'ALL', filters: { authorId?: string; reported?: boolean } = {}) => useInfiniteQuery({
  queryKey: ['posts', topic, q, status, filters], queryFn: ({ pageParam }) => postService.list(topic, q, pageParam, status, filters),
  initialPageParam: null as string | null, getNextPageParam: page => page.nextCursor,
})
export const useFeedbacks = (filters: { status?: string; reason?: string; q?: string; suggestedDiseaseCode?: string; from?: number; to?: number } = {}) => useInfiniteQuery({
  queryKey: ['feedbacks', filters], queryFn: ({ pageParam }) => feedbackService.list(pageParam, filters),
  initialPageParam: null as string | null, getNextPageParam: page => page.nextCursor,
})
export const usePost = (id: string) => useQuery({ queryKey: ['post', id], queryFn: () => postService.detail(id), enabled: !!id })
export const useComments = (id: string) => useQuery({ queryKey: ['comments', id], queryFn: () => postService.comments(id), enabled: !!id })
export const useDiseases = (params: { part?: string; cause?: string; common?: boolean; q?: string }) =>
  useQuery({ queryKey: ['diseases', params], queryFn: diseaseService.adminList })
export const useDisease = (id: string) => useQuery({ queryKey: ['disease', id], queryFn: () => diseaseService.detail(id), enabled: !!id })
export const useRiskAreas = (params: { province?: string; disease?: string; days: number; sort: string }) =>
  useQuery({ queryKey: ['risk', params], queryFn: () => riskService.areas(params) })
