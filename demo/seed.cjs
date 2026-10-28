const fs = require('node:fs')
const path = require('node:path')

const projectId = 'demo-riceguard'
const authBase = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1'
const firestoreBase = `http://127.0.0.1:8085/v1/projects/${projectId}/databases/(default)/documents`
const fixture = JSON.parse(fs.readFileSync(path.join(__dirname, 'dashboard-data.json'), 'utf8'))
const now = Date.now()
const timestamp = daysAgo => now - daysAgo * 86400000
const date = daysAgo => new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit'
}).format(new Date(timestamp(daysAgo)))

function firestoreValue(value) {
  if (value === null) return { nullValue: null }
  if (typeof value === 'string') return { stringValue: value }
  if (typeof value === 'boolean') return { booleanValue: value }
  if (typeof value === 'number') return Number.isInteger(value)
    ? { integerValue: String(value) } : { doubleValue: value }
  if (Array.isArray(value)) return { arrayValue: { values: value.map(firestoreValue) } }
  if (typeof value === 'object') return { mapValue: { fields: firestoreFields(value) } }
  throw new Error(`Kiểu dữ liệu không hỗ trợ: ${typeof value}`)
}

function firestoreFields(object) {
  return Object.fromEntries(Object.entries(object)
    .filter(([, value]) => value !== undefined)
    .map(([key, value]) => [key, firestoreValue(value)]))
}

async function request(url, options) {
  const response = await fetch(url, options)
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(`${response.status} ${url}: ${JSON.stringify(body)}`)
  return body
}

async function write(documentPath, data) {
  if (!/^[a-zA-Z0-9_-]+(?:\/[a-zA-Z0-9_-]+)+$/.test(documentPath)) {
    throw new Error(`Đường dẫn dữ liệu không hợp lệ: ${documentPath}`)
  }
  await request(`${firestoreBase}/${documentPath}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer owner' },
    body: JSON.stringify({ fields: firestoreFields(data) })
  })
}

async function emulatorUser(user) {
  const body = { email: user.email, password: 'DemoOnlyUser#2026', returnSecureToken: true }
  const endpoint = `${authBase}/accounts:signUp?key=fake`
  const response = await fetch(endpoint, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
  })
  const result = await response.json()
  if (response.ok) return result.localId
  if (result.error?.message === 'EMAIL_EXISTS') {
    return (await request(`${authBase}/accounts:signInWithPassword?key=fake`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
    })).localId
  }
  throw new Error(`Không tạo được người dùng demo ${user.email}: ${JSON.stringify(result)}`)
}

async function main() {
  // Hai URL ở trên chỉ trỏ tới Firebase Emulator trên localhost; không dùng cấu hình Firebase thật.
  const users = {}
  for (const user of fixture.users) {
    const uid = await emulatorUser(user)
    users[user.key] = uid
    await write(`users/${uid}`, {
      email: user.email, fullName: user.fullName, province: user.province,
      district: user.district, role: user.role, verifiedExpert: Boolean(user.verifiedExpert),
      locked: false, settings: { theme: 'SYSTEM', language: 'vi', notifyAlert: true,
        notifyReminder: true, notifyCommunity: true }, fcmTokens: [],
      createdAt: timestamp(user.daysAgo), updatedAt: timestamp(0)
    })
  }

  for (const post of fixture.posts) {
    await write(`posts/${post.id}`, {
      authorId: users[post.author], province: post.province, topic: post.topic,
      title: post.title, content: post.content, images: [], likeCount: post.likeCount,
      commentCount: post.commentCount, hidden: post.hidden,
      createdAt: timestamp(post.daysAgo), updatedAt: timestamp(post.daysAgo)
    })
  }
  await write('posts/demo-post-1/comments/demo-comment-1', {
    authorId: users.expert1, text: 'Nên theo dõi thêm độ ẩm và kiểm tra các lá mới.',
    parentId: null, likeCount: 2, createdAt: timestamp(0)
  })

  for (const report of fixture.reports) {
    await write(`reports/${report.id}`, {
      type: report.type, postId: report.postId, targetId: report.targetId,
      reporterId: users[report.reporter], reason: report.reason, detail: report.detail,
      status: report.status, createdAt: timestamp(report.daysAgo)
    })
  }
  for (const scan of fixture.scans) {
    await write(`users/${users[scan.user]}/scans/${scan.id}`, {
      fieldId: `demo-field-${scan.user}`, fieldName: scan.fieldName,
      province: scan.province, district: scan.district, status: 'DIAGNOSED',
      riskLevel: scan.riskLevel, diseaseCode: scan.diseaseCode,
      diseaseName: scan.diseaseName, confidence: scan.confidence,
      severity: scan.riskLevel === 'HIGH' ? 'HIGH' : 'MEDIUM',
      symptoms: ['Vết bệnh trên lá lúa'], summary: 'Kết quả mẫu để trình diễn dashboard.',
      recommendations: [], stage: 'TILLERING', engine: 'demo', countedInArea: true,
      createdAt: timestamp(scan.daysAgo)
    })
  }
  for (const user of fixture.users.filter(user => user.role === 'FARMER')) {
    await write(`users/${users[user.key]}/fields/demo-field-${user.key}`, {
      name: user.key === 'farmer1' ? 'Ruộng Phong Điền' : 'Ruộng Thoại Sơn',
      province: user.province, district: user.district, nextCheckDate: date(0),
      createdAt: timestamp(user.daysAgo), updatedAt: timestamp(0)
    })
  }
  for (const feedback of fixture.feedbacks) {
    await write(`feedbacks/${feedback.id}`, {
      userId: users[feedback.user], scanId: feedback.scanId,
      scanDiseaseCode: feedback.scanDiseaseCode, scanConfidence: feedback.scanConfidence,
      engine: 'demo', reason: feedback.reason,
      suggestedDiseaseCode: feedback.suggestedDiseaseCode,
      description: feedback.description, attachments: [], status: feedback.status,
      resolution: feedback.resolution, createdAt: timestamp(feedback.daysAgo)
    })
  }
  for (const area of fixture.areas) {
    await write(`areaStats/${area.id}`, {
      province: area.province, district: area.district, date: date(area.daysAgo),
      total: area.total, byDisease: area.byDisease, byStage: area.byStage,
      lastAt: timestamp(area.daysAgo)
    })
  }
  for (const campaign of fixture.campaigns) {
    await write(`adminCampaigns/${campaign.id}`, {
      title: campaign.title, body: campaign.body, audience: campaign.audience,
      province: null, userIds: [], status: campaign.status, createdBy: 'demo-admin',
      createdAt: timestamp(campaign.daysAgo), startedAt: null, sentAt: null,
      recipientCount: 0, delivered: 0, failed: 0
    })
  }
  for (const entry of fixture.audit) {
    await write(`adminAudit/${entry.id}`, {
      actorId: entry.actorId, action: entry.action, targetType: entry.targetType,
      targetId: entry.targetId, detail: entry.detail, createdAt: timestamp(entry.daysAgo)
    })
  }
  console.log('Đã nạp dữ liệu demo: người dùng, bài viết, báo cáo, lượt quét, phản hồi AI, vùng nguy cơ, chiến dịch và nhật ký.')
}

if (require.main === module) main().catch(error => { console.error(error.message); process.exitCode = 1 })
module.exports = { firestoreFields }
