// Doi backend ngay tren URL: ?api=http://localhost:8082/api (duoc nho trong Local Storage),
// hoac bam nut web-1/2/3 o goc phai duoi man hinh (xem components/server-badge.js).
const apiFromUrl = new URLSearchParams(window.location.search).get('api');
if (apiFromUrl) localStorage.setItem('cinehubApiBaseUrl', apiFromUrl);

export const APP_CONFIG = Object.freeze({
  apiBaseUrl: localStorage.getItem('cinehubApiBaseUrl') || 'http://localhost:8080/api',
  requestTimeout: 7000,
  pageSize: 12,
  // Cac backend khi chay docker compose (web-1/2/3)
  apiServers: [
    { name: 'web-1', url: 'http://localhost:8081/api' },
    { name: 'web-2', url: 'http://localhost:8082/api' },
    { name: 'web-3', url: 'http://localhost:8083/api' }
  ]
});
