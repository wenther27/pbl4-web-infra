import { adminApi } from '../api/admin.js';
import { invalidateDashboard } from './dashboard.js';
import { bindImagePreviews, imageUploadField, resolveImageUrl } from './image-upload.js';
import { confirmAction, escapeHtml, formatDate, movieTypeLabel, openAdminDialog, showToast, statusBadge, tableMessage } from './shared.js';

let movies = [];
let genres = [];
let initialized = false;

function genreChecks(selectedIds = []) {
  return genres.map((genre) => `<label><input type="checkbox" name="genreIds" value="${genre.id}" ${selectedIds.includes(Number(genre.id)) ? 'checked' : ''}>${escapeHtml(genre.name)}</label>`).join('') || '<span>Chưa có thể loại.</span>';
}

function movieForm(movie = {}) {
  const selectedIds = (movie.genres || []).map((genre) => Number(genre.id));
  return `<div class="admin-form-grid">
    <label class="admin-form-field admin-form-field--full"><span>Tên phim *</span><input name="title" required maxlength="255" value="${escapeHtml(movie.title || '')}"></label>
    <label class="admin-form-field admin-form-field--full"><span>Mô tả</span><textarea name="description">${escapeHtml(movie.description || '')}</textarea></label>
    <label class="admin-form-field"><span>Loại phim *</span><select name="movieType" required><option value="movie" ${movie.movieType === 'movie' ? 'selected' : ''}>Phim lẻ</option><option value="series" ${movie.movieType === 'series' ? 'selected' : ''}>Phim bộ</option><option value="anime" ${movie.movieType === 'anime' ? 'selected' : ''}>Anime</option></select></label>
    <label class="admin-form-field"><span>Trạng thái *</span><select name="status" required><option value="released" ${movie.status === 'released' ? 'selected' : ''}>Đã phát hành</option><option value="ongoing" ${movie.status === 'ongoing' ? 'selected' : ''}>Đang chiếu</option><option value="upcoming" ${movie.status === 'upcoming' ? 'selected' : ''}>Sắp chiếu</option></select></label>
    <label class="admin-form-field"><span>Ngày phát hành</span><input name="releaseDate" type="date" value="${escapeHtml(movie.releaseDate || '')}"></label>
    <label class="admin-form-field"><span>Thời lượng (phút) *</span><input name="duration" type="number" required min="1" value="${movie.duration || ''}"></label>
    <label class="admin-form-field"><span>Điểm đánh giá *</span><input name="rating" type="number" required min="0" max="10" step="0.1" value="${movie.rating ?? 0}"></label>
    ${imageUploadField({ label: 'Poster phim', urlName: 'posterUrl', fileName: 'posterFile', currentUrl: movie.posterUrl || '' })}
    ${imageUploadField({ label: 'Ảnh nền phim', urlName: 'backdropUrl', fileName: 'backdropFile', currentUrl: movie.backdropUrl || '' })}
    <div class="admin-form-field admin-form-field--full"><span>Thể loại</span><div class="admin-checks">${genreChecks(selectedIds)}</div></div>
    <div class="admin-checkbox-row admin-form-field--full"><label><input name="isTrending" type="checkbox" ${movie.isTrending ? 'checked' : ''}>Đang thịnh hành</label><label><input name="isTopRated" type="checkbox" ${movie.isTopRated ? 'checked' : ''}>Đánh giá cao</label></div>
  </div>`;
}

async function toMoviePayload(formData) {
  const [posterUrl, backdropUrl] = await Promise.all([
    resolveImageUrl(formData, { fileName: 'posterFile', urlName: 'posterUrl', category: 'movies' }),
    resolveImageUrl(formData, { fileName: 'backdropFile', urlName: 'backdropUrl', category: 'movies' })
  ]);
  return {
    title: formData.get('title').trim(),
    description: formData.get('description').trim(),
    posterUrl,
    backdropUrl,
    releaseDate: formData.get('releaseDate') || null,
    duration: Number(formData.get('duration')),
    rating: Number(formData.get('rating')),
    movieType: formData.get('movieType'),
    status: formData.get('status'),
    genreIds: formData.getAll('genreIds').map(Number),
    isTrending: formData.has('isTrending'),
    isTopRated: formData.has('isTopRated')
  };
}

