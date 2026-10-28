import * as Dialog from '@radix-ui/react-dialog'
import { AlertCircle, ChevronRight, Inbox, LoaderCircle, X } from 'lucide-react'
import type { ReactNode } from 'react'
import { errorMessage } from '../api/client'
import { cn } from '../lib/utils'
export { Button } from './ui/button'
export { Card } from './ui/card'
import { Button } from './ui/button'

export { cn }
export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  return <div className="page-heading"><div><h1>{title}</h1>{description && <p>{description}</p>}</div>{actions && <div className="heading-actions">{actions}</div>}</div>
}
export function StatusBadge({ label, tone = 'neutral' }: { label: string; tone?: 'neutral' | 'success' | 'warning' | 'danger' | 'info' }) {
  return <span className={cn('status-badge', `status-${tone}`)}>{label}</span>
}
export function SkeletonRows({ count = 4 }: { count?: number }) {
  return <div className="skeleton-stack">{Array.from({ length: count }, (_, i) => <div className="skeleton-row" key={i}><span /><span /><span /></div>)}</div>
}
export function EmptyState({ title, description }: { title: string; description?: string }) {
  return <div className="state-panel"><div className="state-icon"><Inbox size={25} /></div><strong>{title}</strong>{description && <p>{description}</p>}</div>
}
export function ErrorState({ error, retry }: { error: unknown; retry: () => void }) {
  return <div className="state-panel"><div className="state-icon error"><AlertCircle size={25} /></div><strong>Không tải được dữ liệu</strong><p>{errorMessage(error)}</p><Button variant="secondary" onClick={retry}>Thử lại</Button></div>
}
export function LoadMore({ hasMore, loading, onClick }: { hasMore: boolean; loading: boolean; onClick: () => void }) {
  if (!hasMore) return null
  return <div className="load-more"><Button variant="secondary" onClick={onClick} disabled={loading}>{loading ? <LoaderCircle className="spin" size={16} /> : <ChevronRight size={16} />} Tải thêm</Button></div>
}
export function ConfirmDialog({ open, onOpenChange, title, description, confirmText, destructive, loading, onConfirm }: {
  open: boolean; onOpenChange: (open: boolean) => void; title: string; description: string;
  confirmText: string; destructive?: boolean; loading?: boolean; onConfirm: () => void;
}) {
  return <Dialog.Root open={open} onOpenChange={onOpenChange}><Dialog.Portal><Dialog.Overlay className="dialog-overlay" /><Dialog.Content className="dialog-content">
    <div className="dialog-top"><Dialog.Title>{title}</Dialog.Title><Dialog.Close className="icon-button" aria-label="Đóng"><X size={18} /></Dialog.Close></div>
    <Dialog.Description>{description}</Dialog.Description>
    <div className="dialog-actions"><Button variant="secondary" onClick={() => onOpenChange(false)} disabled={loading}>Hủy</Button><Button variant={destructive ? 'danger' : 'primary'} onClick={onConfirm} disabled={loading}>{loading && <LoaderCircle className="spin" size={16} />}{confirmText}</Button></div>
  </Dialog.Content></Dialog.Portal></Dialog.Root>
}
export function formatDate(value?: number | null) { return value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' }).format(value) : '—' }
export function percent(value?: number | null) { return value == null ? '—' : `${Math.round(value * 100)}%` }
export function initials(value?: string | null) { return (value?.trim().split(/\s+/).slice(-2).map(p => p[0]).join('') || 'RG').toUpperCase() }
