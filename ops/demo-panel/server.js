// Trang dieu khien demo PBL4: bam nut thay vi go lenh.
//   node ops/demo-panel/server.js      -> mo http://localhost:9000
//
// Server nho (chi dung thu vien co san cua Node, khong can npm install):
//  - Tao luu luong test toi Load Balancer (nguoi dung binh thuong, ke tan cong R1, ke spam)
//    va dem ma tra ve moi giay -> trang ve bieu do truc tiep (200 / 403 / 429 / loi)
//  - Chay cac lenh demo co dinh: tat/bat web-2, lam cham web-3, rolling deploy...
//  - Goi API quan tri cua LB (cong 8090): trang thai backend, IP bi chan, gioi han toc do
//
// An toan: chi nghe tren 127.0.0.1 (may khac khong vao duoc), chi gui request toi LB tren chinh may nay,
// chi chay cac lenh viet san trong ACTIONS (khong nhan lenh tu trinh duyet).

'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const ROOT = path.resolve(__dirname, '..', '..');           // thu muc goc du an
const PORT = Number(process.env.PANEL_PORT || 9000);
const LB_HOST = process.env.PANEL_LB_HOST || '127.0.0.1';
const LB_PORT = Number(process.env.PANEL_LB_PORT || 8080);
const ADMIN_URL = process.env.PANEL_ADMIN_URL || 'http://127.0.0.1:8090';
const MAX_TOTAL_RPS = 120;                                    // tran tong luu luong, tranh lam treo may

// ---------------------------------------------------------------- token quan tri LB
function readEnvFile() {
  try {
    const text = fs.readFileSync(path.join(ROOT, '.env'), 'utf8');
    const line = text.split(/\r?\n/).find((l) => l.startsWith('LB_ADMIN_TOKEN='));
    return line ? line.slice('LB_ADMIN_TOKEN='.length).trim() : null;
  } catch {
    return null;
  }
}
// Thu lan luot: bien moi truong -> .env -> mac dinh cua docker-compose. Cai nao LB nhan thi dung.
const TOKEN_CANDIDATES = [...new Set([process.env.LB_ADMIN_TOKEN, readEnvFile(), 'dev-admin-token-doi-di']
  .filter(Boolean))];
let adminToken = TOKEN_CANDIDATES[0];

