import { clearSession, getSession } from '../auth/session.js';
import { mountAuthModal } from './auth-modal.js';

function escapeHtml(value = '') {
  return String(value).replace(/[&<>'"]/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char]);
}

function headerTemplate(root, activePage, session) {
  const active = (page) => activePage === page ? 'is-active' : '';
  const authLabel = session ? escapeHtml(session.username) : 'Đăng nhập';
  const authHref = '#';
  const avatarInitial = session ? escapeHtml(String(session.username).trim().charAt(0).toUpperCase() || 'U') : '';
  const roleLabel = session?.role === 'ADMIN' ? 'Quản trị viên' : 'Thành viên';
  const mobileAdminLink = session?.role === 'ADMIN' ? `<a class="nav-admin-mobile" href="${root}/pages/admin/index.html">Trang quản trị</a>` : '';
  return `<div class="site-header"><div class="container site-header__inner">
    <a class="brand" href="${root}/index.html" aria-label="CineHub - Trang chủ"><span class="brand__mark">▣</span><span>Cine<em>Hub</em></span></a>
    <nav class="nav" id="main-nav" aria-label="Điều hướng chính">
      <a class="${active('home')}" href="${root}/index.html">Trang chủ</a>
      <a class="${active('catalog')}" href="${root}/pages/catalog.html">Danh mục</a>
      <a href="${root}/pages/catalog.html?type=movie">Phim chiếu rạp</a>
      <a href="${root}/pages/catalog.html?type=series">Phim bộ</a>
      <a href="${root}/pages/catalog.html?type=anime">Anime</a>
      ${mobileAdminLink}
      ${session ? `<a class="nav-auth-mobile" href="#" data-auth-logout>${authLabel} · Đăng xuất</a>` : `<a class="nav-auth-mobile" href="${authHref}" data-auth-open="login">Đăng nhập</a><a class="nav-auth-mobile" href="${authHref}" data-auth-open="register">Đăng ký</a>`}
    </nav>
    <form class="header-search" action="${root}/pages/catalog.html"><label class="sr-only" for="site-search">Tìm phim</label><input id="site-search" name="q" type="search" placeholder="Tìm kiếm phim…"><button type="submit" aria-label="Tìm kiếm"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5"></circle><path d="m16 16 4 4"></path></svg></button></form>
    ${session
      ? `<div class="member-menu"><button class="member-avatar" id="member-button" type="button" aria-label="Mở menu tài khoản của ${authLabel}" aria-haspopup="true" aria-controls="member-dropdown" aria-expanded="false"><span class="member-avatar__circle" aria-hidden="true">${avatarInitial}</span><span class="member-avatar__name">${authLabel}</span><svg viewBox="0 0 20 20" aria-hidden="true"><path d="m6 8 4 4 4-4"></path></svg></button><div class="member-dropdown" id="member-dropdown"><div class="member-dropdown__profile"><div class="member-dropdown__avatar" aria-hidden="true">${avatarInitial}</div><div><strong>${authLabel}</strong><small>${roleLabel}</small></div></div>${session.role === 'ADMIN' ? `<a class="member-dropdown__link" href="${root}/pages/admin/index.html"><span aria-hidden="true">▦</span>Trang quản trị</a>` : ''}<button class="member-dropdown__logout" id="logout-button" type="button"><span aria-hidden="true">↪</span>Đăng xuất</button></div></div>`
      : `<div class="header-auth-actions"><a class="header-auth-button header-auth-button--login" href="${authHref}" data-auth-open="login">Đăng nhập</a><a class="header-auth-button header-auth-button--register" href="${authHref}" data-auth-open="register">Đăng ký</a></div>`}
    <button class="menu-button" id="menu-button" type="button" aria-controls="main-nav" aria-expanded="false">☰</button>
  </div></div>`;
}

function footerTemplate(root) {
  return `<div class="site-footer"><div class="container">
    <div class="footer-grid"><div class="footer-grid__brand"><a class="brand" href="${root}/index.html"><span class="brand__mark">▣</span><span>Cine<em>Hub</em></span></a><p>Nền tảng trải nghiệm điện ảnh tuyển chọn, rõ ràng và tập trung vào nội dung.</p></div>
      <div><h3>Thể loại</h3><div class="footer-links"><a href="${root}/pages/catalog.html?type=movie">Phim điện ảnh</a><a href="${root}/pages/catalog.html?type=series">Phim bộ</a><a href="${root}/pages/catalog.html?type=anime">Anime</a></div></div>
      <div><h3>Dịch vụ</h3><div class="footer-links"><a href="${root}/pages/catalog.html">Kho phim</a><a href="#">Gói thành viên</a><a href="#">Phim yêu thích</a></div></div>
      <div><h3>Hỗ trợ</h3><div class="footer-links"><a href="#">Trung tâm hỗ trợ</a><a href="#">Điều khoản sử dụng</a><a href="#">Quyền riêng tư</a></div></div>
    </div><div class="footer-bottom"><span>© 2026 CineHub. Bảo lưu toàn bộ quyền.</span><span>Thiết kế CineHub Lumina</span></div>
  </div></div>`;
}

export function mountLayout() {
  const root = document.body.dataset.root || '.';
  const page = document.body.dataset.page || '';
  const session = getSession();
  document.querySelector('#site-header').innerHTML = headerTemplate(root, page, session);
  document.querySelector('#site-footer').innerHTML = footerTemplate(root);
  if (!session) mountAuthModal();

  const button = document.querySelector('#menu-button');
  const nav = document.querySelector('#main-nav');
  button?.addEventListener('click', () => {
    const isOpen = nav.classList.toggle('is-open');
    button.setAttribute('aria-expanded', String(isOpen));
    document.body.classList.toggle('menu-open', isOpen);
  });

  const memberButton = document.querySelector('#member-button');
  const memberDropdown = document.querySelector('#member-dropdown');
  const closeMemberMenu = () => {
    memberDropdown?.classList.remove('is-open');
    memberButton?.setAttribute('aria-expanded', 'false');
  };
  memberButton?.addEventListener('click', (event) => {
    event.stopPropagation();
    const isOpen = memberDropdown.classList.toggle('is-open');
    memberButton.setAttribute('aria-expanded', String(isOpen));
  });
  memberDropdown?.addEventListener('click', (event) => event.stopPropagation());
  document.addEventListener('click', closeMemberMenu);
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      closeMemberMenu();
      memberButton?.focus();
    }
  });

  document.querySelector('#logout-button')?.addEventListener('click', () => {
    clearSession();
    window.location.href = `${root}/index.html`;
  });
  document.querySelector('[data-auth-logout]')?.addEventListener('click', (event) => {
    event.preventDefault();
    clearSession();
    window.location.href = `${root}/index.html`;
  });
}
