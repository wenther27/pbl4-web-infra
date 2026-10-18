import { adminApi } from '../api/admin.js';
import { escapeHtml, movieTypeLabel, statusBadge, tableMessage } from './shared.js';

let loaded = false;

function statCard(label, value, hint, icon) {
  return `<article class="admin-stat-card"><div class="admin-stat-card__top"><span>${label}</span><span class="admin-stat-card__icon">${icon}</span></div><strong>${value}</strong><small>${hint}</small></article>`;
}

export async function loadDashboard(force = false) {
  if (loaded && !force) return;
  const stats = document.querySelector('#admin-stats');
  const movieBody = document.querySelector('#dashboard-movies');
  stats.innerHTML = statCard('Đang tải dữ liệu', '…', 'Vui lòng chờ', '↻');
  movieBody.innerHTML = tableMessage(4, 'Đang tải dữ liệu…', 'loading');

  const [moviesResult, genresResult, usersResult] = await Promise.allSettled([
    adminApi.getMovies(),
    adminApi.getGenres(),
    adminApi.getUsers()
  ]);
  const movies = moviesResult.status === 'fulfilled' ? moviesResult.value : [];
  const genres = genresResult.status === 'fulfilled' ? genresResult.value : [];
  const users = usersResult.status === 'fulfilled' ? usersResult.value : [];
  const admins = users.filter((user) => user.role === 'ADMIN').length;

  stats.innerHTML = [
    statCard('Tổng số phim', movies.length, 'Nội dung trong hệ thống', '▤'),
    statCard('Thể loại', genres.length, 'Nhóm nội dung đang dùng', '◇'),
    statCard('Người dùng', users.length, 'Tài khoản đã đăng ký', '♙'),
    statCard('Quản trị viên', admins, 'Tài khoản có quyền ADMIN', '⌾')
  ].join('');

  movieBody.innerHTML = movies.length ? [...movies].reverse().slice(0, 6).map((movie) => `<tr>
    <td><div class="admin-movie-cell"><img src="${escapeHtml(movie.posterUrl || '')}" alt="" onerror="this.style.visibility='hidden'"><div><strong>${escapeHtml(movie.title)}</strong><small>#${movie.id}</small></div></div></td>
    <td>${escapeHtml(movieTypeLabel(movie.movieType))}</td><td>${statusBadge(movie.status)}</td><td>★ ${Number(movie.rating || 0).toFixed(1)}</td>
  </tr>`).join('') : tableMessage(4, 'Chưa có phim trong hệ thống.');

  if ([moviesResult, genresResult, usersResult].some((result) => result.status === 'rejected')) {
    stats.insertAdjacentHTML('afterend', '<p class="admin-error">Một phần dữ liệu tổng quan chưa tải được. Hãy kiểm tra backend và phiên đăng nhập.</p>');
  }
  loaded = true;
}

export function invalidateDashboard() {
  loaded = false;
}
