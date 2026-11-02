import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Activity, AlertCircle, Bell, CheckCircle2, Clock, Database, Eye, FileText, Layers, Lightbulb, List, Play, RefreshCw, Send, Settings, ShieldCheck, TriangleAlert, User, Users, BarChart3, Cpu } from 'lucide-react'
import { toast } from 'sonner'
import { errorMessage } from '../api/client'
import { operationService } from '../api/services'
import { Button, ConfirmDialog, Modal, Page, PageHeader, Panel, Pill, Stat, type Tone } from '../components/ui'
import { dateTime, shortTime } from '../lib/admin'

type Job = 'reminders' | 'risk' | 'diseases'
const KIND: Record<string, string> = { REMINDERS: 'Nhắc lịch canh tác đã chạy', REMINDERS_TEST: 'Chạy thử nhắc lịch', RISK_REFRESH: 'Làm mới dữ liệu vùng nguy cơ', DISEASE_SYNC: 'Đồng bộ thư viện bệnh', DATASET_BACKFILL: 'Đồng bộ lượt quét vào kho ảnh' }
const UNIT: Record<string, string> = { REMINDERS: 'thông báo đã gửi', REMINDERS_TEST: 'thông báo thử', RISK_REFRESH: 'bản ghi khu vực', DISEASE_SYNC: 'bệnh trong thư viện', DATASET_BACKFILL: 'ảnh được thêm' }

