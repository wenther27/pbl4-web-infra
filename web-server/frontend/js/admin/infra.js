import { adminApi } from '../api/admin.js';
import { confirmAction, errorMessage, escapeHtml, showToast } from './shared.js';

const REFRESH_MS = 2000;
const HISTORY = 30;

let initialized = false;
let loading = false;
let timer = null;
let previous = null;
const history = new Map();
let lastData = null;

const $ = (selector) => document.querySelector(selector);
const isVisible = () => !$('[data-admin-view="infra"]').hidden && !document.hidden;

const STRATEGY_LABELS = {
  dwlc: 'DWLC (Dynamic Weighted Least Connection)',
  'round-robin': 'Round-robin'
};

function format(value, digits = 0, unit = '') {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  return `${Number(value).toLocaleString('vi-VN', { maximumFractionDigits: digits, minimumFractionDigits: digits })}${unit}`;
}

function statusOf(backend) {
  if (backend.draining && backend.up) return { label: 'Đang bảo trì', css: 'warning', dot: 'is-maintenance' };
  if (backend.draining) return { label: 'Bảo trì · đang khởi động lại', css: 'warning', dot: 'is-maintenance' };
  if (backend.up) return { label: 'Đang phục vụ', css: 'success', dot: 'is-up' };
  return { label: 'DOWN', css: 'danger', dot: 'is-down' };
}

function computeRates(backends, now) {
  const rates = {};
  if (previous) {
    const seconds = Math.max(0.5, (now - previous.time) / 1000);
    backends.forEach((b) => {
      const before = previous.served[b.url];
      rates[b.url] = before === undefined ? null : Math.max(0, (b.served - before) / seconds);
    });
  }
  previous = { time: now, served: Object.fromEntries(backends.map((b) => [b.url, b.served])) };
  backends.forEach((b) => {
    if (rates[b.url] === undefined || rates[b.url] === null) return;
    const list = history.get(b.url) || [];
    list.push(rates[b.url]);
    if (list.length > HISTORY) list.shift();
    history.set(b.url, list);
  });
  return rates;
}

function sparkline(points, maxValue) {
  const width = 220;
  const height = 44;
  if (!points || points.length < 2) {
    return `<svg class="admin-infra-spark" viewBox="0 0 ${width} ${height}" aria-hidden="true"></svg>`;
  }
  const max = Math.max(1, maxValue);
  const step = width / (HISTORY - 1);
  const offset = (HISTORY - points.length) * step;
  const coords = points.map((v, i) => [offset + i * step, height - 3 - (v / max) * (height - 8)]);
  const line = coords.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const area = `${coords[0][0].toFixed(1)},${height} ${line} ${coords[coords.length - 1][0].toFixed(1)},${height}`;
  return `<svg class="admin-infra-spark" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" aria-hidden="true">
    <polygon points="${area}" class="admin-infra-spark__area"></polygon>
    <polyline points="${line}" class="admin-infra-spark__line"></polyline>
  </svg>`;
}

function renderSummary(lb, rates) {
  const backends = lb.backends || [];
  const serving = backends.filter((b) => b.up && !b.draining).length;
  const known = Object.values(rates).filter((v) => v !== null && v !== undefined);
  const total = known.length ? known.reduce((sum, v) => sum + v, 0) : null;
  const strategy = STRATEGY_LABELS[lb.strategy] || lb.strategy || '—';
  $('#infra-summary').innerHTML = `
    <div class="admin-infra-stat"><span>Thuật toán</span><strong>${escapeHtml(strategy)}</strong></div>
    <div class="admin-infra-stat"><span>Đang phục vụ</span><strong>${serving} / ${backends.length} backend</strong></div>
    <div class="admin-infra-stat"><span>Lưu lượng qua LB</span><strong>${format(total, 1, ' req/s')}</strong></div>
    <div class="admin-infra-stat"><span>Health check</span><strong>5 giây/lần · 3 lỗi → DOWN · 2 OK → UP</strong></div>`;
}

