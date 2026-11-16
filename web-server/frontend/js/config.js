const apiFromUrl = new URLSearchParams(window.location.search).get('api');
if (apiFromUrl) localStorage.setItem('cinehubApiBaseUrl', apiFromUrl);

export const APP_CONFIG = Object.freeze({
  apiBaseUrl: localStorage.getItem('cinehubApiBaseUrl') || 'http://localhost:8080/api',
  requestTimeout: 7000,
  pageSize: 12,
  apiServers: [
    { name: 'web-1', url: 'http://localhost:8081/api' },
    { name: 'web-2', url: 'http://localhost:8082/api' },
    { name: 'web-3', url: 'http://localhost:8083/api' }
  ]
});
