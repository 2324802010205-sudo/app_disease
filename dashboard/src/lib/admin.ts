export function downloadCsv(filename: string, headers: string[], rows: unknown[][]) {
  const escape = (value: unknown) => `"${String(value ?? '').replaceAll('"', '""')}"`
  const csv = '\uFEFF' + [headers, ...rows].map(row => row.map(escape).join(',')).join('\r\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
export async function collectPages<T>(fetch: (cursor?: string | null) => Promise<{items: T[]; nextCursor: string | null}>): Promise<T[]> {
  const result: T[] = []
  let cursor: string | null = null
  do {
    const page = await fetch(cursor)
    result.push(...page.items)
    cursor = page.nextCursor
  } while (cursor)
  return result
}
export function dateTime(value?: number | null) { return value ? new Date(value).toLocaleString('vi-VN') : '—' }

const tz = 'Asia/Ho_Chi_Minh'
export function shortTime(value?: number | null) {
  if (!value) return '—'
  const d = new Date(value)
  const part = (o: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat('vi-VN', { timeZone: tz, ...o }).format(d)
  return `${part({ hour: '2-digit', minute: '2-digit', hour12: false })} ${part({ day: '2-digit', month: '2-digit', year: '2-digit' })}`
}
export function ago(value?: number | null) {
  if (!value) return ''
  const m = Math.floor((Date.now() - value) / 60000)
  if (m < 1) return 'Vừa xong'
  if (m < 60) return `${m} phút trước`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} giờ trước`
  return `${Math.floor(h / 24)} ngày trước`
}
export const pct = (v?: number | null) => v == null ? '—' : `${Math.round(v * 100)}%`
export const TOPICS: Record<string, { label: string; tone: 'info' | 'success' | 'violet' | 'warning' }> = {
  PEST: { label: 'Sâu bệnh', tone: 'success' }, EXPERIENCE: { label: 'Kinh nghiệm', tone: 'info' }, VARIETY: { label: 'Giống lúa', tone: 'violet' },
  WEATHER: { label: 'Thời tiết', tone: 'info' }, WATER: { label: 'Nước tưới', tone: 'warning' },
}
export const REPORT_REASONS: Record<string, string> = { SPAM: 'Tin rác, quảng cáo', MISLEADING: 'Sai lệch thông tin', OFFENSIVE: 'Nội dung không phù hợp', OTHER: 'Khác' }
export const FEEDBACK_REASONS: Record<string, string> = { WRONG_RESULT: 'Kết quả chưa đúng', UNCLEAR_IMAGE: 'Ảnh chưa rõ', MISSING_INFO: 'Thiếu thông tin', NEED_ADVICE: 'Muốn góp ý chuyên môn' }
/** Mức độ suy ra từ lý do, cùng quy tắc với bộ lọc priority ở backend. */
export const reportPriority = (reason: string) => reason === 'MISLEADING' ? 'HIGH' : reason === 'SPAM' ? 'MEDIUM' : 'LOW'
export const feedbackPriority = (reason: string) => reason === 'WRONG_RESULT' ? 'HIGH' : reason === 'UNCLEAR_IMAGE' ? 'MEDIUM' : 'LOW'
export const PRIORITY = { HIGH: { label: 'Cao', tone: 'danger' }, MEDIUM: { label: 'Trung bình', tone: 'warning' }, LOW: { label: 'Thấp', tone: 'success' } } as const
export const RISK = {
  VERY_HIGH: { label: 'Rất cao', tone: 'danger', color: '#B83A39' }, HIGH: { label: 'Cao', tone: 'danger', color: '#E35457' },
  MEDIUM: { label: 'Trung bình', tone: 'warning', color: '#FABF55' }, LOW: { label: 'Thấp', tone: 'success', color: '#2E8E56' },
} as const
export const PARTS: Record<string, string> = { LEAF: 'Lá', STEM: 'Thân', GRAIN: 'Hạt' }
export const CAUSES: Record<string, string> = { FUNGUS: 'Nấm', BACTERIA: 'Vi khuẩn', VIRUS: 'Virus', OTHER: 'Khác' }
export const LABEL_NAMES: Record<string, string> = { healthy: 'Lá khỏe', unknown: 'Không xác định', pending: 'Chờ phân tích' }
