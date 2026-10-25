import { adminApi } from '../api/admin.js';
import { invalidateDashboard } from './dashboard.js';
import { confirmAction, escapeHtml, formatDate, showToast, tableMessage } from './shared.js';

let users = [];
let currentUsername = '';
let initialized = false;

function renderUsers(filter = '') {
  const query = filter.trim().toLocaleLowerCase('vi');
  const visible = users.filter((user) => `${user.username} ${user.email}`.toLocaleLowerCase('vi').includes(query));
  document.querySelector('#user-count').textContent = `${visible.length} tài khoản`;
  document.querySelector('#user-table-body').innerHTML = visible.length ? visible.map((user) => `<tr>
    <td><div class="admin-user-cell"><span class="admin-user-avatar">${escapeHtml(user.username?.slice(0, 1).toUpperCase() || 'U')}</span><div><strong>${escapeHtml(user.username)}</strong><small>#${user.id}</small></div></div></td>
    <td>${escapeHtml(user.email)}</td><td><span class="admin-badge ${user.role === 'ADMIN' ? 'admin-badge--admin' : ''}">${escapeHtml(user.role)}</span></td><td>${formatDate(user.createAt)}</td>
    <td class="admin-table__actions"><button class="admin-icon-button admin-icon-button--danger" data-delete-user="${user.id}" type="button" ${user.username === currentUsername ? 'disabled title="Không thể xóa tài khoản đang đăng nhập"' : ''}>Xóa</button></td>
  </tr>`).join('') : tableMessage(5, query ? 'Không tìm thấy tài khoản phù hợp.' : 'Chưa có tài khoản.');
}

async function loadUsers() {
  const body = document.querySelector('#user-table-body');
  body.innerHTML = tableMessage(5, 'Đang tải người dùng…', 'loading');
  try { users = await adminApi.getUsers(); renderUsers(document.querySelector('#user-search').value); } catch (error) { body.innerHTML = tableMessage(5, error.message, 'error'); }
}

export async function initUsers(session) {
  currentUsername = session.username;
  if (!initialized) {
    document.querySelector('#user-search').addEventListener('input', (event) => renderUsers(event.target.value));
    document.querySelector('#user-table-body').addEventListener('click', async (event) => {
      const button = event.target.closest('[data-delete-user]');
      if (!button) return;
      const user = users.find((item) => item.id === Number(button.dataset.deleteUser));
      if (!user || user.username === currentUsername || !confirmAction(`Xóa tài khoản “${user.username}”?`)) return;
      try { await adminApi.deleteUser(user.id); showToast('Đã xóa tài khoản.'); invalidateDashboard(); await loadUsers(); } catch (error) { showToast(error.message, 'error'); }
    });
    initialized = true;
  }
  if (!users.length) await loadUsers();
}
