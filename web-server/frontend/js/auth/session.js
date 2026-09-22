const SESSION_KEY = 'cinehubSession';

export function getSession() {
  try {
    const session = JSON.parse(localStorage.getItem(SESSION_KEY));
    if (!session?.token || !session?.username) return null;
    return session;
  } catch {
    return null;
  }
}

export function saveSession(authResponse) {
  const session = {
    token: authResponse.token,
    username: authResponse.username,
    role: authResponse.role
  };
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  return session;
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY);
}

export function getAuthorizationHeader() {
  const session = getSession();
  return session ? { Authorization: `Bearer ${session.token}` } : {};
}
