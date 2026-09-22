export const APP_CONFIG = Object.freeze({
  apiBaseUrl: localStorage.getItem('cinehubApiBaseUrl') || 'http://localhost:8080/api',
  requestTimeout: 7000,
  pageSize: 12
});
