import { adminApi } from '../api/admin.js';
import { escapeHtml, showToast } from './shared.js';

const MAX_VIDEO_SIZE = 500 * 1024 * 1024;
const ALLOWED_VIDEO_TYPES = new Set(['video/mp4', 'video/webm', 'video/ogg']);

export function videoUploadField({ currentUrl = '' } = {}) {
  return `<div class="admin-form-field admin-form-field--full admin-video-field">
    <span>Video *</span>
    <div class="admin-video-picker">
      <div class="admin-video-preview" data-video-preview>
        ${currentUrl ? `<video src="${escapeHtml(currentUrl)}" controls preload="metadata"></video>` : '<span>Chưa chọn video</span>'}
      </div>
      <div class="admin-video-picker__controls">
        <label class="admin-file-button">Chọn video từ máy<input name="videoFile" data-video-file type="file" accept="video/mp4,video/webm,video/ogg"></label>
        <strong data-video-name>${currentUrl ? 'Đang sử dụng video hiện tại' : 'Chưa có file được chọn'}</strong>
        <small>MP4, WebM hoặc OGG · tối đa 500 MB</small>
      </div>
    </div>
    <label class="admin-image-url"><span>Hoặc nhập URL video</span><input name="videoUrl" type="url" value="${escapeHtml(currentUrl)}" placeholder="https://..."></label>
  </div>`;
}

export function bindVideoPreview(scope = document) {
  const input = scope.querySelector('[data-video-file]');
  const preview = scope.querySelector('[data-video-preview]');
  const name = scope.querySelector('[data-video-name]');
  if (!input || !preview || !name) return;

  let temporaryUrl;
  input.addEventListener('change', () => {
    if (temporaryUrl) URL.revokeObjectURL(temporaryUrl);
    const file = input.files?.[0];
    if (!file) return;
    try {
      validateVideo(file);
    } catch (error) {
      input.value = '';
      name.textContent = 'Chưa có file được chọn';
      showToast(error.message, 'error');
      return;
    }

    temporaryUrl = URL.createObjectURL(file);
    preview.innerHTML = '';
    const video = document.createElement('video');
    video.src = temporaryUrl;
    video.controls = true;
    video.preload = 'metadata';
    preview.append(video);
    name.textContent = `${file.name} · ${formatFileSize(file.size)}`;
  });

  scope.closest('dialog')?.addEventListener('close', () => {
    if (temporaryUrl) URL.revokeObjectURL(temporaryUrl);
  }, { once: true });
}

export async function resolveVideoUrl(formData) {
  const file = formData.get('videoFile');
  if (file instanceof File && file.size > 0) {
    validateVideo(file);
    const uploaded = await adminApi.uploadVideo(file);
    return uploaded.url;
  }

  const videoUrl = String(formData.get('videoUrl') || '').trim();
  if (!videoUrl) throw new Error('Vui lòng chọn video từ máy hoặc nhập URL video.');
  return videoUrl;
}

function validateVideo(file) {
  if (!ALLOWED_VIDEO_TYPES.has(file.type)) {
    throw new Error('Chỉ hỗ trợ video MP4, WebM hoặc OGG.');
  }
  if (file.size > MAX_VIDEO_SIZE) {
    throw new Error('Video không được lớn hơn 500 MB.');
  }
}

function formatFileSize(bytes) {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
