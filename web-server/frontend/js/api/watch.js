import { request } from './client.js';
import { getAuthorizationHeader, getSession } from '../auth/session.js';

export const watchApi = {
  isAuthenticated() {
    return Boolean(getSession());
  },

  getHistory() {
    return request('/history', { headers: getAuthorizationHeader() });
  },

  saveProgress({ movieId, episodeId, lastPosition }) {
    return request('/history', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...getAuthorizationHeader()
      },
      body: JSON.stringify({ movieId, episodeId, lastPosition })
    });
  }
};
