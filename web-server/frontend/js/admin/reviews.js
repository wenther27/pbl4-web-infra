import { adminApi } from '../api/admin.js';
import { confirmAction, escapeHtml, formatDate, showToast, tableMessage } from './shared.js';

let movies = [];
let reviews = [];
let initialized = false;

function setMovies(nextMovies) {
  movies = nextMovies;
  const select = document.querySelector('#review-movie-select');
  const selected = select.value;
  select.innerHTML = `<option value="">-- Chọn phim --</option>${movies.map((movie) => `<option value="${movie.id}">${escapeHtml(movie.title)}</option>`).join('')}`;
  if (movies.some((movie) => String(movie.id) === selected)) select.value = selected;
}

function renderReviews() {
  document.querySelector('#review-table-body').innerHTML = reviews.length ? reviews.map((review) => `<tr><td><strong>${escapeHtml(review.username || 'Ẩn danh')}</strong><br><small>#${review.userId || '—'}</small></td><td>★ ${review.rating}</td><td style="white-space:normal;min-width:260px">${escapeHtml(review.content)}</td><td>${formatDate(review.createAt)}</td><td class="admin-table__actions"><button class="admin-icon-button admin-icon-button--danger" data-delete-review="${review.id}" type="button">Xóa</button></td></tr>`).join('') : tableMessage(5, 'Phim này chưa có đánh giá.');
}

async function loadReviews(movieId) {
  const body = document.querySelector('#review-table-body');
  body.innerHTML = tableMessage(5, 'Đang tải đánh giá…', 'loading');
  try { reviews = await adminApi.getReviews(movieId); renderReviews(); } catch (error) { body.innerHTML = tableMessage(5, error.message, 'error'); }
}

export async function initReviews() {
  if (!initialized) {
    const select = document.querySelector('#review-movie-select');
    select.addEventListener('change', () => {
      if (select.value) loadReviews(select.value);
      else document.querySelector('#review-table-body').innerHTML = tableMessage(5, 'Hãy chọn một phim để xem đánh giá.');
    });
    document.querySelector('#review-table-body').addEventListener('click', async (event) => {
      const button = event.target.closest('[data-delete-review]');
      if (!button || !confirmAction('Xóa đánh giá này?')) return;
      try { await adminApi.deleteReview(button.dataset.deleteReview); showToast('Đã xóa đánh giá.'); await loadReviews(select.value); } catch (error) { showToast(error.message, 'error'); }
    });
    window.addEventListener('admin:movies-updated', (event) => setMovies(event.detail));
    initialized = true;
  }
  if (!movies.length) {
    try { setMovies(await adminApi.getMovies()); } catch (error) { showToast(error.message, 'error'); }
  }
}
