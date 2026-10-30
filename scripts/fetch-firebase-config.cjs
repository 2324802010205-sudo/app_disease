const fs = require('fs');
const crypto = require('crypto');
const { execFileSync } = require('child_process');
const os = require('os');
const path = require('path');
const root = path.resolve(__dirname, '..');
const accountFile = [process.env.FIREBASE_SERVICE_ACCOUNT_FILE,
  path.join(root, 'backend', 'firebase-service-account.local.json'),
  path.join(root, 'backend', 'firebase-service-account.local.json.json')].find(f => f && fs.existsSync(f));
if (!accountFile) throw new Error('Không thấy service account. Đặt backend/firebase-service-account.local.json hoặc biến FIREBASE_SERVICE_ACCOUNT_FILE.');
const serviceAccount = JSON.parse(fs.readFileSync(accountFile, 'utf8'));
const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
const now = Math.floor(Date.now() / 1000);
const claims = {
  iss: serviceAccount.client_email,
  scope: 'https://www.googleapis.com/auth/cloud-platform',
  aud: 'https://oauth2.googleapis.com/token',
  iat: now,
  exp: now + 300
};
const input = `${encode({ alg: 'RS256', typ: 'JWT' })}.${encode(claims)}`;
const signature = crypto.sign('RSA-SHA256', Buffer.from(input), serviceAccount.private_key).toString('base64url');
async function main() {
  const auth = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${input}.${signature}` })
  });
  if (!auth.ok) throw new Error(`OAuth HTTP ${auth.status}`);
  const { access_token } = await auth.json();
  const project = encodeURIComponent(serviceAccount.project_id);
  const list = await fetch(`https://firebase.googleapis.com/v1beta1/projects/${project}/androidApps`,
    { headers: { authorization: `Bearer ${access_token}` } });
  if (!list.ok) throw new Error(`Firebase app list HTTP ${list.status}`);
  const apps = (await list.json()).apps || [];
  const app = apps.find(a => a.packageName === 'com.example.project_final');
  if (!app) throw new Error('No registered Firebase Android app for com.example.project_final');
  const installConfig = async () => {
    const response = await fetch(`https://firebase.googleapis.com/v1beta1/${app.name}/config`,
      { headers: { authorization: `Bearer ${access_token}` } });
    if (!response.ok) throw new Error(`Firebase config HTTP ${response.status}`);
    const data = Buffer.from((await response.json()).configFileContents, 'base64');
    const parsed = JSON.parse(data.toString('utf8'));
    if (!parsed.client?.some(c => c.client_info?.android_client_info?.package_name === 'com.example.project_final'))
      throw new Error('Firebase config package mismatch');
    fs.writeFileSync(path.join(root, 'app', 'google-services.json'), data);
    console.log('Firebase Android config installed: app/google-services.json');
  };
  const keystore = path.join(os.homedir(), '.android', 'debug.keystore');
  if (!fs.existsSync(keystore)) return installConfig();
  const cert = execFileSync('keytool', ['-list', '-v', '-keystore', keystore,
    '-alias', 'androiddebugkey', '-storepass', 'android', '-keypass', 'android'], { encoding: 'utf8' });
  const existing = await fetch(`https://firebase.googleapis.com/v1beta1/${app.name}/sha`,
    { headers: { authorization: `Bearer ${access_token}` } });
  if (!existing.ok) throw new Error(`Firebase SHA list HTTP ${existing.status}`);
  const certificates = (await existing.json()).certificates || [];
  for (const [label, certType] of [['SHA1', 'SHA_1'], ['SHA256', 'SHA_256']]) {
    const match = cert.match(new RegExp(`${label}:\\s*([A-Fa-f0-9:]+)`));
    if (!match) throw new Error(`Debug ${label} unavailable`);
    const shaHash = match[1].toUpperCase();
    if (certificates.some(c => c.shaHash.toUpperCase() === shaHash)) {
      console.log(`Debug ${label} already registered.`);
      continue;
    }
    const added = await fetch(`https://firebase.googleapis.com/v1beta1/${app.name}/sha`, {
      method: 'POST', headers: { authorization: `Bearer ${access_token}`, 'content-type': 'application/json' },
      body: JSON.stringify({ shaHash, certType })
    });
    if (!added.ok) throw new Error(`Firebase debug ${label} registration HTTP ${added.status}`);
    console.log(`Debug ${label} registered.`);
  }
  await installConfig();
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
