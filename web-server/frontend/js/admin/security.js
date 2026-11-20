import { adminApi } from '../api/admin.js';
import { confirmAction, errorMessage, escapeHtml, openAdminDialog, showToast, tableMessage } from './shared.js';

const RULES = {
  HIGH_RATE_IP: { code: 'R1', name: 'Quá nhiều request', tone: 'rate' },
  SCAN_PATHS: { code: 'R2', name: 'Dò quét đường dẫn', tone: 'scan' },
  BRUTE_FORCE_LOGIN: { code: 'R3', name: 'Đoán mật khẩu', tone: 'auth' },
  ATTACK_SIGNATURE: { code: 'R8', name: 'Mẫu tấn công', tone: 'sig' },
  ML_IDS: { code: 'R9', name: 'IDS học máy', tone: 'ml' }
};
const BLOCK_MINUTES = [5, 30, 60];
const AUTO_REFRESH_SECONDS = 10;
const AUTO_BLOCKER = 'auto-detector';
const MODEL_OPEN_KEY = 'cinehubSecurityModelOpen';

let suspicious = [];
let blocked = [];
let neverBlock = [];
let suspiciousError = '';
let blockedError = '';
let mlIds = null;
let mlIdsError = '';
let filter = 'all';
let evalLive = null;
let evalRunning = false;
let evalThreshold = null;
let initialized = false;
let loading = false;
let ticks = 0;

const $ = (selector) => document.querySelector(selector);
const loadError = (error) => (error instanceof TypeError
  ? 'Không kết nối được backend (Load Balancer :8080 đang tắt?).'
  : errorMessage(error));
const isVisible = () => !$('[data-admin-view="security"]').hidden && !document.hidden;
const pct = (value) => (typeof value === 'number' ? `${(value * 100).toFixed(1)}%` : '—');
const count = (value) => (typeof value === 'number' ? value.toLocaleString('vi-VN') : '—');
const vpct = (value, digits = 2) => (typeof value === 'number' ? `${(value * 100).toFixed(digits).replace('.', ',')}%` : '—');
const vnum = (value, digits = 3) => (typeof value === 'number' ? value.toFixed(digits).replace('.', ',') : '—');

