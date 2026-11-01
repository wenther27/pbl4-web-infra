import { clearSession } from '../auth/session.js';

const pageTitles = {
  dashboard: 'Tổng quan',
  movies: 'Quản lý phim',
  genres: 'Quản lý thể loại',
  episodes: 'Quản lý tập phim',
  users: 'Quản lý người dùng',
  reviews: 'Quản lý đánh giá',
  security: 'Bảo mật'
};

export function mountAdminLayout(session, onRouteChange) {
  const shell = document.querySelector('#admin-shell');
  const sidebar = document.querySelector('#admin-sidebar');
  const menuButton = document.querySelector('#admin-menu-button');
  shell.hidden = false;
  document.querySelector('#admin-username').textContent = session.username;
  document.querySelector('#dashboard-name').textContent = session.username;
  document.querySelector('#admin-avatar').textContent = session.username.slice(0, 1).toUpperCase();
  document.querySelector('#dashboard-date').textContent = new Intl.DateTimeFormat('vi-VN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date());

  function activateRoute() {
    const requestedRoute = window.location.hash.slice(1);
    const route = pageTitles[requestedRoute] ? requestedRoute : 'dashboard';
    document.querySelectorAll('[data-admin-view]').forEach((view) => { view.hidden = view.dataset.adminView !== route; });
    document.querySelectorAll('[data-admin-route]').forEach((link) => link.classList.toggle('is-active', link.dataset.adminRoute === route));
    document.querySelector('#admin-page-title').textContent = pageTitles[route];
    document.querySelector('#admin-breadcrumb').textContent = `Quản trị / ${pageTitles[route]}`;
    sidebar.classList.remove('is-open');
    menuButton.setAttribute('aria-expanded', 'false');
    onRouteChange(route);
  }

  window.addEventListener('hashchange', activateRoute);
  document.querySelectorAll('[data-go-route]').forEach((button) => button.addEventListener('click', () => { window.location.hash = button.dataset.goRoute; }));
  menuButton.addEventListener('click', () => {
    const isOpen = sidebar.classList.toggle('is-open');
    menuButton.setAttribute('aria-expanded', String(isOpen));
  });
  document.querySelector('#admin-logout').addEventListener('click', () => {
    clearSession();
    window.location.href = '../../index.html';
  });
  activateRoute();
}
