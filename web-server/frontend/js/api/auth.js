import { request } from './client.js';

function post(path, body) {
  return request(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
}

export const authApi = {
  login(credentials) {
    return post('/auth/login', credentials);
  },
  register(account) {
    return post('/auth/register', account);
  }
};