function formatTime(value) {
  const date = new Date(value);
  if (!value || Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(date);
}

function ago(value) {
  const ms = Date.now() - new Date(value).getTime();
  if (!(ms >= 0)) return '—';
  const s = Math.floor(ms / 1000);
  if (s < 60) return 'vừa xong';
  if (s < 3600) return `${Math.floor(s / 60)} phút trước`;
  if (s < 86400) return `${Math.floor(s / 3600)} giờ trước`;
  return `${Math.floor(s / 86400)} ngày trước`;
}

function remaining(until) {
  const ms = new Date(until).getTime() - Date.now();
  if (!(ms > 0)) return 'đã hết hạn';
  const total = Math.ceil(ms / 1000);
  const minutes = Math.floor(total / 60);
  const seconds = String(total % 60).padStart(2, '0');
  return minutes ? `${minutes}:${seconds}` : `${total} giây`;
}

function progress(since, until) {
  const start = new Date(since).getTime();
  const end = new Date(until).getTime();
  if (!(end > start)) return 0;
  return Math.max(0, Math.min(100, ((end - Date.now()) / (end - start)) * 100));
}

function ruleChip(rule) {
  const r = RULES[rule] || { code: '?', name: rule, tone: 'other' };
  return `<span class="sec-rule sec-rule--${r.tone}" title="${escapeHtml(r.name)}"><b>${r.code}</b>${escapeHtml(r.name)}</span>`;
}

function details(message = '') {
  const ml = /diem cao nhat ([\d.]+)/.exec(message);
  const cut = message.indexOf(', vd: ');
  const text = cut >= 0 ? message.slice(0, cut) : message;
  const example = cut >= 0 ? message.slice(cut + 6) : '';
  const score = ml
    ? `<span class="sec-score" style="--score:${Number(ml[1]) * 100}%" title="Điểm mô hình R9 (0–1)"><i></i>${escapeHtml(ml[1])}</span>`
    : '';
  const summary = ml
    ? `${score}<span class="sec-detail__text">Mô hình đánh giá là tấn công</span>`
    : `<span class="sec-detail__text" title="${escapeHtml(text)}">${escapeHtml(text)}</span>`;
  return `<div class="sec-detail">${summary}</div>${example
    ? `<code class="sec-payload" title="${escapeHtml(example)}">${escapeHtml(example)}</code>` : ''}`;
}

function blockedBy(by) {
  return by === AUTO_BLOCKER
    ? '<span class="sec-pill sec-pill--auto" title="Bộ phát hiện bất thường tự động chặn">⚡ Tự động</span>'
    : `<span class="sec-pill">👤 ${escapeHtml(by || '—')}</span>`;
}

function suspiciousActions(item, blockedEntry) {
  const fp = (item.rules || []).includes('ML_IDS')
    ? `<button class="sec-btn sec-btn--ghost" type="button" data-fp-ip="${escapeHtml(item.ip)}"
        title="Đánh dấu cảnh báo R9 của IP này là báo nhầm: lưu request để train lại model và gỡ chặn">↺ Báo nhầm</button>`
    : '';
  if (neverBlock.includes(item.ip)) {
    return `<span class="sec-pill sec-pill--safe" title="Gateway Docker = trình duyệt của admin sau NAT. Chặn IP này sẽ khóa luôn admin.">🛡 IP được bảo vệ</span>`;
  }
  if (blockedEntry) {
    return `${fp}<span class="sec-pill sec-pill--blocked">⛔ Đang chặn · <span data-until="${escapeHtml(blockedEntry.until)}">${remaining(blockedEntry.until)}</span></span>
      <button class="sec-btn" data-unblock-ip="${escapeHtml(item.ip)}" type="button">Gỡ chặn</button>`;
  }
  return `${fp}<div class="sec-split" role="group" aria-label="Chặn IP"><span>Chặn</span>${BLOCK_MINUTES.map((m) =>
    `<button type="button" data-block-ip="${escapeHtml(item.ip)}" data-minutes="${m}" title="Chặn ${m} phút">${m}′</button>`).join('')}</div>`;
}

function renderKpis() {
  const blockedSet = new Set(blocked.map((b) => b.ip));
  const high = suspicious.filter((s) => s.severity === 'HIGH').length;
  const open = suspicious.filter((s) => !blockedSet.has(s.ip) && !neverBlock.includes(s.ip)).length;
  const auto = blocked.filter((b) => b.by === AUTO_BLOCKER).length;
  const found = mlIds?.detections;
  const up = !mlIdsError && mlIds?.up;
  const threshold = mlIds?.model?.threshold;
  const accuracy = (evalLive || mlIds?.model?.evaluation)?.overall?.accuracy;
  const tile = (tone, icon, label, value, sub) => `<div class="sec-kpi sec-kpi--${tone}">
      <span class="sec-kpi__icon">${icon}</span>
      <div><span class="sec-kpi__label">${label}</span><strong>${value}</strong><small>${sub}</small></div>
    </div>`;
  $('#security-kpis').innerHTML = [
    tile(high ? 'danger' : 'neutral', '⚠', 'IP bị cảnh báo', suspiciousError ? '—' : suspicious.length,
      suspiciousError ? 'Lỗi tải dữ liệu' : `${high} mức cao · ${open} chưa chặn`),
    tile(blocked.length ? 'warning' : 'neutral', '⛔', 'Đang bị chặn', blockedError ? '—' : blocked.length,
      blockedError ? 'Lỗi tải dữ liệu' : `${auto} tự động · ${blocked.length - auto} thủ công`),
    tile('ml', '◈', 'R9 phát hiện (24 giờ)', found ? `${found.r9Ips} IP` : '—',
      found ? `${found.onlyR9Ips} IP chỉ R9 bắt được · R8: ${found.r8Ips}` : 'Chưa có số liệu'),
    tile(up ? 'success' : 'danger', up ? '●' : '○', 'Trạng thái R9', up ? 'Đang hoạt động' : 'Không hoạt động',
      up ? `${typeof accuracy === 'number' ? `Độ chính xác ${vpct(accuracy, 1)} · ` : ''}ngưỡng ${threshold ?? '?'}` : 'R1–R8 vẫn chạy bình thường')
  ].join('');
}

function renderSuspicious() {
  const body = $('#suspicious-table-body');
  $('#suspicious-count').textContent = suspicious.length;
  if (suspiciousError) {
    body.innerHTML = tableMessage(4, suspiciousError, 'error');
    return;
  }
  const blockedByIp = new Map(blocked.map((entry) => [entry.ip, entry]));
  const rows = suspicious.filter((item) => {
    if (filter === 'high') return item.severity === 'HIGH';
    if (filter === 'r9') return (item.rules || []).includes('ML_IDS');
    if (filter === 'open') return !blockedByIp.has(item.ip) && !neverBlock.includes(item.ip);
    return true;
  });
  if (!rows.length) {
    body.innerHTML = tableMessage(4, suspicious.length
      ? 'Không có IP nào khớp bộ lọc.'
      : '✓ Không có IP nào bị cảnh báo trong khoảng thời gian này.');
    return;
  }
  body.innerHTML = rows.map((item) => {
    const isHigh = item.severity === 'HIGH';
    return `<tr class="sec-row ${isHigh ? 'sec-row--high' : 'sec-row--medium'}">
      <td class="sec-cell-main">
        <div class="sec-ip-line">
          <strong class="sec-ip">${escapeHtml(item.ip)}</strong>
          <span class="sec-sev ${isHigh ? 'sec-sev--high' : ''}">${isHigh ? 'Cao' : 'Trung bình'}</span>
        </div>
        ${details(item.lastMessage)}
      </td>
      <td><div class="sec-rules">${(item.rules || []).map(ruleChip).join('')}</div></td>
      <td class="sec-cell-count"><strong>${item.alertCount}</strong><small title="${escapeHtml(formatTime(item.lastSeen))}">${ago(item.lastSeen)}</small></td>
      <td class="admin-table__actions"><div class="sec-actions">${suspiciousActions(item, blockedByIp.get(item.ip))}</div></td>
    </tr>`;
  }).join('');
}

function renderBlocked() {
  const body = $('#blocked-table-body');
  $('#blocked-count').textContent = blocked.length;
  $('#security-protected').innerHTML = neverBlock.length
    ? `<span title="Đây là gateway của mạng Docker, tức chính trình duyệt của admin sau NAT; chặn nó sẽ khóa luôn admin.">🛡 IP được bảo vệ: <b>${neverBlock.map(escapeHtml).join(', ')}</b></span>`
    : '';
  if (blockedError) {
    body.innerHTML = tableMessage(5, blockedError, 'error');
    return;
  }
  if (!blocked.length) {
    body.innerHTML = tableMessage(5, '✓ Chưa có IP nào bị chặn.');
    return;
  }
  body.innerHTML = blocked.map((entry) => `<tr>
    <td><strong class="sec-ip">${escapeHtml(entry.ip)}</strong></td>
    <td><div class="sec-reason">${(entry.reason || '—').replace(/^Tu dong: /, '').split(/,\s*/)
      .map((part) => (RULES[part] ? ruleChip(part) : `<span>${escapeHtml(part)}</span>`)).join('')}</div></td>
    <td>${blockedBy(entry.by)}</td>
    <td class="sec-timer">
      <div class="sec-timer__top"><b data-until="${escapeHtml(entry.until)}">${remaining(entry.until)}</b>
        <small>${formatTime(entry.since)} → ${formatTime(entry.until)}</small></div>
      <div class="sec-bar"><i data-since="${escapeHtml(entry.since)}" data-bar-until="${escapeHtml(entry.until)}"
        style="width:${progress(entry.since, entry.until)}%"></i></div>
    </td>
    <td class="admin-table__actions"><button class="sec-btn" data-unblock-ip="${escapeHtml(entry.ip)}" type="button">Gỡ chặn</button></td>
  </tr>`).join('');
}

function renderMlIds() {
  const status = $('#mlids-status');
  const body = $('#mlids-body');
  if (mlIdsError || !mlIds?.up) {
    status.innerHTML = '<span class="sec-pill sec-pill--blocked">○ Không hoạt động</span>';
    $('#mlids-summary').textContent = 'Dịch vụ ml-ids không phản hồi — R9 tạm bỏ qua, R1–R8 vẫn hoạt động.';
    body.innerHTML = `<p class="sec-model__note">${escapeHtml(mlIdsError || 'Dịch vụ ml-ids không phản hồi.')}
      Kiểm tra: <code>docker compose ps ml-ids</code></p>`;
    return;
  }
  const model = mlIds.model || {};
  const stats = mlIds.stats || {};
  status.innerHTML = '<span class="sec-pill sec-pill--ok">● Đang hoạt động</span>';
  $('#mlids-summary').textContent = `v${model.version ?? '?'} · ${model.model || model.features || '—'} · ngưỡng ${model.threshold ?? '?'}`;
  const bar = (value, tone) => `<div class="sec-metric"><span>${pct(value)}</span>
    <div class="sec-bar sec-bar--${tone}"><i style="width:${typeof value === 'number' ? Math.min(100, value * 100) : 0}%"></i></div></div>`;
  const rows = Object.entries(model.byThreshold || {}).map(([threshold, r]) => {
    const active = Number(threshold) === Number(model.threshold);
    return `<tr class="${active ? 'is-active' : ''}">
      <td><b>${escapeHtml(threshold)}</b>${active ? ' <span class="sec-pill sec-pill--ok">đang dùng</span>' : ''}</td>
      <td>${bar(r.csicRecall, 'good')}</td><td>${pct(r.csicFpr)}</td>
      <td>${bar(r.paramsRecall, 'good')}</td><td>${pct(r.paramsFpr)}</td>
      <td>${pct(r.benignFpr)}</td>
    </tr>`;
  }).join('');
  const fact = (label, value) => `<div class="sec-fact"><span>${label}</span><strong>${value}</strong></div>`;
  body.innerHTML = `
    <div class="sec-facts">
      ${fact('Mô hình', `v${escapeHtml(String(model.version ?? '?'))} · ${escapeHtml(model.model || model.features || '—')}`)}
      ${fact('Dữ liệu train', `${count(model.trainValues)} mẫu`)}
      ${fact('Đã chấm từ lúc khởi động', `${count(stats.scored)} request`)}
      ${fact('Báo nhầm chờ train lại', `${count(mlIds.pendingFeedback ?? 0)} mẫu`)}
      ${fact('Tốc độ', `${escapeHtml(String(model.msPerValue ?? '?'))} ms / giá trị`)}
      ${fact('Train lúc', escapeHtml(model.trainedAt ? formatDateTime(model.trainedAt) : '?'))}
    </div>
    <div class="sec-datasets">${(model.datasets || []).map((d) => `<span>${escapeHtml(d)}</span>`).join('')}</div>
    <div class="admin-table-wrap">
      <table class="admin-table sec-table sec-model__table">
        <thead><tr>
          <th>Ngưỡng</th><th>CSIC bắt được</th><th>CSIC báo nhầm</th>
          <th>Payload bắt được</th><th>Payload báo nhầm</th><th>Input thường báo nhầm</th>
        </tr></thead>
        <tbody>${rows || tableMessage(6, 'Model chưa có số liệu đánh giá.')}</tbody>
      </table>
    </div>
    <p class="sec-model__note">Đo trên dữ liệu không dùng để train: CSIC 2010 (theo request), payload sqlmap/XSS/FuzzDB của HttpParamsDataset và input bình thường tổng hợp.
      Ngưỡng cao hơn thì ít báo nhầm hơn nhưng bỏ sót nhiều hơn.</p>`;
}

function setCard(set) {
  const attacks = set.tp + set.fn;
  const correct = set.tp + set.tn;
  const detail = attacks
    ? `<div class="sec-acc__row"><span>Precision <b>${vpct(set.precision, 1)}</b></span><span>Recall <b>${vpct(set.recall, 1)}</b></span><span>F1 <b>${vnum(set.f1)}</b></span></div>`
    : `<div class="sec-acc__row"><span>Chỉ có mẫu bình thường · báo nhầm <b>${count(set.fp)}/${count(set.total)}</b> (${vpct(set.fpr)})</span></div>`;
  const hint = set.key === 'csic'
    ? '<small class="sec-acc__hint">CSIC có nhiều request "bất thường" nhưng không chứa payload (sai kiểu dữ liệu, giá trị lạ) nên recall thấp hơn.</small>'
    : '';
  return `<div class="sec-acc__set">
    <div class="sec-acc__set-head"><b>${escapeHtml(set.name)}</b><span>${count(set.total)} ${escapeHtml(set.unit || 'mẫu')}</span></div>
    <div class="sec-acc__set-value"><strong>${vpct(set.accuracy)}</strong><span>đúng ${count(correct)}/${count(set.total)}</span></div>
    <div class="sec-bar sec-bar--good"><i style="width:${(set.accuracy || 0) * 100}%"></i></div>
    ${detail}${hint}
  </div>`;
}

function matrix(o) {
  return `<table class="sec-cm">
    <thead><tr><th>Thực tế ↓ · Model →</th><th>Tấn công</th><th>Bình thường</th></tr></thead>
    <tbody>
      <tr><th>Tấn công</th>
        <td class="sec-cm__ok"><b>${count(o.tp)}</b><small>TP · bắt đúng</small></td>
        <td class="sec-cm__bad"><b>${count(o.fn)}</b><small>FN · bỏ sót</small></td></tr>
      <tr><th>Bình thường</th>
        <td class="sec-cm__bad"><b>${count(o.fp)}</b><small>FP · báo nhầm</small></td>
        <td class="sec-cm__ok"><b>${count(o.tn)}</b><small>TN · cho qua đúng</small></td></tr>
    </tbody>
  </table>`;
}

function errorList(title, rows) {
  return rows.length ? `<div><h4>${title}</h4><ul>${rows.map((r) =>
    `<li><span class="sec-acc__score">${vnum(r.score)}</span><code>${escapeHtml(r.text)}</code></li>`).join('')}</ul></div>` : '';
}

function renderAccuracy() {
  const body = $('#accuracy-body');
  const select = $('#accuracy-threshold');
  const run = $('#accuracy-run');
  const model = mlIds?.model || {};
  const active = model.threshold == null ? '' : String(model.threshold);
  const options = [...new Set([...Object.keys(model.byThreshold || {}), active].filter(Boolean))]
    .sort((a, b) => Number(a) - Number(b));
  const wanted = evalThreshold ?? active;
  const signature = `${options.join('|')}#${active}`;
  if (select.dataset.signature !== signature) {
    select.innerHTML = options.map((t) => `<option value="${escapeHtml(t)}">${escapeHtml(t)}${t === active ? ' · đang dùng' : ''}</option>`).join('')
      || '<option value="">—</option>';
    select.dataset.signature = signature;
  }
  if (options.includes(wanted)) select.value = wanted;
  run.disabled = evalRunning || !mlIds?.up;
  select.disabled = evalRunning || !mlIds?.up;
  run.textContent = evalRunning ? '⏳ Đang chấm…' : '▶ Kiểm tra ngay';

  const data = evalLive || model.evaluation;
  const key = data ? `${evalLive?.receivedAt || data.evaluatedAt}|${data.threshold}|${evalRunning}` : `none|${Boolean(mlIds?.up)}`;
  if (body.dataset.key === key) return;
  body.dataset.key = key;
  if (!data) {
    body.innerHTML = `<p class="sec-model__note">${mlIds?.up
      ? 'Model chưa có số liệu độ chính xác. Chạy <code>python evaluate.py</code> trong thư mục ml-ids rồi khởi động lại ml-ids.'
      : 'Dịch vụ ml-ids không phản hồi.'}</p>`;
    return;
  }
  const o = data.overall || {};
  const meta = evalLive
    ? `<span class="sec-pill sec-pill--ok">● Vừa kiểm tra trực tiếp</span>
       lúc ${formatTime(evalLive.receivedAt)} · chấm ${count(o.total)} mẫu trong ${vnum(evalLive.seconds, 2)} giây · ngưỡng <b>${escapeHtml(String(data.threshold))}</b>`
    : `<span class="sec-pill">Số liệu lúc train</span>
       ${escapeHtml(data.evaluatedAt || '?')} (UTC) · ngưỡng <b>${escapeHtml(String(data.threshold))}</b> · bấm "Kiểm tra ngay" để model chấm lại trước mặt bạn`;
  const params = (data.sets || []).find((s) => s.byType);
  const types = params ? params.byType.filter((t) => t.attack) : [];
  const errors = evalLive ? (data.sets || []).map((s) =>
    errorList(`${escapeHtml(s.name)} · bỏ sót (điểm gần ngưỡng nhất)`, s.missed || [])
    + errorList(`${escapeHtml(s.name)} · báo nhầm`, s.falseAlarms || [])).join('') : '';

  body.innerHTML = `
    <div class="sec-acc__meta ${evalRunning ? 'is-running' : ''}">${meta}</div>
    <div class="sec-acc__grid">
      <div class="sec-acc__hero">
        <span class="sec-acc__label">Độ chính xác tổng (Accuracy)</span>
        <strong>${vpct(o.accuracy)}</strong>
        <small>Đoán đúng ${count((o.tp || 0) + (o.tn || 0))} / ${count(o.total)} mẫu</small>
        <div class="sec-acc__minis">
          <div><span>Precision</span><b>${vpct(o.precision)}</b><small>báo là tấn công thì đúng</small></div>
          <div><span>Recall</span><b>${vpct(o.recall)}</b><small>tấn công bắt được</small></div>
          <div><span>F1</span><b>${vnum(o.f1)}</b><small>cân bằng P và R</small></div>
          <div><span>AUC</span><b>${vnum(o.auc, 4)}</b><small>1 là hoàn hảo</small></div>
        </div>
        ${matrix(o)}
      </div>
      <div class="sec-acc__sets">${(data.sets || []).map(setCard).join('')}</div>
    </div>
    ${types.length ? `<div class="sec-acc__types"><h4>Theo loại tấn công (HttpParamsDataset)</h4>${types.map((t) => `
      <div class="sec-acc__type"><span>${escapeHtml(t.name)}</span>
        <div class="sec-bar sec-bar--good"><i style="width:${t.rate * 100}%"></i></div>
        <b>${vpct(t.rate, 1)}</b><small>${count(t.correct)}/${count(t.total)}</small></div>`).join('')}</div>` : ''}
    ${errors ? `<details class="sec-acc__errors"><summary>Xem các mẫu model chấm sai</summary><div class="sec-acc__error-grid">${errors}</div></details>` : ''}`;
}

async function runEvaluation() {
  evalRunning = true;
  renderAccuracy();
  try {
    const result = await adminApi.evaluateMlIds($('#accuracy-threshold').value);
    evalLive = { ...result, receivedAt: new Date().toISOString() };
    showToast(`Đã chấm ${count(result.overall?.total)} mẫu: độ chính xác ${vpct(result.overall?.accuracy)}.`);
  } catch (error) {
    showToast(errorMessage(error), 'error');
  } finally {
    evalRunning = false;
    renderAccuracy();
    renderKpis();
  }
}

function formatDateTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(date);
}

