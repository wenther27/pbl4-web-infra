// Khung nho goc phai duoi man hinh, dung khi demo/test load balancing:
// - Dong tren: server nao (web-1/2/3) vua tra loi request gan nhat,
//   doc tu header X-Instance-Id backend gan vao moi response.
// - Dong duoi: nut chuyen frontend sang goi backend khac. Phien dang nhap
//   (JWT trong Local Storage) giu nguyen -> thay duoc backend la stateless.
import { APP_CONFIG } from '../config.js';

let badge;
let statusLine;

function ensureBadge() {
  if (badge) return;
  badge = document.createElement('div');
  badge.style.cssText = [
    'position:fixed', 'right:16px', 'bottom:16px', 'z-index:9999',
    'padding:10px 12px', 'border-radius:14px', 'font:600 13px/1.3 system-ui,sans-serif',
    'color:#fff', 'background:#1f2937', 'box-shadow:0 4px 14px rgba(0,0,0,.25)',
    'transition:transform .2s', 'display:grid', 'gap:8px'
  ].join(';');

  statusLine = document.createElement('div');
  statusLine.textContent = 'Đang kết nối...';
  badge.appendChild(statusLine);

  const buttons = document.createElement('div');
  buttons.style.cssText = 'display:flex;gap:6px';
  APP_CONFIG.apiServers.forEach((server) => {
    const button = document.createElement('button');
    const active = server.url === APP_CONFIG.apiBaseUrl;
    button.type = 'button';
    button.textContent = server.name;
    button.title = server.url;
    button.style.cssText = [
      'border:0', 'border-radius:8px', 'padding:4px 8px', 'cursor:pointer',
      'font:600 12px system-ui,sans-serif',
      active ? 'background:#f59e0b;color:#111' : 'background:#374151;color:#e5e7eb'
    ].join(';');
    button.addEventListener('click', () => {
      localStorage.setItem('cinehubApiBaseUrl', server.url);
      const url = new URL(window.location.href);
      url.searchParams.delete('api');
      window.location.href = url.toString();
    });
    buttons.appendChild(button);
  });
  badge.appendChild(buttons);
  document.body.appendChild(badge);
}

function pulse() {
  badge.style.transform = 'scale(1.06)';
  setTimeout(() => { badge.style.transform = 'scale(1)'; }, 200);
}

export function showServedBy(instanceId, apiBaseUrl) {
  if (!instanceId) return;
  ensureBadge();
  badge.style.background = '#1f2937';
  statusLine.textContent = `Phục vụ bởi: ${instanceId}`;
  statusLine.title = apiBaseUrl;
  pulse();
}

export function showServerDown(apiBaseUrl) {
  ensureBadge();
  badge.style.background = '#b91c1c';
  statusLine.textContent = 'Mất kết nối backend';
  statusLine.title = apiBaseUrl;
  pulse();
}

// Hien khung ngay khi mo trang, ke ca truoc khi co request nao
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', ensureBadge);
} else {
  ensureBadge();
}