export function Operations() {
  const client = useQueryClient()
  const [test, setTest] = useState(true)
  const [busy, setBusy] = useState(false)
  const [ask, setAsk] = useState<Job | null>(null)
  const [recipients, setRecipients] = useState(false)
  const [log, setLog] = useState(false)
  const status = useQuery({ queryKey: ['operations'], queryFn: operationService.status })
  const preview = useQuery({ queryKey: ['reminder-preview'], queryFn: operationService.previewReminders })
  const s = status.data
  const jobs = s?.jobs ?? []
  const today = new Date(); today.setHours(0, 0, 0, 0)
  const runsToday = jobs.filter(j => j.createdAt >= today.getTime()).length
  const issues = s ? (s.diagnosis.available ? 0 : 1) : 0
  const reachable = !!s && s.backend === 'ONLINE' && s.firestore === 'ONLINE'
  const count = preview.data?.count ?? 0
  async function run(kind: Job) {
    setBusy(true)
    try {
      if (kind === 'reminders') { const r = await operationService.runReminders(test); toast.success(`Đã gửi ${r.sent} nhắc lịch${test ? ' ở chế độ thử' : ''}`) }
      if (kind === 'risk') { const r = await operationService.refreshRisk(); toast.success(`Đã cập nhật ${r.count} bản ghi vùng nguy cơ`) }
      if (kind === 'diseases') { const r = await operationService.syncDiseases(); toast.success(`Thư viện có ${r.count} bệnh sau khi đồng bộ`) }
      await Promise.all(['operations', 'reminder-preview', 'risk', 'diseases'].map(k => client.invalidateQueries({ queryKey: [k] })))
    } catch (e) { toast.error(errorMessage(e)) } finally { setBusy(false); setAsk(null) }
  }
  const tasks: { id: Job; icon: typeof Settings; name: string; desc: string; level: string; tone: Tone }[] = [
    { id: 'reminders', icon: Settings, name: 'Gửi nhắc lịch canh tác', desc: 'Gửi thông báo cho người dùng đến hạn kiểm tra ruộng', level: 'Trung bình', tone: 'warning' },
    { id: 'risk', icon: Database, name: 'Làm mới dữ liệu vùng nguy cơ', desc: 'Quét lại lượt quét để tổng hợp ca bệnh theo khu vực', level: 'Thấp', tone: 'success' },
    { id: 'diseases', icon: RefreshCw, name: 'Đồng bộ thư viện bệnh', desc: 'Nạp nội dung bệnh mới, giữ bản ghi admin đã sửa', level: 'Thấp', tone: 'success' },
  ]
  return <Page art="landscape">
    <PageHeader title="Vận hành hệ thống" description="Các tác vụ thủ công dành riêng cho quản trị viên. Theo dõi trạng thái và kích hoạt quy trình an toàn." />
    <div className="grid grid-4">
      <Stat icon={Settings} label="Tác vụ khả dụng" value={tasks.length} sub="Sẵn sàng thực thi" subTone="up" />
      <Stat icon={BarChart3} tone="info" label="Lần chạy hôm nay" value={runsToday} sub={jobs[0] ? `Gần nhất ${shortTime(jobs[0].createdAt)}` : 'Chưa có lần chạy'} />
      <Stat icon={TriangleAlert} tone="warning" label="Cần kiểm tra" value={issues} sub={issues ? 'Dịch vụ AI chưa sẵn sàng' : 'Không có vấn đề'} subTone={issues ? 'warn' : 'up'} />
      <Stat icon={CheckCircle2} label="Trạng thái hệ thống" value={s ? (reachable ? 'Ổn định' : 'Có sự cố') : '—'} word sub={s ? `${s.diseases} bệnh, ${s.riskAreas} bản ghi vùng nguy cơ` : 'Đang tải'} />
    </div>
    {status.isError && <div className="notice danger" style={{ marginBottom: 18 }}><AlertCircle size={18} />{errorMessage(status.error)}</div>}
    <div className="grid grid-main-side">
      <Panel>
        <div className="op-head"><div className="icon-tile lg"><Bell size={27} /></div><div><h2>Gửi nhắc lịch canh tác</h2><p>Chạy thủ công tác vụ nhắc người dùng có lịch kiểm tra ruộng đến hạn hôm nay. Tác vụ này có thể gửi thông báo thật.</p><div className="op-tags"><Pill tone="success" bare><Play size={13} />Tác vụ thủ công</Pill><Pill tone="warning" bare><AlertCircle size={13} />Mức ảnh hưởng: Trung bình</Pill></div></div></div>
        <div className="notice" style={{ marginTop: 16 }}><AlertCircle size={19} style={{ flex: 'none' }} />Chỉ dùng khi cần kiểm thử hoặc vận hành theo quy trình. Nên xem trước danh sách người nhận trước khi gửi.</div>
        <div className="op-actions">
          <Button disabled={busy || preview.isError} onClick={() => setAsk('reminders')}><Play size={17} />Kích hoạt tác vụ</Button>
          <Button variant="outline" onClick={() => setRecipients(true)}><Users size={17} />Xem trước người nhận</Button>
          <Button variant="outline" onClick={() => setLog(true)}><FileText size={17} />Nhật ký gần đây</Button>
        </div>
        <div className="grid grid-2" style={{ marginBottom: 0 }}>
          <div className="sub-panel"><h3><Database size={19} />Điều kiện chạy</h3><div className="cond"><User size={17} /><span>Có <b>{preview.isPending ? '...' : count}</b> người dùng đến hạn</span></div><div className="cond"><Send size={17} />Kênh gửi: Thông báo đẩy và hộp thư</div><div className="cond"><Clock size={17} />Lịch tự động: mỗi ngày trên máy chủ</div></div>
          <div className="sub-panel"><h3><ShieldCheck size={19} />Kiểm soát an toàn</h3>
            <div className="switch-row"><div><strong>Yêu cầu xác nhận trước khi gửi</strong><small>Luôn bật cho tác vụ có tác động thật</small></div><button className="switch" role="switch" aria-checked="true" aria-label="Yêu cầu xác nhận" disabled /></div>
            <div className="switch-row"><div><strong>Giới hạn thử nghiệm 10 người</strong><small>Chỉ gửi tối đa 10 thông báo</small></div><button className="switch" role="switch" aria-checked={test} aria-label="Giới hạn thử nghiệm" onClick={() => setTest(v => !v)} /></div>
          </div>
        </div>
      </Panel>
      <div className="stack">
        <Panel icon={Activity} title="Trạng thái vận hành">
          <div className="status-rows">
            <div className="status-row"><Cpu size={17} />Máy chủ API<span className={`dot-status${s?.backend === 'ONLINE' ? '' : ' bad'}`}>{s?.backend === 'ONLINE' ? 'Hoạt động' : 'Không rõ'}</span></div>
            <div className="status-row"><Database size={17} />Cơ sở dữ liệu<span className={`dot-status${s?.firestore === 'ONLINE' ? '' : ' bad'}`}>{s?.firestore === 'ONLINE' ? 'Bình thường' : 'Không rõ'}</span></div>
            <div className="status-row"><Layers size={17} />Dịch vụ chẩn đoán AI<span className={`dot-status${s?.diagnosis.available ? '' : ' warn'}`}>{s ? (s.diagnosis.available ? `Sẵn sàng (${s.diagnosis.engine})` : 'Chưa bật') : '...'}</span></div>
            <div className="status-row"><Clock size={17} />Lần chạy cuối<b>{jobs[0] ? shortTime(jobs[0].createdAt) : 'Chưa có'}</b></div>
          </div>
        </Panel>
        <Panel icon={FileText} title="Nhật ký gần nhất" action={<button className="panel-link" onClick={() => setLog(true)}>Xem tất cả</button>}>
          {jobs.length ? <div className="timeline">{jobs.slice(0, 3).map(j => <div className="tl-item" key={j.id}><div><strong>{KIND[j.kind] ?? j.kind}</strong><small>{j.count} {UNIT[j.kind] ?? 'mục'}</small></div><time>{shortTime(j.createdAt)}</time></div>)}</div> : <div className="empty-chart" style={{ minHeight: 80 }}>Chưa có tác vụ nào</div>}
        </Panel>
        <div className="hint-card short"><h3><i><Lightbulb size={19} /></i>Lưu ý vận hành</h3><p>Chỉ kích hoạt tác vụ thủ công khi đã xác nhận phạm vi ảnh hưởng và có khả năng theo dõi sau khi chạy.</p></div>
      </div>
    </div>
    <Panel flush icon={List} title="Tác vụ thủ công khác">
      <div className="table-wrap"><table className="data-table"><thead><tr><th>Tên tác vụ</th><th>Mô tả</th><th>Mức độ</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{tasks.map(t => <tr key={t.id}><td><span className="meta-icons cell-title"><span><t.icon size={17} />{t.name}</span></span></td><td>{t.desc}</td><td><Pill bare tone={t.tone}>{t.level}</Pill></td><td><span className="dot-status">Sẵn sàng</span></td><td><Button size="sm" disabled={busy} onClick={() => setAsk(t.id)}>Thực thi</Button></td></tr>)}</tbody></table></div>
    </Panel>

    <ConfirmDialog open={ask === 'reminders'} onOpenChange={o => !o && !busy && setAsk(null)} icon={Bell} title="Chạy tác vụ nhắc lịch?" description="Hệ thống sẽ kiểm tra các lịch đến hạn hôm nay. Hãy xác nhận thêm một lần trước khi gửi thông báo." warning={test ? 'Chế độ thử: chỉ gửi tối đa 10 thông báo thật.' : 'Tác vụ này sẽ gửi thông báo thật đến toàn bộ người dùng đến hạn.'} confirmText="Tiếp tục" loading={busy} facts={[{ icon: Settings, label: 'Loại tác vụ', value: 'Thủ công' }, { icon: Users, label: 'Người dùng đến hạn', value: count }, { icon: ShieldCheck, label: 'Chế độ', value: test ? 'Thử nghiệm' : 'Toàn bộ' }]} onConfirm={() => run('reminders')} />
    <ConfirmDialog open={ask === 'risk'} onOpenChange={o => !o && !busy && setAsk(null)} icon={Database} title="Làm mới vùng nguy cơ?" description="Hệ thống sẽ quét lại toàn bộ lượt quét để tính lại số ca theo khu vực. Tránh chạy khi đang có nhiều lượt quét mới." confirmText="Tiếp tục" loading={busy} onConfirm={() => run('risk')} />
    <ConfirmDialog open={ask === 'diseases'} onOpenChange={o => !o && !busy && setAsk(null)} icon={RefreshCw} title="Đồng bộ thư viện bệnh?" description="Nội dung bệnh có phiên bản mới sẽ được cập nhật. Bệnh đã được quản trị viên sửa vẫn giữ nguyên." confirmText="Tiếp tục" loading={busy} onConfirm={() => run('diseases')} />
    <Modal open={recipients} onOpenChange={setRecipients} icon={Eye} title="Người nhận hôm nay" description={`${count} ruộng đến hạn kiểm tra.`}>
      <div className="dialog-body">{preview.data?.recipients.length ? <table className="data-table"><thead><tr><th>Người dùng</th><th>Ruộng</th><th>Khu vực</th></tr></thead><tbody>{preview.data.recipients.map(r => <tr key={`${r.userId}/${r.fieldId}`}><td>{r.userId.slice(0, 12)}</td><td>{r.fieldName || r.fieldId}</td><td>{[r.district, r.province].filter(Boolean).join(', ')}</td></tr>)}</tbody></table> : <div className="empty-chart">Không có người nhận hôm nay</div>}</div>
    </Modal>
    <Modal open={log} onOpenChange={setLog} icon={FileText} title="Nhật ký tác vụ" description="30 lần chạy gần nhất.">
      <div className="dialog-body">{jobs.length ? <table className="data-table"><thead><tr><th>Tác vụ</th><th>Kết quả</th><th>Thời gian</th></tr></thead><tbody>{jobs.map(j => <tr key={j.id}><td>{KIND[j.kind] ?? j.kind}</td><td>{j.count} {UNIT[j.kind] ?? 'mục'}</td><td>{dateTime(j.createdAt)}</td></tr>)}</tbody></table> : <div className="empty-chart">Chưa có tác vụ nào</div>}</div>
    </Modal>
  </Page>
}