function renderAll() {
  renderBlocked();
  renderSuspicious();
  renderMlIds();
  renderAccuracy();
  renderKpis();
}

async function load({ quiet = false } = {}) {
  if (loading) return;
  loading = true;
  if (!quiet) {
    $('#suspicious-table-body').innerHTML = tableMessage(4, 'Đang tải cảnh báo…', 'loading');
    $('#blocked-table-body').innerHTML = tableMessage(5, 'Đang tải danh sách chặn…', 'loading');
  }
  try {
    const [alerts, blockList, mlStatus] = await Promise.allSettled([
      adminApi.getSuspiciousIps($('#security-window').value),
      adminApi.getBlockedIps(),
      adminApi.getMlIdsStatus()
    ]);
    if (mlStatus.status === 'fulfilled') {
      mlIds = mlStatus.value;
      mlIdsError = '';
    } else {
      mlIdsError = loadError(mlStatus.reason);
    }
    if (blockList.status === 'fulfilled') {
      blocked = blockList.value?.blocked || [];
      neverBlock = blockList.value?.neverBlock || [];
      blockedError = '';
    } else {
      blockedError = loadError(blockList.reason);
    }
    if (alerts.status === 'fulfilled') {
      suspicious = alerts.value || [];
      suspiciousError = '';
    } else {
      suspiciousError = loadError(alerts.reason);
    }
    renderAll();
    const failed = suspiciousError && blockedError;
    const live = $('#security-updated');
    live.classList.toggle('is-error', Boolean(failed));
    live.textContent = failed
      ? 'Mất kết nối backend'
      : `Cập nhật ${new Intl.DateTimeFormat('vi-VN', { timeStyle: 'medium' }).format(new Date())}`;
  } finally {
    loading = false;
  }
}

