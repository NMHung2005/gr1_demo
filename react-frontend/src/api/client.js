const AUTH_KEY = 'libraryAuth';
export const AUTH_EXPIRED_EVENT = 'library-auth-expired';

export function getStoredAuth() {
  const raw = localStorage.getItem(AUTH_KEY);
  if (!raw) {
    return null; //
  }

  try {
    return JSON.parse(raw);
  } catch (error) {
    localStorage.removeItem(AUTH_KEY);
    return null;
  }
}

export function saveStoredAuth(authData) {
  localStorage.setItem(AUTH_KEY, JSON.stringify(authData));
}

export function clearStoredAuth() {
  localStorage.removeItem(AUTH_KEY);
}

async function readResponseData(response) {
  const contentType = response.headers.get('content-type') || '';
  return contentType.includes('application/json') ? response.json() : null;
}

async function refreshAccessToken(auth) {
  const response = await fetch('/api/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: auth.refreshToken })
  });
  const data = await readResponseData(response);

  if (!response.ok || !data?.accessToken) {
    throw new Error('Refresh token is invalid');
  }

  saveStoredAuth({
    ...auth,
    ...data,
    refreshToken: data.refreshToken || auth.refreshToken
  });
}

function expireSession() {
  clearStoredAuth();
  window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
}

async function sendRequest(path, options, canRefresh) {
  const auth = getStoredAuth();
  const headers = {
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...(auth?.accessToken ? { Authorization: `Bearer ${auth.accessToken}` } : {}),
    ...(options.headers || {})
  };

  const response = await fetch(path, {
    ...options,
    headers
  });

  const data = await readResponseData(response);

  const isProtectedRequest = !path.startsWith('/api/auth/');
  if (response.status === 401 && isProtectedRequest && auth?.refreshToken) {
    if (canRefresh) {
      try {
        await refreshAccessToken(auth);
        return sendRequest(path, options, false);
      } catch (error) {
        expireSession();
        throw new Error('Session expired. Please log in again.');
      }
    }

    expireSession();
    throw new Error('Session expired. Please log in again.');
  }

  if (!response.ok) {
    throw new Error(data?.message || data?.error || `HTTP ${response.status}`);
  }

  return data;
}

export async function apiClient(path, options = {}) {
  return sendRequest(path, options, true);
}