function renderBackends(lb, rates) {
  const backends = lb.backends || [];
  const isDwlc = lb.strategy === 'dwlc';
  const maxWeight = Math.max(1, ...backends.map((b) => b.weight || 0));
  const totalRate = backends.reduce((sum, b) => sum + (rates[b.url] || 0), 0);
  const maxRate = Math.max(1, ...backends.flatMap((b) => history.get(b.url) || [0]));
  const servingOthers = (b) => backends.some((x) => x.url !== b.url && x.up && !x.draining);

  $('#infra-backends').innerHTML = backends.map((b) => {
    const status = statusOf(b);
    const rate = rates[b.url];
    const share = totalRate > 0 && rate !== null && rate !== undefined ? (rate / totalRate) * 100 : null;
    const weightPercent = isDwlc && b.weight ? Math.min(100, (b.weight / maxWeight) * 100) : 0;

    const action = b.draining
      ? `<button class="admin-button admin-button--secondary" type="button" data-undrain="${escapeHtml(b.name)}">Kết thúc bảo trì</button>`
      : `<button class="admin-button admin-button--secondary" type="button" data-drain="${escapeHtml(b.name)}"
          ${servingOthers(b) ? '' : 'disabled title="Không thể bảo trì: đây là backend cuối cùng đang phục vụ"'}>Bảo trì (drain)</button>`;

    const dwlcRows = isDwlc
      ? `<div class="admin-infra-weight">
          <div class="admin-infra-weight__head"><span>Trọng số DWLC</span><strong>${format(b.weight, 2)}</strong></div>
          <div class="admin-infra-bar"><span style="width:${weightPercent.toFixed(1)}%"></span></div>
          <small>${b.fresh ? 'tính từ độ trễ và tỉ lệ lỗi thực tế' : 'chưa có số liệu 30 giây gần đây → mặc định 1,00'}</small>
        </div>
        <dl class="admin-infra-metrics">
          <div><dt>Độ trễ TB</dt><dd>${format(b.latencyMs, 0, ' ms')}</dd></div>
          <div><dt>Tỉ lệ lỗi</dt><dd>${format(b.errorRate, 1, '%')}</dd></div>`
      : `<p class="admin-infra-note">Round-robin chia lần lượt, không dùng trọng số.</p>
        <dl class="admin-infra-metrics">`;

    return `<article class="admin-infra-card admin-infra-card--${status.css}">
      <header class="admin-infra-card__head">
        <div><span class="admin-infra-dot ${status.dot}"></span><h3>${escapeHtml(b.name)}</h3></div>
        <span class="admin-badge admin-badge--${status.css}">${status.label}</span>
      </header>
      <p class="admin-infra-url">${escapeHtml(b.url)}</p>
      ${dwlcRows}
          <div><dt>Đang xử lý</dt><dd>${format(b.inFlight)}</dd></div>
          <div><dt>Request/giây</dt><dd>${format(rate, 1)}</dd></div>
          <div><dt>Phần tải</dt><dd>${format(share, 0, '%')}</dd></div>
          <div><dt>Tổng đã nhận</dt><dd>${format(b.served)}</dd></div>
        </dl>
      <div class="admin-infra-spark-wrap">
        <span>Request/giây · 1 phút gần nhất</span>
        ${sparkline(history.get(b.url), maxRate)}
      </div>
      <footer class="admin-infra-card__actions">${action}</footer>
    </article>`;
  }).join('');
}

