import { APP_CONFIG } from '../config.js';
import { showServedBy, showServerDown } from '../components/server-badge.js';

export async function request(path, options = {}) {
  const controller = new AbortController();
  const { timeout = APP_CONFIG.requestTimeout, ...fetchOptions } = options;
  const timeoutId = setTimeout(() => controller.abort(), timeout);

  try {
    let response;
    try {
      response = await fetch(`${APP_CONFIG.apiBaseUrl}${path}`, {
        ...fetchOptions,
        headers: { Accept: 'application/json', ...fetchOptions.headers },
        signal: controller.signal
      });
    } catch (networkError) {
      showServerDown(APP_CONFIG.apiBaseUrl);
      throw networkError;
    }
    showServedBy(response.headers.get('X-Instance-Id'), APP_CONFIG.apiBaseUrl);
    if (!response.ok) {
      let message = `API trả về mã ${response.status}`;
      try {
        const errorBody = await response.json();
        if (typeof errorBody.details === 'string') message = errorBody.details;
        else if (errorBody.error) message = errorBody.error;
      } catch { /* phản hồi lỗi không phải JSON */ }
      const error = new Error(message);
      error.status = response.status;
      throw error;
    }
    if (response.status === 204) return null;
    const responseText = await response.text();
    if (!responseText) return null;
    try {
      return JSON.parse(responseText);
    } catch {
      return responseText;
    }
  } finally {
    clearTimeout(timeoutId);
  }
}

export const movieApi = {
  getMovies(params = {}) {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== '' && value !== undefined && value !== null) query.set(key, value);
    });
    return request(`/movies?${query.toString()}`);
  },
  getGenres() {
    return request('/genres');
  },
  getEpisodes(movieId) {
    return request(`/movies/${encodeURIComponent(movieId)}/episodes`);
  }
};
