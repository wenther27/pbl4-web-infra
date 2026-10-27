import { adminApi } from '../api/admin.js';
import { escapeHtml, showToast } from './shared.js';

const MAX_IMAGE_SIZE = 5 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']);

export function imageUploadField({ label, urlName, fileName, currentUrl = '', full = false }) {
  const fieldClass = full ? 'admin-form-field admin-form-field--full' : 'admin-form-field';
  return `<div class="${fieldClass} admin-image-field">
    <span>${escapeHtml(label)}</span>
    <div class="admin-image-picker">
      <div class="admin-image-preview" data-image-preview="${escapeHtml(fileName)}">
        ${currentUrl ? `<img src="${escapeHtml(currentUrl)}" alt="Ảnh hiện tại">` : '<span>Chưa chọn ảnh</span>'}
      </div>
      <div class="admin-image-picker__controls">
        <label class="admin-file-button">Chọn ảnh từ máy<input name="${escapeHtml(fileName)}" data-image-file="${escapeHtml(fileName)}" type="file" accept="image/jpeg,image/png,image/webp,image/gif"></label>
        <small>JPG, PNG, WebP hoặc GIF · tối đa 5 MB</small>
      </div>
    </div>
    <label class="admin-image-url"><span>Hoặc nhập URL</span><input name="${escapeHtml(urlName)}" type="url" value="${escapeHtml(currentUrl)}" placeholder="https://..."></label>
  </div>`;
}

export function bindImagePreviews(scope = document) {
  scope.querySelectorAll('[data-image-file]').forEach((input) => {
    input.addEventListener('change', () => {
      const preview = scope.querySelector(`[data-image-preview="${input.dataset.imageFile}"]`);
      const file = input.files?.[0];
      if (!preview || !file) return;
      try {
        validateImage(file);
      } catch (error) {
        input.value = '';
        showToast(error.message, 'error');
        return;
      }
      const temporaryUrl = URL.createObjectURL(file);
      preview.innerHTML = `<img src="${temporaryUrl}" alt="Ảnh đã chọn">`;
      preview.querySelector('img').addEventListener('load', () => URL.revokeObjectURL(temporaryUrl), { once: true });
    });
  });
}

export async function resolveImageUrl(formData, { fileName, urlName, category }) {
  const file = formData.get(fileName);
  if (file instanceof File && file.size > 0) {
    validateImage(file);
    const uploaded = await adminApi.uploadImage(file, category);
    return uploaded.url;
  }
  return String(formData.get(urlName) || '').trim();
}

function validateImage(file) {
  if (!ALLOWED_IMAGE_TYPES.has(file.type)) {
    throw new Error('Chỉ hỗ trợ ảnh JPG, PNG, WebP hoặc GIF.');
  }
  if (file.size > MAX_IMAGE_SIZE) {
    throw new Error('Ảnh không được lớn hơn 5 MB.');
  }
}