// ---------------------------------------------------------------- nhat ky (hien o khung Log)
const logs = [];
let logId = 0;
function log(text, level = 'info') {
  const clean = String(text).replace(/\x1b\[[0-9;]*m/g, '').trimEnd();
  if (!clean) return;
  logs.push({ id: ++logId, time: Date.now(), level, text: clean });
  if (logs.length > 400) logs.shift();
  console.log(`[${level}] ${clean}`);
}

// ---------------------------------------------------------------- goi HTTP toi LB
const agent = new http.Agent({ keepAlive: true, maxSockets: 256 });

function lbRequest(pathname, { ip, timeout = 10000 } = {}) {
  return new Promise((resolve) => {
    const started = Date.now();
    const headers = { 'User-Agent': 'pbl4-demo-panel' };
    if (ip) headers['X-Forwarded-For'] = ip;          // gia lap IP client (LB_TRUST_XFF=true khi demo)
    const req = http.request({ host: LB_HOST, port: LB_PORT, path: pathname, method: 'GET', headers, agent, timeout }, (res) => {
      res.resume();
      res.on('end', () => resolve({ status: res.statusCode, ms: Date.now() - started, instance: res.headers['x-instance-id'] || null }));
    });
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.on('error', () => resolve({ status: 0, ms: Date.now() - started, instance: null }));
    req.end();
  });
}

async function adminCall(method, pathname, body) {
  for (let attempt = 0; attempt < TOKEN_CANDIDATES.length; attempt++) {
    const res = await fetch(ADMIN_URL + pathname, {
      method,
      headers: { 'X-Admin-Token': adminToken, 'X-Admin-User': 'demo-panel', ...(body ? { 'Content-Type': 'application/json' } : {}) },
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(4000)
    });
    if (res.status === 401) {                          // sai token -> thu token tiep theo
      const next = TOKEN_CANDIDATES[(TOKEN_CANDIDATES.indexOf(adminToken) + 1) % TOKEN_CANDIDATES.length];
      if (next === adminToken) break;
      adminToken = next;
      continue;
    }
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    return { status: res.status, data };
  }
  return { status: 401, data: { error: 'LB tu choi moi token (kiem tra LB_ADMIN_TOKEN)' } };
}

// ---------------------------------------------------------------- thong ke theo giay
// series: 60 o gan nhat, moi o = 1 giay: { t, groups: { nhom: { ok, blocked, limited, error, n, ms } } }
const series = [];
function bucket(now = Date.now()) {
  const t = Math.floor(now / 1000);
  let last = series[series.length - 1];
  if (!last || last.t !== t) {
    last = { t, groups: {} };
    series.push(last);
    while (series.length > 61) series.shift();
  }
  return last;
}
function record(group, result) {
  const b = bucket();
  const g = b.groups[group] || (b.groups[group] = { ok: 0, blocked: 0, limited: 0, error: 0, n: 0, ms: 0 });
  g.n += 1;
  g.ms += result.ms;
  if (result.status >= 200 && result.status < 400) g.ok += 1;
  else if (result.status === 403) g.blocked += 1;
  else if (result.status === 429) g.limited += 1;
  else g.error += 1;                                   // 5xx, 0 (khong ket noi duoc)...
}

const ML_IDS_URL = process.env.ML_IDS_URL || 'http://127.0.0.1:8000';
const R9_PAYLOADS = [
  "x','0');waitfor delay '0:0:15';--",
  '<svg/onfocus=alert(1) autofocus>',
  'any\r\nSet-cookie: Tamper=1041264011'
];

async function mlScore(pathname, query) {
  try {
    const res = await fetch(ML_IDS_URL + '/predict', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ requests: [{ path: pathname, query }] }),
      signal: AbortSignal.timeout(3000)
    });
    const data = await res.json();
    return data.scores ? data.scores[0].toFixed(3) : '?';
  } catch {
    return 'không gọi được ml-ids';
  }
}

// ---------------------------------------------------------------- may tao luu luong
const GENERATORS = {
  normal:   { label: 'Người dùng bình thường', rps: 5,  ip: null,           path: '/api/movies' },
  attacker: { label: 'Kẻ tấn công R1 (203.0.113.50)', rps: 5, ip: '203.0.113.50', path: '/api/movies' },
  spam:     { label: 'Kẻ spam (203.0.113.99)', rps: 50, ip: '203.0.113.99', path: '/api/movies' }
};
const running = {};                                    // ten -> { timer, credit, inflight, startedAt }

function startGenerator(name) {
  const g = GENERATORS[name];
  if (!g || running[name]) return;
  const total = Object.keys(running).reduce((s, k) => s + GENERATORS[k].rps, 0) + g.rps;
  if (total > MAX_TOTAL_RPS) {
    log(`Không bật "${g.label}": tổng lưu lượng vượt ${MAX_TOTAL_RPS} req/s`, 'warn');
    return;
  }
  const state = { credit: 0, inflight: 0, startedAt: Date.now() };
  // Moi 100ms gui rps/10 request (cong don phan le) -> luu luong deu
  state.timer = setInterval(() => {
    state.credit += g.rps / 10;
    while (state.credit >= 1) {
      state.credit -= 1;
      if (state.inflight >= 200) continue;             // LB qua cham -> bo bot, khong don ung
      state.inflight += 1;
      lbRequest(g.path, { ip: g.ip }).then((r) => { state.inflight -= 1; record(name, r); });
    }
  }, 100);
  running[name] = state;
  log(`▶ Bật: ${g.label} (${g.rps} req/s)`);
}

