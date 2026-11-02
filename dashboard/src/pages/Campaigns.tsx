import { useState } from 'react'
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCircle2, Eye, Save, Send } from 'lucide-react'
import { toast } from 'sonner'
import { campaignService, userService } from '../api/services'
import { errorMessage } from '../api/client'
import { Button, Page, PageHeader, Panel, Pill, QueryState, SearchBox, useConfirm, type Tone } from '../components/ui'
import { shortTime } from '../lib/admin'
import type { Campaign, CampaignDraft, CampaignPreview } from '../types'

const initial: CampaignDraft = { title: '', body: '', audience: 'PROVINCE', province: '', userIds: [] }
const STATUS: Record<string, { label: string; tone: Tone }> = { DRAFT: { label: 'Nháp', tone: 'neutral' }, SENDING: { label: 'Đang gửi', tone: 'info' }, SENT: { label: 'Đã gửi', tone: 'success' }, PARTIAL: { label: 'Lỗi một phần', tone: 'warning' }, FAILED: { label: 'Thất bại', tone: 'danger' } }
const AUDIENCE: Record<string, string> = { ALL: 'Toàn bộ người dùng', PROVINCE: 'Theo tỉnh/thành', SELECTED: 'Chọn người dùng' }

export function Campaigns() {
  const client = useQueryClient()
  const [draft, setDraft] = useState<CampaignDraft>(initial)
  const [preview, setPreview] = useState<CampaignPreview | null>(null)
  const [saved, setSaved] = useState<Campaign | null>(null)
  const [busy, setBusy] = useState(false)
  const [userSearch, setUserSearch] = useState('')
  const [userQ, setUserQ] = useState('')
  const confirm = useConfirm()
  const users = useInfiniteQuery({ queryKey: ['campaign-users', userQ], queryFn: ({ pageParam }) => userService.list({ q: userQ }, pageParam), initialPageParam: null as string | null, getNextPageParam: p => p.nextCursor, enabled: draft.audience === 'SELECTED' })
  const campaigns = useInfiniteQuery({ queryKey: ['campaigns'], queryFn: ({ pageParam }) => campaignService.list(pageParam), initialPageParam: null as string | null, getNextPageParam: p => p.nextCursor, refetchInterval: query => query.state.data?.pages.some(p => p.items.some(c => c.status === 'SENDING')) ? 3000 : false })
  function edit(patch: Partial<CampaignDraft>) { setDraft(d => ({ ...d, ...patch })); setPreview(null); setSaved(null) }
  function toggle(uid: string) { edit({ userIds: draft.userIds?.includes(uid) ? draft.userIds.filter(x => x !== uid) : [...(draft.userIds || []), uid] }) }
  async function previewDraft() { setBusy(true); try { setPreview(await campaignService.preview(draft)) } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) } }
  async function save() { setBusy(true); try { const r = await campaignService.create(draft); setSaved(r); await client.invalidateQueries({ queryKey: ['campaigns'] }); toast.success('Đã lưu chiến dịch nháp') } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false) } }
  const send = (c: Campaign) => confirm.ask({ title: 'Gửi thông báo này?', description: `"${c.title}" sẽ được gửi đến ${c.recipientCount || 'các'} người nhận. Thông báo là thật và không thu hồi được.`, confirmText: 'Gửi thông báo', icon: Send, onConfirm: async () => {
    try { const r = await campaignService.send(c.id); await client.invalidateQueries({ queryKey: ['campaigns'] }); setSaved(r); toast.success('Đã bắt đầu gửi. Theo dõi kết quả trong lịch sử.') } catch (e) { toast.error(errorMessage(e)) }
  } })
  const history = campaigns.data?.pages.flatMap(p => p.items) ?? []
  return <Page art="field">
    <PageHeader title="Thông báo" description="Soạn, xem trước người nhận, lưu nháp và gửi thông báo cho người dùng." />
    <div className="grid grid-main-side">
      <Panel icon={Bell} title="Soạn thông báo">
        <div className="form-grid">
          <label className="form-field full">Tiêu đề<input className="field-input" maxLength={120} value={draft.title} onChange={e => edit({ title: e.target.value })} /></label>
          <label className="form-field">Nhóm người nhận<select className="field-select" value={draft.audience} onChange={e => edit({ audience: e.target.value as CampaignDraft['audience'] })}>{Object.entries(AUDIENCE).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
          {draft.audience === 'PROVINCE' && <label className="form-field">Tỉnh / thành<input className="field-input" value={draft.province || ''} onChange={e => edit({ province: e.target.value })} /></label>}
          <label className="form-field full">Nội dung<textarea className="field-area" maxLength={2000} value={draft.body} onChange={e => edit({ body: e.target.value })} /></label>
        </div>
        {draft.audience === 'SELECTED' && <div className="sub-panel" style={{ marginTop: 14 }}><h3>Chọn người nhận ({draft.userIds?.length || 0})</h3><SearchBox placeholder="Tên, email, UID" value={userSearch} onChange={setUserSearch} onSubmit={() => setUserQ(userSearch)} /><div style={{ marginTop: 10, maxHeight: 220, overflowY: 'auto' }}>{users.data?.pages.flatMap(p => p.items).map(u => <label key={u.id} className="check-row" style={{ padding: '6px 0' }}><input className="check" type="checkbox" checked={draft.userIds?.includes(u.id) || false} onChange={() => toggle(u.id)} />{u.fullName} ({u.email})</label>)}</div>{users.hasNextPage && <Button size="sm" variant="secondary" onClick={() => users.fetchNextPage()} disabled={users.isFetchingNextPage}>Tải thêm</Button>}</div>}
        <div className="op-actions" style={{ marginBottom: 0 }}>
          <Button variant="outline" disabled={busy || !draft.title.trim() || !draft.body.trim()} onClick={previewDraft}><Eye size={16} />Xem trước người nhận</Button>
          <Button variant="secondary" disabled={busy || !preview} onClick={save}><Save size={16} />Lưu nháp</Button>
          {saved?.status === 'DRAFT' && <Button disabled={busy} onClick={() => send(saved)}><Send size={16} />Gửi chiến dịch đã lưu</Button>}
        </div>
      </Panel>
      <Panel icon={CheckCircle2} title="Xem trước" description={preview ? `${preview.count} người nhận phù hợp` : 'Bấm Xem trước người nhận để kiểm tra.'}>
        {preview ? <><div className="quote-box"><strong style={{ color: 'var(--ink)' }}>{draft.title}</strong><div style={{ marginTop: 4 }}>{draft.body}</div></div><div className="status-rows" style={{ marginTop: 13 }}>{preview.recipients.slice(0, 8).map(r => <div className="status-row" key={r.id}>{r.name}<span className="cell-sub">{r.province || ''}</span></div>)}</div>{preview.count > 8 && <p className="cell-sub" style={{ marginTop: 10 }}>Và {preview.count - 8} người khác.</p>}</> : <div className="empty-chart">Chưa có bản xem trước</div>}
      </Panel>
    </div>
    <Panel flush title="Lịch sử chiến dịch">
      <QueryState query={campaigns} empty={history.length === 0} emptyText="Chưa có chiến dịch" />
      {history.length > 0 && <div className="table-wrap"><table className="data-table"><thead><tr><th>Chiến dịch</th><th>Nhóm nhận</th><th>Trạng thái</th><th>Đã tạo / lỗi</th><th>Thời gian</th><th>Thao tác</th></tr></thead><tbody>{history.map(c => {
        const st = STATUS[c.status] ?? { label: c.status, tone: 'neutral' as Tone }
        const retry = c.status === 'DRAFT' || c.status === 'PARTIAL' || (c.status === 'SENDING' && !!c.startedAt && Date.now() - c.startedAt > 900000)
        return <tr key={c.id}><td className="cell-title">{c.title}</td><td>{AUDIENCE[c.audience] ?? c.audience}{c.province ? `: ${c.province}` : ''}</td><td><Pill tone={st.tone}>{st.label}</Pill></td><td>{c.delivered} / {c.failed}</td><td className="nowrap">{shortTime(c.sentAt || c.createdAt)}</td><td>{retry && <Button size="sm" disabled={busy} onClick={() => send(c)}>{c.status === 'DRAFT' ? 'Gửi' : 'Thử lại'}</Button>}</td></tr>
      })}</tbody></table></div>}
      {campaigns.hasNextPage && <div className="table-foot"><span>Đã tải {history.length} chiến dịch</span><Button size="sm" variant="secondary" disabled={campaigns.isFetchingNextPage} onClick={() => campaigns.fetchNextPage()}>Tải thêm</Button></div>}
    </Panel>
    {confirm.node}
  </Page>
}
