import { adminApi } from '../api/admin.js';
import { confirmAction, errorMessage, escapeHtml, openAdminDialog, showToast, tableMessage } from './shared.js';

// Trang Quan tri > Bao mat:
//  - Bang 1: IP bi bo phat hien bat thuong canh bao (R1, R2, R3), gop theo IP
//  - Bang 2: IP dang bi Load Balancer chan, dem nguoc thoi gian con lai
// Du lieu lay qua backend (/api/admin/security/...), backend moi la noi giu token cua LB.

const RULE_LABELS = {
  HIGH_RATE_IP: 'R1 · Quá nhiều request',
  SCAN_PATHS: 'R2 · Dò quét đường dẫn',
  BRUTE_FORCE_LOGIN: 'R3 · Đoán mật khẩu'
};
const BLOCK_MINUTES = [5, 30, 60];
const AUTO_REFRESH_SECONDS = 10;

let suspicious = [];
let blocked = [];
let neverBlock = [];
let suspiciousError = '';
let blockedError = '';
let initialized = false;
let loading = false;
let ticks = 0;

const $ = (selector) => document.querySelector(selector);
// Loi mang (fetch that bai) -> cau de hieu; con lai dung thong bao cua backend
const loadError = (error) => (error instanceof TypeError
  ? 'Không kết nối được backend (Load Balancer :8080 đang tắt?).'
  : errorMessage(error));
const isVisible = () => !$('[data-admin-view="security"]').hidden && !document.hidden;

function formatTime(value) {
  const date = new Date(value);
  if (!value || Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit', minute: '2-digit', second: '2-digit', day: '2-digit', month: '2-digit'
  }).format(date);
}

function remaining(until) {
  const ms = new Date(until).getTime() - Date.now();
  if (!(ms > 0)) return 'đã hết hạn';
  const total = Math.ceil(ms / 1000);
  const minutes = Math.floor(total / 60);
  const seconds = String(total % 60).padStart(2, '0');
  return minutes ? `còn ${minutes} phút ${seconds} giây` : `còn ${total} giây`;
}

function ruleBadges(rules = []) {
  return `<div class="admin-rule-list">${rules.map((rule) =>
    `<span class="admin-badge">${escapeHtml(RULE_LABELS[rule] || rule)}</span>`).join('')}</div>`;
}

function severityBadge(severity) {
  return severity === 'HIGH'
    ? '<span class="admin-badge admin-badge--danger">Cao</span>'
    : '<span class="admin-badge admin-badge--warning">Trung bình</span>';
}

// Cot thao tac cua bang canh bao: IP duoc bao ve / dang bi chan / nut chan
function suspiciousActions(item, blockedEntry) {
  if (neverBlock.includes(item.ip)) {
    return '<span class="admin-badge" title="Gateway Docker = trình duyệt của admin sau NAT. Chặn IP này sẽ khóa luôn admin.">IP được bảo vệ</span>';
  }
  if (blockedEntry) {
    return `<span class="admin-badge admin-badge--danger">Đang chặn</span>
      <small class="admin-countdown" data-until="${escapeHtml(blockedEntry.until)}">${remaining(blockedEntry.until)}</small>
      <button class="admin-icon-button" data-unblock-ip="${escapeHtml(item.ip)}" type="button">Gỡ chặn</button>`;
  }
  return BLOCK_MINUTES.map((minutes) =>
    `<button class="admin-icon-button admin-icon-button--danger" type="button"
      data-block-ip="${escapeHtml(item.ip)}" data-minutes="${minutes}">Chặn ${minutes}′</button>`).join('');
}