function stopGenerator(name) {
  const state = running[name];
  if (!state) return;
  clearInterval(state.timer);
  delete running[name];
  log(`■ Tắt: ${GENERATORS[name].label}`);
}

// ---------------------------------------------------------------- chay lenh co dinh
const busy = new Set();
function runCommand(key, label, cmd, args) {
  if (busy.has(key)) {
    log(`"${label}" đang chạy, chờ xong đã`, 'warn');
    return;
  }
  busy.add(key);
  log(`$ ${cmd} ${args.join(' ')}`, 'cmd');
  const child = spawn(cmd, args, { cwd: ROOT, windowsHide: true });
  const pipe = (stream, level) => {
    let rest = '';
    stream.on('data', (chunk) => {
      const lines = (rest + chunk.toString()).split(/\r?\n/);
      rest = lines.pop();
      lines.forEach((l) => log(l, level));
    });
    stream.on('end', () => rest && log(rest, level));
  };
  pipe(child.stdout, 'out');
  pipe(child.stderr, 'out');                           // docker compose in tien trinh ra stderr
  child.on('error', (e) => { busy.delete(key); log(`Không chạy được ${cmd}: ${e.message}`, 'error'); });
  child.on('close', (code) => {
    busy.delete(key);
    log(code === 0 ? `✔ Xong: ${label}` : `✖ ${label} kết thúc với mã ${code}`, code === 0 ? 'ok' : 'error');
  });
}

const POWERSHELL = process.platform === 'win32' ? 'powershell' : 'pwsh';

