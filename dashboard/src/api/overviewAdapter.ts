import type { AdminStats } from '../types'
export interface OverviewData {
  scans: number; todayCases: number
  users: { total: number; newToday: number }
  reports: AdminStats['reports']; feedbacks: AdminStats['feedbacks']; posts: AdminStats['posts']
  trend: { day: string; scans: number; cases: number }[]
  diseaseMix: { name: string; value: number }[]
}
