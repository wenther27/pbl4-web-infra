export function escapeHtml(value = '') {
  return String(value).replace(/[&<>'"]/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[character]);
}

export function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? escapeHtml(value) : new Intl.DateTimeFormat('vi-VN').format(date);
}

export function movieTypeLabel(type) {
  return ({ movie: 'Phim lẻ', series: 'Phim bộ', anime: 'Anime' })[type] || type || '—';
}

export function statusBadge(status) {
  const config = {
    released: ['Đã phát hành', 'success'],
    ongoing: ['Đang chiếu', 'warning'],
    upcoming: ['Sắp chiếu', '']
  }[status] || [status || '—', ''];
  return `<span class="admin-badge ${config[1] ? `admin-badge--${config[1]}` : ''}">${escapeHtml(config[0])}</span>`;
}

export function showToast(message, type = 'success') {
  const region = document.querySelector('#admin-toast-region');
  const toast = document.createElement('div');
  toast.className = `admin-toast ${type === 'error' ? 'is-error' : ''}`;
  toast.textContent = message;
  region.append(toast);
  setTimeout(() => toast.remove(), 3600);
}

export function errorMessage(error, fallback = 'Không thể hoàn tất thao tác.') {
  if (error?.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (error?.status === 403) return 'Tài khoản không có quyền thực hiện thao tác này.';
  return error?.message || fallback;
}

export function tableMessage(columnCount, message, type = 'empty') {
  return `<tr><td colspan="${columnCount}" class="admin-${type}">${escapeHtml(message)}</td></tr>`;
}

export function confirmAction(message) {
  return window.confirm(message);
}

export function openAdminDialog({ title, content, submitLabel = 'Lưu thay đổi', onSubmit }) {
  const dialog = document.querySelector('#admin-dialog');
  const form = document.querySelector('#admin-dialog-form');
  const titleElement = document.querySelector('#admin-dialog-title');
  const contentElement = document.querySelector('#admin-dialog-content');
  const submitButton = document.querySelector('#admin-dialog-submit');

  titleElement.textContent = title;
  contentElement.innerHTML = content;
  submitButton.textContent = submitLabel;
  submitButton.disabled = false;

  const submitHandler = async (event) => {
    if (event.submitter?.value === 'cancel') return;
    event.preventDefault();
    if (!form.reportValidity()) return;
    submitButton.disabled = true;
    const originalLabel = submitButton.textContent;
    submitButton.textContent = 'Đang lưu…';
    try {
      await onSubmit(new FormData(form));
      dialog.close();
    } catch (error) {
      showToast(errorMessage(error), 'error');
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = originalLabel;
    }
  };

  form.onsubmit = submitHandler;
  dialog.showModal();
}