// ---------------------------------------------------------------- cac kich ban (nut bam)
const ACTIONS = {
  'gen-normal-start':   () => startGenerator('normal'),
  'gen-normal-stop':    () => stopGenerator('normal'),
  'gen-attacker-start': () => startGenerator('attacker'),
  'gen-attacker-stop':  () => stopGenerator('attacker'),
  'gen-spam-start':     () => startGenerator('spam'),
  'gen-spam-stop':      () => stopGenerator('spam'),

  // DWLC: 30 request lien tiep, dem backend nao tra loi (header X-Instance-Id)
  'dwlc-check': async () => {
    const count = {};
    for (let i = 0; i < 30; i++) {
      const r = await lbRequest('/api/movies');
      record('test', r);
      const key = r.instance || `HTTP ${r.status}`;
      count[key] = (count[key] || 0) + 1;
    }
    log(`DWLC · 30 request được chia: ${Object.entries(count).sort().map(([k, v]) => `${k} = ${v}`).join(' · ')}`, 'ok');
  },

  'web2-stop':  () => runCommand('web2', 'Tắt web-2', 'docker', ['compose', 'stop', 'web-2']),
  'web2-start': () => runCommand('web2', 'Bật web-2', 'docker', ['compose', 'start', 'web-2']),
  'web3-slow':  () => runCommand('web3', 'Làm chậm web-3 (0,25 CPU)', 'docker',
    ['compose', '-f', 'docker-compose.yml', '-f', 'docker-compose.slow.yml', 'up', '-d', '--no-deps', 'web-3']),
  'web3-normal': () => runCommand('web3', 'Trả web-3 về bình thường', 'docker',
    ['compose', 'up', '-d', '--no-deps', 'web-3']),
  'rolling-web2': () => runCommand('rolling', 'Rolling deploy web-2', POWERSHELL,
    ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.join('ops', 'rolling-deploy.ps1'),
      '-Services', 'web-2', '-Token', adminToken]),

  // R8: 1 request chua mau XSS tu IP 203.0.113.50 (chuoi thu vo hai, chi gui toi LB tren may nay)
  'r8-xss': async () => {
    const r = await lbRequest('/api/movies?keyword=%3Cscript%3Ealert(1)%3C%2Fscript%3E', { ip: '203.0.113.50' });
    record('test', r);
    log(`R8 · Gửi 1 request XSS từ 203.0.113.50 → HTTP ${r.status}. Chờ 10–20 giây để bộ phát hiện quét và tự chặn.`,
      r.status === 200 ? 'ok' : 'warn');
  },

  'r9-ml': async () => {
    const ip = '203.0.113.60';
    for (const q of R9_PAYLOADS) {
      const pathname = '/api/movies?title=' + encodeURIComponent(q);
      const r = await lbRequest(pathname, { ip });
      record('test', r);
      const score = await mlScore('/api/movies', 'title=' + encodeURIComponent(q));
      log(`R9 · ${ip} gửi title=${q} → HTTP ${r.status} · điểm ML ${score}`, 'ok');
    }
    log('R9 · Chờ 10–20 giây: trang Admin → Bảo mật sẽ có cảnh báo "R9 · IDS học máy" cho 203.0.113.60. R8 không báo vì regex không có các mẫu này.');
  },
  'r9-normal': async () => {
    for (const q of ['avengers', 'lật mặt', "Ocean's Eleven", 'Spider-Man: No Way Home']) {
      const score = await mlScore('/api/movies', 'title=' + encodeURIComponent(q));
      log(`R9 · Request bình thường title=${q} · điểm ML ${score}`, 'ok');
    }
  },

  // Rate limit nhanh: 80 request trong khoang 1 giay tu 1 IP moi (moi lan bam 1 IP khac -> thung token day).
  // Gui 4 dot x 20 request song song: ket qua khong phu thuoc may nhanh hay cham.
  'ratelimit-burst': async () => {
    const ip = `203.0.113.${100 + Math.floor(Math.random() * 150)}`;
    const count = {};
    for (let round = 0; round < 4; round++) {
      const results = await Promise.all(Array.from({ length: 20 }, () => lbRequest('/api/movies', { ip })));
      results.forEach((r) => { record('test', r); count[r.status] = (count[r.status] || 0) + 1; });
    }
    log(`Rate limit · 80 request dồn dập từ ${ip}: ${Object.entries(count).map(([k, v]) => `HTTP ${k} = ${v}`).join(' · ')}`, 'ok');
  },
  'ratelimit-low':     () => setRateLimit(2, 5),
  'ratelimit-default': () => setRateLimit(10, 20),

  // Don dep giua cac phan demo: go chan moi IP, undrain moi backend, tra rate limit mac dinh
  reset: async () => {
    Object.keys(running).forEach(stopGenerator);
    const blocked = await adminCall('GET', '/admin/blocked');
    for (const entry of blocked.data?.blocked || []) {
      await adminCall('DELETE', `/admin/blocked/${encodeURIComponent(entry.ip)}`);
      log(`Gỡ chặn ${entry.ip}`);
    }
    const backends = await adminCall('GET', '/admin/backends');
    for (const b of backends.data?.backends || []) {
      if (b.draining) {
        await adminCall('POST', `/admin/backends/${b.name}/undrain`);
        log(`Kết thúc bảo trì ${b.name}`);
      }
    }
    await setRateLimit(10, 20);
    log('✔ Đã dọn: không còn IP bị chặn, không backend nào bảo trì, rate limit 10 req/s · burst 20', 'ok');
  }
};

async function setRateLimit(ratePerSecond, burst) {
  const r = await adminCall('POST', '/admin/ratelimit', { ratePerSecond, burst });
  log(r.status === 200 ? `Rate limit = ${ratePerSecond} req/s/IP, burst ${burst}` : `Đổi rate limit lỗi: HTTP ${r.status}`,
    r.status === 200 ? 'ok' : 'error');
}

