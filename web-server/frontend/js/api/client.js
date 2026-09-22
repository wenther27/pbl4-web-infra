import { APP_CONFIG } from '../config.js';

export async function request(path, options = {}) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), APP_CONFIG.requestTimeout);

  try {
    const response = await fetch(`${APP_CONFIG.apiBaseUrl}${path}`, {
      ...options,
      headers: { Accept: 'application/json', ...options.headers },
      signal: controller.signal
    });
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
    return await response.json();
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
  }
};