async function blockIp(ip, minutes, reason) {
  await adminApi.blockIp({ ip, minutes, reason });
  showToast(`Đã chặn ${ip} trong ${minutes} phút.`);
  await load({ quiet: true });
}

async function unblockIp(ip) {
  await adminApi.unblockIp(ip);
  showToast(`Đã gỡ chặn ${ip}.`);
  await load({ quiet: true });
}

function openManualBlockDialog() {
  openAdminDialog({
    title: 'Chặn IP thủ công',
    submitLabel: 'Chặn IP',
    content: `<div class="admin-form-grid">
      <label class="admin-form-field admin-form-field--full"><span>Địa chỉ IP *</span>
        <input name="ip" required maxlength="45" placeholder="vd: 203.0.113.50" autocomplete="off"></label>
      <label class="admin-form-field"><span>Thời gian chặn *</span>
        <select name="minutes" required>
          <option value="5">5 phút</option><option value="15">15 phút</option>
          <option value="30" selected>30 phút</option><option value="60">1 giờ</option>
          <option value="1440">1 ngày</option>
        </select></label>
      <label class="admin-form-field"><span>Lý do</span>
        <input name="reason" maxlength="200" placeholder="vd: Dò mật khẩu"></label>
    </div>`,
    onSubmit: (form) => blockIp(
      String(form.get('ip')).trim(),
      Number(form.get('minutes')),
      String(form.get('reason') || '').trim() || 'Chặn thủ công'
    )
  });
}

