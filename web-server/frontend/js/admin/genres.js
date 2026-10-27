import { adminApi } from '../api/admin.js';
import { invalidateDashboard } from './dashboard.js';
import { escapeHtml, openAdminDialog, showToast } from './shared.js';

let loaded = false;
let initialized = false;

function renderGenres(genres) {
  const grid = document.querySelector('#genre-grid');
  grid.innerHTML = genres.length ? genres.map((genre) => `<article class="admin-genre-card"><span>◇</span><h3>${escapeHtml(genre.name)}</h3><p>/${escapeHtml(genre.slug)}</p></article>`).join('') : '<p class="admin-empty">Chưa có thể loại trong hệ thống.</p>';
}

async function loadGenres() {
  const grid = document.querySelector('#genre-grid');
  grid.innerHTML = '<p class="admin-loading">Đang tải thể loại…</p>';
  try {
    renderGenres(await adminApi.getGenres());
    loaded = true;
  } catch (error) {
    grid.innerHTML = `<p class="admin-error">${escapeHtml(error.message)}</p>`;
  }
}

export async function initGenres() {
  if (!initialized) {
    document.querySelector('#add-genre').addEventListener('click', () => openAdminDialog({
      title: 'Thêm thể loại',
      submitLabel: 'Tạo thể loại',
      content: `<div class="admin-form-grid"><label class="admin-form-field admin-form-field--full"><span>Tên thể loại *</span><input name="name" required maxlength="50" placeholder="Ví dụ: Khoa học viễn tưởng"></label><label class="admin-form-field admin-form-field--full"><span>Slug *</span><input name="slug" required maxlength="50" pattern="[a-z0-9-]+" placeholder="khoa-hoc-vien-tuong"><small>Chỉ dùng chữ thường không dấu, số và dấu gạch ngang.</small></label></div>`,
      onSubmit: async (data) => {
        await adminApi.createGenre({ name: data.get('name').trim(), slug: data.get('slug').trim() });
        showToast('Đã thêm thể loại.');
        invalidateDashboard();
        await loadGenres();
      }
    }));
    initialized = true;
  }
  if (!loaded) await loadGenres();
}
