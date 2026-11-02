import * as Dialog from '@radix-ui/react-dialog'
import { AlertCircle, ChevronLeft, ChevronRight, Inbox, LoaderCircle, Search, X, type LucideIcon } from 'lucide-react'
import type { ButtonHTMLAttributes, FormEvent, ReactNode } from 'react'
import { useState } from 'react'
import { Area, AreaChart, Bar, BarChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import { errorMessage } from '../api/client'
import { cn } from '../lib/utils'

export { cn }
export type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'info' | 'violet'
const SPARK: Record<string, string> = { success: '#2E8E56', danger: '#E35457', warning: '#E2B75C', info: '#4A8FD6', neutral: '#2E8E56', violet: '#2E8E56' }

export function Button({ variant = 'primary', size, block, className, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'outline' | 'danger' | 'soft'; size?: 'sm'; block?: boolean }) {
  return <button type="button" className={cn('btn', `btn-${variant}`, size === 'sm' && 'btn-sm', block && 'btn-block', className)} {...props} />
}
export function Page({ art = 'field', children }: { art?: 'landscape' | 'field' | 'rice'; children: ReactNode }) {
  return <div className="page"><div className="page-art" aria-hidden="true" style={{ backgroundImage: `url(/art/head-${art}.png)` }} />{children}</div>
}
export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  return <div className="page-head"><div><h1>{title}</h1>{description && <p>{description}</p>}</div>{actions && <div className="head-actions">{actions}</div>}</div>
}
export function Panel({ title, description, icon: Icon, tone, action, flush, className, children }: { title?: string; description?: string; icon?: LucideIcon; tone?: Tone; action?: ReactNode; flush?: boolean; className?: string; children?: ReactNode }) {
  return <section className={cn('panel', flush && 'panel-flush', className)}>
    {title && <div className="panel-head" style={flush ? { padding: '18px 20px 0' } : undefined}><div className="panel-title">{Icon && <div className={cn('icon-tile', tone && `tone-${tone}`)}><Icon size={21} /></div>}<div><h2>{title}</h2>{description && <p>{description}</p>}</div></div>{action}</div>}
    {children}
  </section>
}
/** Dãy số thật (ví dụ 7 ngày gần nhất); không truyền thì không vẽ, tránh biểu đồ trang trí không có nghĩa. */
export function Sparkline({ data, tone = 'success', kind = 'area' }: { data: number[]; tone?: Tone; kind?: 'area' | 'bar' }) {
  const color = SPARK[tone]
  const rows = data.map((v, i) => ({ i, v }))
  const id = `sp-${color.slice(1)}`
  return <div className="stat-chart" aria-hidden="true"><ResponsiveContainer width="100%" height="100%">{kind === 'bar'
    ? <BarChart data={rows}><Bar dataKey="v" fill={color} fillOpacity={.55} radius={[2, 2, 0, 0]} isAnimationActive={false} /></BarChart>
    : <AreaChart data={rows}><defs><linearGradient id={id} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor={color} stopOpacity={.32} /><stop offset="1" stopColor={color} stopOpacity={0} /></linearGradient></defs><Area type="monotone" dataKey="v" stroke={color} strokeWidth={1.8} fill={`url(#${id})`} isAnimationActive={false} /></AreaChart>}
  </ResponsiveContainer></div>
}
export function Stat({ icon: Icon, tone, label, value, sub, subTone, word, valueDanger, spark, sparkKind, onClick }: { icon: LucideIcon; tone?: Tone; label: string; value: ReactNode; sub?: ReactNode; subTone?: 'up' | 'warn' | 'bad'; word?: boolean; valueDanger?: boolean; spark?: number[]; sparkKind?: 'area' | 'bar'; onClick?: () => void }) {
  return <section className={cn('panel stat', spark && spark.length > 1 && 'has-spark')} onClick={onClick} style={onClick ? { cursor: 'pointer' } : undefined}>
    <div className={cn('icon-tile lg', tone && `tone-${tone}`)}><Icon size={26} /></div>
    <div className="stat-body"><div className="stat-label">{label}</div><div className={cn('stat-value', word && 'word', valueDanger && 'danger')}>{value}</div>{sub != null && <div className={cn('stat-sub', subTone)}>{sub}</div>}</div>
    {spark && spark.length > 1 && <Sparkline data={spark} tone={tone} kind={sparkKind} />}
  </section>
}
export function Pill({ tone = 'neutral', bare, children }: { tone?: Tone; bare?: boolean; children: ReactNode }) {
  return <span className={cn('pill', tone !== 'neutral' && `pill-${tone}`, bare && 'bare')}>{children}</span>
}
export function SearchBox({ placeholder, value, onChange, onSubmit, style }: { placeholder: string; value: string; onChange: (v: string) => void; onSubmit: () => void; style?: React.CSSProperties }) {
  return <form className="search-box" style={style} onSubmit={(e: FormEvent) => { e.preventDefault(); onSubmit() }}><Search size={16} /><input aria-label={placeholder} placeholder={placeholder} value={value} onChange={e => onChange(e.target.value)} /></form>
}
export function Tabs<T extends string>({ value, onChange, items }: { value: T; onChange: (v: T) => void; items: { key: T; label: string; count?: number }[] }) {
  return <div className="tabs" role="tablist">{items.map(t => <button key={t.key} role="tab" aria-selected={value === t.key} className={cn('tab', value === t.key && 'active')} onClick={() => onChange(t.key)}>{t.label}{!!t.count && <b>{t.count}</b>}</button>)}</div>
}
export function Bars({ rows, palette = ['#15573A', '#4E946E', '#8EB99F', '#ADCFB9', '#C6DECF'], dots }: { rows: { name: string; value: number }[]; palette?: string[]; dots?: string[] }) {
  const total = rows.reduce((a, r) => a + r.value, 0)
  if (!total) return <div className="empty-chart">Chưa có dữ liệu</div>
  return <div className="bars">{rows.map((r, i) => {
    const pct = Math.round(r.value / total * 100)
    return <div className="bar-row" key={r.name}><span className="name">{dots && <i className="bar-dot" style={{ background: dots[i % dots.length] }} />}{r.name}</span><div className="bar-track"><div className="bar-fill" style={{ width: `${pct}%`, background: palette[i % palette.length] }} /></div><span className="num">{pct}% ({r.value})</span></div>
  })}</div>
}
export function Steps({ items }: { items: { title: string; text: string; icon?: LucideIcon }[] }) {
  return <div className="steps">{items.map((s, i) => <div className="step" key={s.title}><div className="step-mark">{s.icon ? <s.icon size={19} /> : i + 1}</div><strong>{s.title}</strong><span>{s.text}</span></div>)}</div>
}
export function Donut({ rows, colors, center, size = 168 }: { rows: { name: string; value: number }[]; colors: string[]; center?: ReactNode; size?: number }) {
  const total = rows.reduce((a, r) => a + r.value, 0)
  if (!total) return <div className="empty-chart" style={{ flex: 1 }}>Chưa có dữ liệu</div>
  return <div style={{ display: 'flex', alignItems: 'center', gap: 26, flexWrap: 'wrap' }}>
    <div style={{ position: 'relative', width: size, height: size, flex: 'none' }}><ResponsiveContainer width="100%" height="100%"><PieChart><Pie data={rows} dataKey="value" nameKey="name" innerRadius="62%" outerRadius="100%" paddingAngle={1.5} stroke="none" startAngle={90} endAngle={-270} isAnimationActive={false}>{rows.map((_, i) => <Cell key={i} fill={colors[i % colors.length]} />)}</Pie><Tooltip /></PieChart></ResponsiveContainer>{center && <div className="chart-center">{center}</div>}</div>
    <div className="legend">{rows.map((r, i) => <div className="legend-row" key={r.name}><i className="legend-swatch" style={{ background: colors[i % colors.length] }} />{r.name}<b>{Math.round(r.value / total * 100)}%</b></div>)}</div>
  </div>
}
export function Pager({ page, pages, hasNext, loading, onPage, label }: { page: number; pages: number; hasNext: boolean; loading?: boolean; onPage: (index: number) => void; label: string }) {
  return <div className="table-foot"><span>{label}</span><div className="pager"><button className="page-btn" aria-label="Trang trước" disabled={page === 0} onClick={() => onPage(page - 1)}><ChevronLeft size={15} /></button>{Array.from({ length: pages }, (_, i) => <button key={i} className={cn('page-btn', i === page && 'active')} onClick={() => onPage(i)}>{i + 1}</button>)}<button className="page-btn" aria-label="Trang sau" disabled={(page >= pages - 1 && !hasNext) || loading} onClick={() => onPage(page + 1)}><ChevronRight size={15} /></button></div></div>
}
export function SkeletonRows({ count = 4 }: { count?: number }) {
  return <div className="skeleton-stack">{Array.from({ length: count }, (_, i) => <div className="skeleton-row" key={i} />)}</div>
}
export function EmptyState({ title, description }: { title: string; description?: string }) {
  return <div className="state-panel"><div className="state-icon"><Inbox size={25} /></div><strong>{title}</strong>{description && <p>{description}</p>}</div>
}
export function ErrorState({ error, retry }: { error: unknown; retry?: () => void }) {
  return <div className="state-panel"><div className="state-icon error"><AlertCircle size={25} /></div><strong>Không tải được dữ liệu</strong><p>{errorMessage(error)}</p>{retry && <Button variant="secondary" onClick={retry}>Thử lại</Button>}</div>
}
export function QueryState({ query, empty, emptyText }: { query: { isPending: boolean; isError: boolean; error: unknown; refetch: () => unknown }; empty?: boolean; emptyText?: string }) {
  if (query.isPending) return <SkeletonRows />
  if (query.isError) return <ErrorState error={query.error} retry={() => query.refetch()} />
  return empty ? <EmptyState title={emptyText || 'Chưa có dữ liệu'} /> : null
}
export function Modal({ open, onOpenChange, title, description, icon: Icon, tone, badge, small, footer, children }: { open: boolean; onOpenChange: (open: boolean) => void; title: string; description?: string; icon?: LucideIcon; tone?: Tone; badge?: ReactNode; small?: boolean; footer?: ReactNode; children?: ReactNode }) {
  return <Dialog.Root open={open} onOpenChange={onOpenChange}><Dialog.Portal><Dialog.Overlay className="dialog-overlay" /><Dialog.Content className={cn('dialog', small && 'sm')} aria-describedby={undefined}>
    <div className="dialog-deco" aria-hidden="true" />
    <div className="dialog-head">{Icon && <div className={cn('icon-tile lg', tone && `tone-${tone}`)}><Icon size={26} /></div>}<div className="grow"><Dialog.Title asChild><h2>{title}</h2></Dialog.Title>{description && <p>{description}</p>}</div>{badge}<Dialog.Close className="dialog-close" aria-label="Đóng"><X size={18} /></Dialog.Close></div>
    {children}
    {footer && <div className="dialog-foot">{footer}</div>}
  </Dialog.Content></Dialog.Portal></Dialog.Root>
}
/** Hộp xác nhận cho thao tác có tác động thật: nêu rõ đối tượng, không dùng confirm() của trình duyệt. */
export function ConfirmDialog({ open, onOpenChange, title, description, warning, confirmText, destructive, loading, icon, facts, onConfirm }: {
  open: boolean; onOpenChange: (open: boolean) => void; title: string; description: string; warning?: string; confirmText: string;
  destructive?: boolean; loading?: boolean; icon?: LucideIcon; facts?: { icon: LucideIcon; label: string; value: ReactNode }[]; onConfirm: () => void
}) {
  return <Modal open={open} onOpenChange={onOpenChange} small title={title} icon={icon} tone={destructive ? 'danger' : undefined} footer={<><Button variant="secondary" onClick={() => onOpenChange(false)} disabled={loading}>Hủy</Button><Button variant={destructive ? 'danger' : 'primary'} onClick={onConfirm} disabled={loading}>{loading && <LoaderCircle className="spin" size={16} />}{confirmText}</Button></>}>
    <div className="confirm-body">{description}{warning && <div className="notice" style={{ marginTop: 14 }}><AlertCircle size={19} style={{ flex: 'none' }} />{warning}</div>}{facts && <div className="fact-strip">{facts.map(f => <div className="fact" key={f.label}><f.icon size={21} /><div><small>{f.label}</small><b>{f.value}</b></div></div>)}</div>}</div>
    <div style={{ height: 16 }} />
  </Modal>
}
export function useConfirm() {
  const [state, setState] = useState<null | Omit<Parameters<typeof ConfirmDialog>[0], 'open' | 'onOpenChange' | 'loading'>>(null)
  const [loading, setLoading] = useState(false)
  const ask = (next: NonNullable<typeof state>) => setState(next)
  const node = state && <ConfirmDialog {...state} open loading={loading} onOpenChange={o => !o && !loading && setState(null)} onConfirm={async () => { setLoading(true); try { await state.onConfirm() } finally { setLoading(false); setState(null) } }} />
  return { ask, node }
}
export function initials(value?: string | null) { return (value?.trim().split(/\s+/).slice(-2).map(p => p[0]).join('') || 'RG').toUpperCase() }
