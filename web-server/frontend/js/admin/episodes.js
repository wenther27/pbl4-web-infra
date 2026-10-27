import { adminApi } from '../api/admin.js';
import { confirmAction, escapeHtml, openAdminDialog, showToast, tableMessage } from './shared.js';
import { bindImagePreviews, imageUploadField, resolveImageUrl } from './image-upload.js';
import { bindVideoPreview, resolveVideoUrl, videoUploadField } from './video-upload.js';

let movies = [];
let episodes = [];
let initialized = false;

function episodeForm(episode = {}) {
  return `<div class="admin-form-grid">
    <label class="admin-form-field admin-form-field--full"><span>Tiêu đề tập</span><input name="title" maxlength="255" value="${escapeHtml(episode.title || '')}" placeholder="Ví dụ: Khởi đầu"></label>
    <label class="admin-form-field"><span>Số tập *</span><input name="episodeNumber" type="number" required min="1" value="${episode.episodeNumber || ''}"></label>
    <label class="admin-form-field"><span>Thời lượng (phút)</span><input name="duration" type="number" min="1" value="${episode.duration || ''}"></label>
    ${videoUploadField({ currentUrl: episode.videoUrl || '' })}
    ${imageUploadField({ label: 'Ảnh thu nhỏ', urlName: 'thumbnailUrl', fileName: 'thumbnailFile', currentUrl: episode.thumbnailUrl || '', full: true })}
  </div>`;
}

async function toPayload(data) {
  const videoUrl = await resolveVideoUrl(data);
  const thumbnailUrl = await resolveImageUrl(data, { fileName: 'thumbnailFile', urlName: 'thumbnailUrl', category: 'episodes' });
  return { title: data.get('title').trim(), episodeNumber: Number(data.get('episodeNumber')), duration: data.get('duration') ? Number(data.get('duration')) : null, videoUrl, thumbnailUrl };
}

function renderEpisodes() {
  const body = document.querySelector('#episode-table-body');
  body.innerHTML = episodes.length ? episodes.map((episode) => `<tr><td><span class="admin-badge">Tập ${episode.episodeNumber}</span></td><td>${escapeHtml(episode.title || 'Chưa đặt tiêu đề')}</td><td>${episode.duration ? `${episode.duration} phút` : '—'}</td><td>${episode.videoUrl ? `<a class="text-link" href="${escapeHtml(episode.videoUrl)}" target="_blank" rel="noreferrer">Mở video ↗</a>` : '—'}</td><td class="admin-table__actions"><div class="admin-actions"><button class="admin-icon-button" data-edit-episode="${episode.id}" type="button">Sửa</button><button class="admin-icon-button admin-icon-button--danger" data-delete-episode="${episode.id}" type="button">Xóa</button></div></td></tr>`).join('') : tableMessage(5, 'Phim này chưa có tập nào.');
}

async function loadEpisodes(movieId) {
  const body = document.querySelector('#episode-table-body');
  body.innerHTML = tableMessage(5, 'Đang tải danh sách tập…', 'loading');
  try {
    episodes = await adminApi.getEpisodes(movieId);
    renderEpisodes();
  } catch (error) { body.innerHTML = tableMessage(5, error.message, 'error'); }
}

function openEpisodeEditor(episode) {
  const movieId = document.querySelector('#episode-movie-select').value;
  openAdminDialog({
    title: episode ? `Chỉnh sửa tập ${episode.episodeNumber}` : 'Thêm tập phim',
    content: episodeForm(episode),
    submitLabel: episode ? 'Lưu thay đổi' : 'Tạo tập phim',
    onSubmit: async (data) => {
      const payload = await toPayload(data);
      if (episode) await adminApi.updateEpisode(episode.id, payload);
      else await adminApi.createEpisode(movieId, payload);
      showToast(episode ? 'Đã cập nhật tập phim.' : 'Đã thêm tập phim.');
      await loadEpisodes(movieId);
    }
  });
  const dialogContent = document.querySelector('#admin-dialog-content');
  bindVideoPreview(dialogContent);
  bindImagePreviews(dialogContent);
}

function setMovies(nextMovies) {
  movies = nextMovies;
  const select = document.querySelector('#episode-movie-select');
  const selected = select.value;
  select.innerHTML = `<option value="">-- Chọn phim --</option>${movies.map((movie) => `<option value="${movie.id}">${escapeHtml(movie.title)}</option>`).join('')}`;
  if (movies.some((movie) => String(movie.id) === selected)) select.value = selected;
}

export async function initEpisodes() {
  if (!initialized) {
    const select = document.querySelector('#episode-movie-select');
    const addButton = document.querySelector('#add-episode');
    select.addEventListener('change', () => {
      addButton.disabled = !select.value;
      if (select.value) loadEpisodes(select.value);
      else document.querySelector('#episode-table-body').innerHTML = tableMessage(5, 'Hãy chọn một phim để xem danh sách tập.');
    });
    addButton.addEventListener('click', () => openEpisodeEditor());
    document.querySelector('#episode-table-body').addEventListener('click', async (event) => {
      const edit = event.target.closest('[data-edit-episode]');
      const remove = event.target.closest('[data-delete-episode]');
      if (edit) openEpisodeEditor(episodes.find((episode) => episode.id === Number(edit.dataset.editEpisode)));
      if (remove) {
        const episode = episodes.find((item) => item.id === Number(remove.dataset.deleteEpisode));
        if (!episode || !confirmAction(`Xóa tập ${episode.episodeNumber}?`)) return;
        try { await adminApi.deleteEpisode(episode.id); showToast('Đã xóa tập phim.'); await loadEpisodes(select.value); } catch (error) { showToast(error.message, 'error'); }
      }
    });
    window.addEventListener('admin:movies-updated', (event) => setMovies(event.detail));
    initialized = true;
  }
  if (!movies.length) {
    try { setMovies(await adminApi.getMovies()); } catch (error) { showToast(error.message, 'error'); }
  }
}