function renderRateLimit(rateLimit) {
  const panel = $('#infra-ratelimit-panel');
  if (!rateLimit) {
    panel.hidden = true;
    return;
  }
  panel.hidden = false;
  const container = $('#infra-ratelimit');
  const editing = container.contains(document.activeElement);
  const top = rateLimit.topRejected || [];
  const topRows = top.length
    ? top.map((t) => `<li><span class="admin-ip">${escapeHtml(t.ip)}</span><strong>${format(t.rejected)} lần 429</strong></li>`).join('')
    : '<li class="admin-infra-note">Chưa có IP nào vượt ngưỡng.</li>';

  if (!editing) {
    container.innerHTML = `
      <form class="admin-infra-rl-form" id="infra-rl-form">
        <div class="admin-infra-stat"><span>Trạng thái</span><strong>${rateLimit.enabled ? 'Đang bật' : 'Đang tắt'}</strong></div>
        <label class="admin-form-field"><span>Request/giây mỗi IP (0 = tắt)</span>
          <input name="ratePerSecond" type="number" min="0" max="10000" step="0.5" required value="${escapeHtml(rateLimit.ratePerSecond)}"></label>
        <label class="admin-form-field"><span>Burst (số request dồn tối đa)</span>
          <input name="burst" type="number" min="1" max="100000" step="1" required value="${escapeHtml(rateLimit.burst)}"></label>
        <button class="admin-button admin-button--primary" type="submit">Lưu ngưỡng</button>
      </form>
      <div class="admin-infra-rl-side">
        <p class="admin-infra-note">Không giới hạn: ${escapeHtml((rateLimit.exempt || []).join(', ') || '—')} · Đang theo dõi ${format(rateLimit.trackedIps)} IP</p>
        <h4>IP bị trả 429 nhiều nhất</h4>
        <ul class="admin-infra-top" id="infra-rl-top">${topRows}</ul>
      </div>`;
  } else {
    const list = $('#infra-rl-top');
    if (list) list.innerHTML = topRows;
  }
}

async function load({ quiet = false } = {}) {
  if (loading) return;
  loading = true;
  try {
    const data = await adminApi.getInfraOverview();
    const lb = data?.lb || { backends: [] };
    const rates = computeRates(lb.backends || [], Date.now());
    lastData = data;
    $('#infra-error').hidden = true;
    renderSummary(lb, rates);
    renderBackends(lb, rates);
    renderRateLimit(data?.rateLimit);
    $('#infra-updated').textContent = `Cập nhật lúc ${new Intl.DateTimeFormat('vi-VN', { timeStyle: 'medium' }).format(new Date())}`;
  } catch (error) {
    const box = $('#infra-error');
    box.hidden = false;
    box.textContent = error instanceof TypeError
      ? 'Không kết nối được backend (Load Balancer :8080 đang tắt?).'
      : errorMessage(error);
    if (!quiet && !lastData) $('#infra-backends').innerHTML = '';
  } finally {
    loading = false;
  }
}

async function setDrain(name, drain) {
  const message = drain
    ? `Bảo trì ${name}?\nLoad Balancer ngừng gửi request MỚI vào ${name}; request đang xử lý vẫn chạy xong.`
    : `Kết thúc bảo trì ${name}?\n${name} sẽ nhận request trở lại (khi health check báo UP).`;
  if (!confirmAction(message)) return;
  try {
    if (drain) await adminApi.drainBackend(name);
    else await adminApi.undrainBackend(name);
    showToast(drain ? `${name} đang bảo trì: không nhận request mới.` : `${name} nhận request trở lại.`);
  } catch (error) {
    showToast(errorMessage(error), 'error');
  }
  await load({ quiet: true });
}

async function saveRateLimit(form) {
  const body = {
    ratePerSecond: Number(form.get('ratePerSecond')),
    burst: Number(form.get('burst'))
  };
  try {
    await adminApi.updateRateLimit(body);
    document.activeElement?.blur();
    showToast(body.ratePerSecond > 0
      ? `Đã đổi giới hạn: ${body.ratePerSecond} request/giây/IP, burst ${body.burst}.`
      : 'Đã tắt giới hạn tốc độ.');
  } catch (error) {
    showToast(errorMessage(error), 'error');
  }
  await load({ quiet: true });
}

function tick() {
  if (isVisible() && $('#infra-auto').checked) load({ quiet: true });
}

export async function initInfra() {
  if (!initialized) {
    $('#infra-refresh').addEventListener('click', () => load());

    const view = $('[data-admin-view="infra"]');
    view.addEventListener('click', (event) => {
      const drain = event.target.closest('[data-drain]');
      const undrain = event.target.closest('[data-undrain]');
      if (drain) setDrain(drain.dataset.drain, true);
      if (undrain) setDrain(undrain.dataset.undrain, false);
    });
    view.addEventListener('submit', (event) => {
      if (event.target.id !== 'infra-rl-form') return;
      event.preventDefault();
      saveRateLimit(new FormData(event.target));
    });

    timer = setInterval(tick, REFRESH_MS);
    initialized = true;
  }
  await load();
}
