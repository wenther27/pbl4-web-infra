import { loadDashboard } from '../admin/dashboard.js';
import { initEpisodes } from '../admin/episodes.js';
import { initGenres } from '../admin/genres.js';
import { initMovies } from '../admin/movies.js';
import { initReviews } from '../admin/reviews.js';
import { initSecurity } from '../admin/security.js';
import { initUsers } from '../admin/users.js';
import { getAdminSession, renderAdminAccessDenied } from '../auth/admin-guard.js';
import { clearSession } from '../auth/session.js';
import { mountAdminLayout } from '../components/admin-layout.js';
import { showToast } from '../admin/shared.js';

const session = getAdminSession();

if (!session) {
  renderAdminAccessDenied();
} else {
  const routeLoaders = {
    dashboard: () => loadDashboard(),
    movies: () => initMovies(),
    genres: () => initGenres(),
    episodes: () => initEpisodes(),
    users: () => initUsers(session),
    reviews: () => initReviews(),
    security: () => initSecurity()
  };

  mountAdminLayout(session, (route) => routeLoaders[route]?.());
  window.addEventListener('admin:forbidden', (event) => {
    if (event.detail.status === 401) {
      clearSession();
      showToast('Phiên đăng nhập đã hết hạn. Bạn sẽ được chuyển về trang chủ.', 'error');
      setTimeout(() => { window.location.href = '../../index.html'; }, 1200);
    }
  });
}
