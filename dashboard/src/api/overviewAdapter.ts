import type { AdminStats } from '../types'
export interface OverviewData {
  scans: number; todayCases: number
  reports: AdminStats['reports']; feedbacks: AdminStats['feedbacks']; posts: AdminStats['posts']
  trend: { day: string; scans: number; cases: number }[]
  diseaseMix: { name: string; value: number }[]
}
