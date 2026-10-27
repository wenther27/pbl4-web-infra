import { getAuthorizationHeader } from '../auth/session.js';
import { request } from './client.js';

export const reviewApi = {
  getByMovie(movieId) {
    return request(`/movies/${encodeURIComponent(movieId)}/reviews`);
  },

  create(movieId, review) {
    return request(`/movies/${encodeURIComponent(movieId)}/reviews`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...getAuthorizationHeader()
      },
      body: JSON.stringify(review)
    });
  }
};