// ---------------------------------------------------------------- trang thai cho trang web
let lbCache = { at: 0, value: null };
async function lbState() {
  if (Date.now() - lbCache.at < 900) return lbCache.value;
  const value = { backends: null, blocked: null, rateLimit: null, error: null };
  try {
    const [b, k, r] = await Promise.all([
      adminCall('GET', '/admin/backends'), adminCall('GET', '/admin/blocked'), adminCall('GET', '/admin/ratelimit')
    ]);
    value.backends = b.status === 200 ? b.data : null;
    value.blocked = k.status === 200 ? k.data : null;
    value.rateLimit = r.status === 200 ? r.data : null;
    if (b.status !== 200) value.error = `API quản trị LB trả HTTP ${b.status}`;
  } catch (e) {
    value.error = `Không gọi được API quản trị LB (${ADMIN_URL}): ${e.message}`;
  }
  lbCache = { at: Date.now(), value };
  return value;
}

// ---------------------------------------------------------------- HTTP server
function sendJson(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(body));
}

// Bao cao JMeter: /reports/report-dwlc2/... -> load-testing/jmeter/report-dwlc2/...
const REPORT_DIR = path.join(ROOT, 'load-testing', 'jmeter');
const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json',
  '.png': 'image/png', '.svg': 'image/svg+xml', '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf' };

function serveFile(res, file) {
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404); res.end('Khong tim thay'); return; }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream' });
    res.end(data);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  try {
    if (req.method === 'GET' && (url.pathname === '/' || url.pathname === '/index.html')) {
      return serveFile(res, path.join(__dirname, 'index.html'));
    }

    if (req.method === 'GET' && url.pathname === '/api/state') {
      const since = Number(url.searchParams.get('since') || 0);
      return sendJson(res, 200, {
        now: Date.now(),
        series: series.slice(-60),
        generators: Object.fromEntries(Object.entries(GENERATORS).map(([k, g]) => [k, { ...g, running: Boolean(running[k]) }])),
        busy: [...busy],
        lb: await lbState(),
        logs: logs.filter((l) => l.id > since)
      });
    }

    const action = url.pathname.match(/^\/api\/action\/([a-z0-9-]+)$/);
    if (req.method === 'POST' && action) {
      const fn = ACTIONS[action[1]];
      if (!fn) return sendJson(res, 404, { error: 'Không có kịch bản này' });
      Promise.resolve(fn()).catch((e) => log(`Lỗi: ${e.message}`, 'error'));
      return sendJson(res, 202, { ok: true });
    }

    const report = url.pathname.match(/^\/reports\/(report-[A-Za-z0-9_-]+)\/(.*)$/);
    if (req.method === 'GET' && report) {
      const base = path.join(REPORT_DIR, report[1]);
      const file = path.normalize(path.join(base, decodeURIComponent(report[2]) || 'index.html'));
      if (!file.startsWith(base + path.sep) && file !== base) { res.writeHead(403); return res.end(); }
      return serveFile(res, fs.existsSync(file) && fs.statSync(file).isDirectory() ? path.join(file, 'index.html') : file);
    }

    if (req.method === 'GET' && url.pathname === '/api/reports') {
      let names = [];
      try { names = fs.readdirSync(REPORT_DIR).filter((n) => n.startsWith('report-') && fs.existsSync(path.join(REPORT_DIR, n, 'index.html'))); } catch { /* chua co bao cao */ }
      return sendJson(res, 200, names.sort());
    }

    res.writeHead(404);
    res.end('Khong tim thay');
  } catch (e) {
    sendJson(res, 500, { error: e.message });
  }
});

server.on('error', (e) => {
  console.error(e.code === 'EADDRINUSE'
    ? `Cong ${PORT} dang bi dung (trang demo da mo o cua so khac?). Dong cua so do, hoac chay: $env:PANEL_PORT=9001; node ops/demo-panel/server.js`
    : e.message);
  process.exit(1);
});

server.listen(PORT, '127.0.0.1', () => {
  log(`Trang điều khiển demo: http://localhost:${PORT}  ·  LB ${LB_HOST}:${LB_PORT}  ·  API quản trị ${ADMIN_URL}`, 'ok');
});

process.on('SIGINT', () => {
  Object.keys(running).forEach(stopGenerator);
  process.exit(0);
});