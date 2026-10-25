import { authApi } from '../api/auth.js';
import { saveSession } from '../auth/session.js';

function modalTemplate() {
  return `<div class="auth-modal" id="auth-modal" aria-hidden="true">
    <button class="auth-modal__backdrop" type="button" data-auth-close aria-label="Đóng cửa sổ"></button>
    <section class="auth-modal__dialog" role="dialog" aria-modal="true" aria-labelledby="auth-modal-title">
      <button class="auth-modal__close" type="button" data-auth-close aria-label="Đóng">×</button>
      <div class="auth-tabs" role="tablist" aria-label="Chọn đăng nhập hoặc đăng ký">
        <button class="is-active" id="modal-login-tab" type="button" role="tab" aria-selected="true">Đăng nhập</button>
        <button id="modal-register-tab" type="button" role="tab" aria-selected="false">Đăng ký</button>
      </div>
      <div class="auth-modal__heading"><p class="eyebrow">Chào mừng trở lại</p><h2 id="auth-modal-title">Đăng nhập CineHub</h2><p id="auth-modal-subtitle">Nhập thông tin tài khoản để tiếp tục.</p></div>
      <div class="auth-message" id="auth-message" role="status" aria-live="polite" hidden></div>
      <form class="auth-form" id="modal-login-form" novalidate>
        <label><span>Tên đăng nhập</span><input id="modal-login-username" name="username" autocomplete="username" required placeholder="Nhập tên đăng nhập"></label>
        <label><span>Mật khẩu</span><input name="password" type="password" autocomplete="current-password" required placeholder="Nhập mật khẩu"></label>
        <button class="button button--primary button--full" type="submit">Đăng nhập</button>
      </form>
      <form class="auth-form" id="modal-register-form" novalidate hidden>
        <label><span>Tên đăng nhập</span><input name="username" autocomplete="username" minlength="3" required placeholder="Chọn tên đăng nhập"></label>
        <label><span>Email</span><input name="email" type="email" autocomplete="email" required placeholder="ban@example.com"></label>
        <label><span>Mật khẩu</span><input name="password" type="password" autocomplete="new-password" minlength="8" required placeholder="Tối thiểu 8 ký tự"></label>
        <label><span>Xác nhận mật khẩu</span><input name="confirmPassword" type="password" autocomplete="new-password" minlength="8" required placeholder="Nhập lại mật khẩu"></label>
        <button class="button button--primary button--full" type="submit">Tạo tài khoản</button>
      </form>
      <p class="auth-modal__note">Bằng việc tiếp tục, bạn đồng ý với điều khoản sử dụng và chính sách quyền riêng tư của CineHub.</p>
    </section>
  </div>`;
}

export function mountAuthModal() {
  document.body.insertAdjacentHTML('beforeend', modalTemplate());
  const modal = document.querySelector('#auth-modal');
  const loginTab = document.querySelector('#modal-login-tab');
  const registerTab = document.querySelector('#modal-register-tab');
  const loginForm = document.querySelector('#modal-login-form');
  const registerForm = document.querySelector('#modal-register-form');
  const message = document.querySelector('#auth-message');
  const title = document.querySelector('#auth-modal-title');
  const subtitle = document.querySelector('#auth-modal-subtitle');
  let lastFocusedElement;

  function showMessage(text, type = 'error') {
    message.textContent = text;
    message.className = `auth-message is-${type}`;
    message.hidden = false;
  }

  function clearMessage() {
    message.hidden = true;
    message.textContent = '';
  }

  function switchMode(mode) {
    const isLogin = mode === 'login';
    loginTab.classList.toggle('is-active', isLogin);
    registerTab.classList.toggle('is-active', !isLogin);
    loginTab.setAttribute('aria-selected', String(isLogin));
    registerTab.setAttribute('aria-selected', String(!isLogin));
    loginForm.hidden = !isLogin;
    registerForm.hidden = isLogin;
    title.textContent = isLogin ? 'Đăng nhập CineHub' : 'Tạo tài khoản mới';
    subtitle.textContent = isLogin ? 'Nhập thông tin tài khoản để tiếp tục.' : 'Chỉ mất một phút để bắt đầu.';
    clearMessage();
  }

  function openModal(mode = 'login') {
    lastFocusedElement = document.activeElement;
    switchMode(mode);
    modal.classList.add('is-open');
    modal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
    setTimeout(() => modal.querySelector('form:not([hidden]) input')?.focus(), 0);
  }

  function closeModal() {
    modal.classList.remove('is-open');
    modal.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('modal-open');
    lastFocusedElement?.focus();
  }

  function setSubmitting(form, isSubmitting) {
    const button = form.querySelector('button[type="submit"]');
    button.disabled = isSubmitting;
    button.textContent = isSubmitting ? 'Đang xử lý…' : button.dataset.label;
  }

  function validateForm(form) {
    form.querySelectorAll('input').forEach((input) => input.classList.toggle('is-invalid', !input.checkValidity()));
    if (form.checkValidity()) return true;
    showMessage('Vui lòng nhập đầy đủ thông tin đúng định dạng.');
    return false;
  }

  document.querySelectorAll('[data-auth-open]').forEach((button) => button.addEventListener('click', (event) => {
    event.preventDefault();
    openModal(button.dataset.authOpen || 'login');
  }));
  modal.querySelectorAll('[data-auth-close]').forEach((button) => button.addEventListener('click', closeModal));
  document.addEventListener('keydown', (event) => { if (event.key === 'Escape' && modal.classList.contains('is-open')) closeModal(); });
  loginTab.addEventListener('click', () => switchMode('login'));
  registerTab.addEventListener('click', () => switchMode('register'));

  [loginForm, registerForm].forEach((form) => {
    const button = form.querySelector('button[type="submit"]');
    button.dataset.label = button.textContent;
  });

  loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearMessage();
    if (!validateForm(loginForm)) return;
    const data = new FormData(loginForm);
    setSubmitting(loginForm, true);
    try {
      saveSession(await authApi.login({ username: data.get('username').trim(), password: data.get('password') }));
      showMessage('Đăng nhập thành công.', 'success');
      setTimeout(() => window.location.reload(), 550);
    } catch (error) {
      showMessage(error.status === 401 ? 'Tên đăng nhập hoặc mật khẩu không chính xác.' : error.message);
    } finally {
      setSubmitting(loginForm, false);
    }
  });

  registerForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearMessage();
    if (!validateForm(registerForm)) return;
    const data = new FormData(registerForm);
    if (data.get('password') !== data.get('confirmPassword')) {
      showMessage('Mật khẩu xác nhận chưa trùng khớp.');
      return;
    }
    setSubmitting(registerForm, true);
    try {
      await authApi.register({ username: data.get('username').trim(), email: data.get('email').trim(), password: data.get('password') });
      const username = data.get('username').trim();
      registerForm.reset();
      switchMode('login');
      document.querySelector('#modal-login-username').value = username;
      showMessage('Đăng ký thành công. Bạn có thể đăng nhập ngay.', 'success');
    } catch (error) {
      showMessage(error.message);
    } finally {
      setSubmitting(registerForm, false);
    }
  });
}