function renderMovies(filter = '') {
  const body = document.querySelector('#movie-table-body');
  const query = filter.trim().toLocaleLowerCase('vi');
  const visibleMovies = movies.filter((movie) => movie.title?.toLocaleLowerCase('vi').includes(query));
  document.querySelector('#movie-count').textContent = `${visibleMovies.length} phim`;
  body.innerHTML = visibleMovies.length ? visibleMovies.map((movie) => `<tr>
    <td><div class="admin-movie-cell"><img src="${escapeHtml(movie.posterUrl || '')}" alt="" onerror="this.style.visibility='hidden'"><div><strong>${escapeHtml(movie.title)}</strong><small>#${movie.id} · ${(movie.genres || []).map((genre) => escapeHtml(genre.name)).join(', ') || 'Chưa phân loại'}</small></div></div></td>
    <td>${escapeHtml(movieTypeLabel(movie.movieType))}</td><td>${formatDate(movie.releaseDate)}</td><td>${statusBadge(movie.status)}</td><td>★ ${Number(movie.rating || 0).toFixed(1)}</td>
    <td class="admin-table__actions"><div class="admin-actions"><button class="admin-icon-button" type="button" data-edit-movie="${movie.id}">Sửa</button><button class="admin-icon-button admin-icon-button--danger" type="button" data-delete-movie="${movie.id}">Xóa</button></div></td>
  </tr>`).join('') : tableMessage(6, query ? 'Không tìm thấy phim phù hợp.' : 'Chưa có phim trong hệ thống.');
}

async function reloadMovies() {
  const body = document.querySelector('#movie-table-body');
  body.innerHTML = tableMessage(6, 'Đang tải danh sách phim…', 'loading');
  try {
    [movies, genres] = await Promise.all([adminApi.getMovies(), adminApi.getGenres()]);
    renderMovies(document.querySelector('#movie-search').value);
    window.dispatchEvent(new CustomEvent('admin:movies-updated', { detail: movies }));
  } catch (error) {
    body.innerHTML = tableMessage(6, error.message, 'error');
  }
}

function openMovieEditor(movie) {
  openAdminDialog({
    title: movie ? 'Chỉnh sửa phim' : 'Thêm phim mới',
    content: movieForm(movie),
    submitLabel: movie ? 'Lưu thay đổi' : 'Tạo phim',
    onSubmit: async (formData) => {
      const payload = await toMoviePayload(formData);
      if (movie) await adminApi.updateMovie(movie.id, payload);
      else await adminApi.createMovie(payload);
      showToast(movie ? 'Đã cập nhật phim.' : 'Đã thêm phim mới.');
      invalidateDashboard();
      await reloadMovies();
    }
  });
  bindImagePreviews(document.querySelector('#admin-dialog-content'));
}

export async function initMovies() {
  if (!initialized) {
    document.querySelector('#add-movie').addEventListener('click', () => openMovieEditor());
    document.querySelector('#movie-search').addEventListener('input', (event) => renderMovies(event.target.value));
    document.querySelector('#movie-table-body').addEventListener('click', async (event) => {
      const editButton = event.target.closest('[data-edit-movie]');
      const deleteButton = event.target.closest('[data-delete-movie]');
      if (editButton) openMovieEditor(movies.find((movie) => movie.id === Number(editButton.dataset.editMovie)));
      if (deleteButton) {
        const movie = movies.find((item) => item.id === Number(deleteButton.dataset.deleteMovie));
        if (!movie || !confirmAction(`Xóa phim “${movie.title}”? Thao tác này không thể hoàn tác.`)) return;
        try {
          await adminApi.deleteMovie(movie.id);
          showToast('Đã xóa phim.');
          invalidateDashboard();
          await reloadMovies();
        } catch (error) { showToast(error.message, 'error'); }
      }
    });
    initialized = true;
  }
  if (!movies.length) await reloadMovies();
}

export function getAdminMovies() {
  return movies;
}
