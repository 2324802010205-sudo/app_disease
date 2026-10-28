const fs = require('node:fs')
const path = require('node:path')
const net = require('node:net')
const crypto = require('node:crypto')
const { spawn, spawnSync } = require('node:child_process')
const readline = require('node:readline')

const root = path.resolve(__dirname, '..')
const backend = path.join(root, 'backend')
const dashboard = path.join(root, 'dashboard')
const logs = path.join(__dirname, 'logs')
const children = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

function commandExists(name) {
  return spawnSync('where.exe', [name], { windowsHide: true, stdio: 'ignore' }).status === 0
}

function portFree(port) {
  return new Promise(resolve => {
    const server = net.createServer()
    server.once('error', () => resolve(false))
    server.listen(port, '127.0.0.1', () => server.close(() => resolve(true)))
  })
}

function start(label, command, args, cwd, env = process.env) {
  const out = fs.createWriteStream(path.join(logs, `${label}.log`), { flags: 'w' })
  const child = spawn(command, args, {
    cwd, env, shell: true, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe']
  })
  child.stdout.pipe(out, { end: false })
  child.stderr.pipe(out, { end: false })
  child.once('close', () => out.end())
  children.push(child)
  child.on('error', error => console.error(`${label}: ${error.message}`))
  console.log(`Đang chạy ${label}...`)
  return child
}

async function until(label, test, child, timeoutMs = 120000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (child.exitCode !== null) throw new Error(`${label} đã dừng. Xem demo/logs/${label}.log`)
    try { if (await test()) return } catch { /* chờ dịch vụ sẵn sàng */ }
    await sleep(750)
  }
  throw new Error(`${label} không sẵn sàng sau ${timeoutMs / 1000} giây. Xem demo/logs/${label}.log`)
}

async function portOpen(port) {
  return new Promise(resolve => {
    const socket = net.connect({ host: '127.0.0.1', port })
    socket.once('connect', () => { socket.destroy(); resolve(true) })
    socket.once('error', () => resolve(false))
    socket.setTimeout(1000, () => { socket.destroy(); resolve(false) })
  })
}

function stopAll() {
  for (const child of children.reverse()) {
    if (child.exitCode === null && child.pid) {
      spawnSync('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], {
        windowsHide: true, stdio: 'ignore'
      })
    }
  }
}

async function waitForEnter() {
  const prompt = readline.createInterface({ input: process.stdin, output: process.stdout })
  await new Promise(resolve => prompt.question('\nNhấn Enter để dừng chế độ demo... ', resolve))
  prompt.close()
}

async function main() {
  if (process.platform !== 'win32') throw new Error('RunDemo.cmd chỉ hỗ trợ Windows.')
  for (const executable of ['node.exe', 'npm.cmd', 'firebase.cmd', 'java.exe']) {
    if (!commandExists(executable)) {
      throw new Error(`Thiếu ${executable}. ${executable === 'firebase.cmd' ? 'Cài bằng: npm.cmd install -g firebase-tools' : 'Cài công cụ rồi mở lại PowerShell.'}`)
    }
  }
  for (const port of [9099, 8085, 8080, 5174]) {
    if (!await portFree(port)) throw new Error(`Cổng ${port} đang được sử dụng. Hãy dừng chương trình đang dùng cổng đó rồi chạy lại RunDemo.cmd.`)
  }
  fs.mkdirSync(logs, { recursive: true })
  const firebase = start('firebase', 'firebase.cmd',
    ['emulators:start', '--only', 'auth,firestore', '--project', 'demo-riceguard'], backend)
  await until('firebase', async () => await portOpen(9099) && await portOpen(8085), firebase)

  console.log('Đang nạp dữ liệu mẫu vào Firebase Emulator...')
  let seed
  for (let attempt = 0; attempt < 5; attempt++) {
    seed = spawnSync('node.exe', [path.join(__dirname, 'seed.cjs')], {
      cwd: root, windowsHide: true, encoding: 'utf8', timeout: 120000
    })
    if (seed.status === 0) break
    await sleep(1000)
  }
  if (seed.status !== 0) throw new Error(`Nạp dữ liệu demo thất bại: ${seed.stderr || seed.stdout || seed.error?.message}`)
  process.stdout.write(seed.stdout)

  const password = crypto.randomBytes(24).toString('base64url')
  const email = 'admin.dashboard@riceguard.local'
  const backendEnv = {
    ...process.env,
    ADMIN_BOOTSTRAP_EMAIL: email,
    ADMIN_BOOTSTRAP_PASSWORD: password,
    LOG_LEVEL: 'INFO',
    SERVER_ADDRESS: '127.0.0.1',
    CORS_ALLOWED_ORIGINS: 'http://localhost:5174,http://127.0.0.1:5174',
    DIAGNOSIS_PROVIDER: 'none',
    REMINDERS_ENABLED: 'false'
  }
  const api = start('backend', path.join(backend, 'gradlew.bat'),
    ['bootRunLocal', '--console=plain'], backend, backendEnv)
  await until('backend', async () => {
    const health = await fetch('http://127.0.0.1:8080/actuator/health')
    return health.ok && (await health.json()).status === 'UP'
  }, api, 180000)
  await until('backend', async () => {
    const response = await fetch('http://127.0.0.1:8080/api/v1/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    })
    if (!response.ok) return false
    return (await response.json()).user?.admin === true
  }, api, 90000)

  if (!fs.existsSync(path.join(dashboard, 'node_modules'))) {
    console.log('Đang cài thư viện dashboard (chỉ cần ở lần chạy đầu)...')
    const install = spawnSync('npm.cmd', ['install'], {
      cwd: dashboard, shell: true, windowsHide: true, stdio: 'inherit'
    })
    if (install.status !== 0) throw new Error('npm install thất bại.')
  }
  const web = start('dashboard', 'npm.cmd',
    ['run', 'dev', '--', '--host', '127.0.0.1', '--port', '5174', '--strictPort'],
    dashboard, { ...process.env, VITE_API_BASE_URL: 'http://127.0.0.1:8080/api/v1' })
  await until('dashboard', async () => {
    const response = await fetch('http://127.0.0.1:5174/login')
    return response.ok
  }, web)

  console.log('\n=== DEMO ĐÃ SẴN SÀNG ===')
  console.log('Dashboard demo: http://localhost:5174/login')
  console.log(`Email: ${email}`)
  console.log(`Mật khẩu demo: ${password}`)
  console.log('Dữ liệu chỉ nằm trong Firebase Emulator. Chạy backend bình thường bằng bootRun để dùng dữ liệu thật.')
  spawn('cmd.exe', ['/d', '/s', '/c', 'start "" "http://localhost:5174/login"'], {
    windowsHide: true, stdio: 'ignore', detached: true
  }).unref()
  await waitForEnter()
}

main().catch(error => {
  console.error(`\nLỖI: ${error.message}`)
  process.exitCode = 1
}).finally(stopAll)
