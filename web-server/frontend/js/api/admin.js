import { getAuthorizationHeader } from '../auth/session.js';
import { request } from './client.js';

async function adminRequest(path, options = {}) {
  const isFormData = options.body instanceof FormData;
  try {
    return await request(path, {
      ...options,
      headers: {
        ...getAuthorizationHeader(),
        ...(options.body && !isFormData ? { 'Content-Type': 'application/json' } : {}),
        ...options.headers
      }
    });
  } catch (error) {
    if (error.status === 401 || error.status === 403) {
      window.dispatchEvent(new CustomEvent('admin:forbidden', { detail: { status: error.status } }));
    }
    throw error;
  }
}

function jsonBody(method, body) {
  return { method, body: JSON.stringify(body) };
}

export const adminApi = {
  uploadImage(file, category = 'movies') {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('category', category);
    return adminRequest('/uploads/images', { method: 'POST', body: formData });
  },

  uploadVideo(file) {
    const formData = new FormData();
    formData.append('file', file);
    return adminRequest('/uploads/videos', {
      method: 'POST',
      body: formData,
      timeout: 10 * 60 * 1000
    });
  },

  getMovies: () => adminRequest('/movies/all'),
  createMovie: (movie) => adminRequest('/movies', jsonBody('POST', movie)),
  updateMovie: (id, movie) => adminRequest(`/movies/${id}`, jsonBody('PUT', movie)),
  deleteMovie: (id) => adminRequest(`/movies/${id}`, { method: 'DELETE' }),

  getGenres: () => adminRequest('/genres'),
  createGenre: (genre) => adminRequest('/genres', jsonBody('POST', genre)),

  getEpisodes: (movieId) => adminRequest(`/movies/${movieId}/episodes`),
  createEpisode: (movieId, episode) => adminRequest(`/movies/${movieId}/episodes`, jsonBody('POST', episode)),
  updateEpisode: (episodeId, episode) => adminRequest(`/episodes/${episodeId}`, jsonBody('PUT', episode)),
  deleteEpisode: (episodeId) => adminRequest(`/episodes/${episodeId}`, { method: 'DELETE' }),

  getUsers: () => adminRequest('/users'),
  deleteUser: (id) => adminRequest(`/users/${id}`, { method: 'DELETE' }),

  getReviews: (movieId) => adminRequest(`/movies/${movieId}/reviews`),
  deleteReview: (reviewId) => adminRequest(`/reviews/${reviewId}`, { method: 'DELETE' }),

  // Bao mat: IP bi canh bao (tu Elasticsearch) va chan / go chan IP tai Load Balancer
  getSuspiciousIps: (minutes = 60) => adminRequest(`/admin/security/suspicious-ips?minutes=${encodeURIComponent(minutes)}`),
  getBlockedIps: () => adminRequest('/admin/security/blocked-ips'),
  blockIp: (body) => adminRequest('/admin/security/blocked-ips', jsonBody('POST', body)),
  unblockIp: (ip) => adminRequest(`/admin/security/blocked-ips/${encodeURIComponent(ip)}`, { method: 'DELETE' })
};