function tick() {
  if (!isVisible()) return;
  let expired = false;
  document.querySelectorAll('[data-admin-view="security"] [data-until]').forEach((element) => {
    element.textContent = remaining(element.dataset.until);
    if (new Date(element.dataset.until).getTime() <= Date.now()) expired = true;
  });
  document.querySelectorAll('[data-admin-view="security"] [data-bar-until]').forEach((element) => {
    element.style.width = `${progress(element.dataset.since, element.dataset.barUntil)}%`;
  });
  ticks += 1;
  if (expired || ($('#security-auto').checked && ticks >= AUTO_REFRESH_SECONDS)) {
    ticks = 0;
    load({ quiet: true });
  }
}

async function onClick(event) {
  const filterButton = event.target.closest('[data-filter]');
  if (filterButton) {
    filter = filterButton.dataset.filter;
    document.querySelectorAll('#suspicious-filters [data-filter]').forEach((b) =>
      b.classList.toggle('is-active', b === filterButton));
    renderSuspicious();
    return;
  }
  const fpButton = event.target.closest('[data-fp-ip]');
  if (fpButton) {
    const ip = fpButton.dataset.fpIp;
    if (!confirmAction(`Đánh dấu cảnh báo R9 của ${ip} là BÁO NHẦM?\nCác request bị model chấm cao trong 60 phút qua sẽ được lưu làm dữ liệu bình thường để train lại, và IP được gỡ chặn.`)) return;
    fpButton.disabled = true;
    try {
      const r = await adminApi.reportFalsePositive(ip);
      showToast(`Đã lưu ${r.saved}/${r.checked} request của ${ip} làm dữ liệu báo nhầm${r.unblocked ? ', đã gỡ chặn' : ''}. Chạy ml-ids\\retrain.ps1 để model học.`);
      await load({ quiet: true });
    } catch (error) {
      showToast(errorMessage(error), 'error');
      fpButton.disabled = false;
    }
    return;
  }
  const blockButton = event.target.closest('[data-block-ip]');
  const unblockButton = event.target.closest('[data-unblock-ip]');
  const button = blockButton || unblockButton;
  if (!button) return;

  if (blockButton) {
    const ip = blockButton.dataset.blockIp;
    const minutes = Number(blockButton.dataset.minutes);
    if (!confirmAction(`Chặn IP ${ip} trong ${minutes} phút?\nMọi request từ IP này sẽ bị Load Balancer trả về 403.`)) return;
    const item = suspicious.find((entry) => entry.ip === ip);
    const reason = `Cảnh báo: ${(item?.rules || []).join(', ')}`.slice(0, 200);
    button.disabled = true;
    try { await blockIp(ip, minutes, reason); } catch (error) { showToast(errorMessage(error), 'error'); button.disabled = false; }
  } else {
    const ip = unblockButton.dataset.unblockIp;
    if (!confirmAction(`Gỡ chặn IP ${ip}?`)) return;
    button.disabled = true;
    try { await unblockIp(ip); } catch (error) { showToast(errorMessage(error), 'error'); button.disabled = false; }
  }
}

export async function initSecurity() {
  if (!initialized) {
    $('#security-refresh').addEventListener('click', () => load());
    $('#security-window').addEventListener('change', () => load());
    $('#security-block-manual').addEventListener('click', openManualBlockDialog);
    $('[data-admin-view="security"]').addEventListener('click', onClick);
    $('#accuracy-run').addEventListener('click', runEvaluation);
    $('#accuracy-threshold').addEventListener('change', (event) => {
      evalThreshold = event.target.value;
      runEvaluation();
    });

    const panel = $('#mlids-panel');
    try { panel.open = localStorage.getItem(MODEL_OPEN_KEY) === '1'; } catch {}
    panel.addEventListener('toggle', () => {
      try { localStorage.setItem(MODEL_OPEN_KEY, panel.open ? '1' : '0'); } catch {}
    });

    setInterval(tick, 1000);
    initialized = true;
  }
  ticks = 0;
  await load();
}