function renderSuspicious() {
  const body = $('#suspicious-table-body');
  $('#suspicious-count').textContent = `${suspicious.length} IP`;
  if (suspiciousError) {
    body.innerHTML = tableMessage(6, suspiciousError, 'error');
    return;
  }
  if (!suspicious.length) {
    body.innerHTML = tableMessage(6, 'Không có IP nào bị cảnh báo trong khoảng thời gian này.');
    return;
  }
  const blockedByIp = new Map(blocked.map((entry) => [entry.ip, entry]));
  body.innerHTML = suspicious.map((item) => `<tr>
    <td><strong class="admin-ip">${escapeHtml(item.ip)}</strong>
      <small class="admin-alert-message">${escapeHtml(item.lastMessage)}</small></td>
    <td>${ruleBadges(item.rules)}</td>
    <td>${severityBadge(item.severity)}</td>
    <td>${item.alertCount}</td>
    <td>${formatTime(item.lastSeen)}</td>
    <td class="admin-table__actions"><div class="admin-security-actions">${suspiciousActions(item, blockedByIp.get(item.ip))}</div></td>
  </tr>`).join('');
}

function renderBlocked() {
  const body = $('#blocked-table-body');
  $('#blocked-count').textContent = `${blocked.length} IP`;
  $('#security-protected').textContent = neverBlock.length
    ? `IP được bảo vệ (không thể chặn): ${neverBlock.join(', ')}. Đây là gateway của mạng Docker, tức chính trình duyệt của admin sau NAT; chặn nó sẽ khóa luôn admin.`
    : 'Mọi IP đều có thể bị chặn.';
  if (blockedError) {
    body.innerHTML = tableMessage(6, blockedError, 'error');
    return;
  }
  if (!blocked.length) {
    body.innerHTML = tableMessage(6, 'Chưa có IP nào bị chặn.');
    return;
  }
  body.innerHTML = blocked.map((entry) => `<tr>
    <td><strong class="admin-ip">${escapeHtml(entry.ip)}</strong></td>
    <td style="white-space:normal;min-width:200px">${escapeHtml(entry.reason || '—')}</td>
    <td>${escapeHtml(entry.by || '—')}</td>
    <td>${formatTime(entry.since)}</td>
    <td>${formatTime(entry.until)}<small class="admin-countdown" data-until="${escapeHtml(entry.until)}">${remaining(entry.until)}</small></td>
    <td class="admin-table__actions"><button class="admin-icon-button" data-unblock-ip="${escapeHtml(entry.ip)}" type="button">Gỡ chặn</button></td>
  </tr>`).join('');
}

async function load({ quiet = false } = {}) {
  if (loading) return;
  loading = true;
  if (!quiet) {
    $('#suspicious-table-body').innerHTML = tableMessage(6, 'Đang tải cảnh báo…', 'loading');
    $('#blocked-table-body').innerHTML = tableMessage(6, 'Đang tải danh sách chặn…', 'loading');
  }
  try {
    // Goi song song; 1 ben loi (vd ES tat) thi ben kia van hien
    const [alerts, blockList] = await Promise.allSettled([
      adminApi.getSuspiciousIps($('#security-window').value),
      adminApi.getBlockedIps()
    ]);
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
    renderBlocked();
    renderSuspicious();
    $('#security-updated').textContent = `Cập nhật lúc ${new Intl.DateTimeFormat('vi-VN', { timeStyle: 'medium' }).format(new Date())}`;
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

// Dong ho 1 giay: cap nhat dem nguoc; moi 10 giay tu tai lai neu bat "Tu dong lam moi"
function tick() {
  if (!isVisible()) return;
  let expired = false;
  document.querySelectorAll('[data-admin-view="security"] [data-until]').forEach((element) => {
    element.textContent = remaining(element.dataset.until);
    if (new Date(element.dataset.until).getTime() <= Date.now()) expired = true;
  });
  ticks += 1;
  if (expired || ($('#security-auto').checked && ticks >= AUTO_REFRESH_SECONDS)) {
    ticks = 0;
    load({ quiet: true });
  }
}

export async function initSecurity() {
  if (!initialized) {
    $('#security-refresh').addEventListener('click', () => load());
    $('#security-window').addEventListener('change', () => load());
    $('#security-block-manual').addEventListener('click', openManualBlockDialog);

    $('[data-admin-view="security"]').addEventListener('click', async (event) => {
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
    });

    setInterval(tick, 1000);
    initialized = true;
  }
  ticks = 0;
  await load();
}
