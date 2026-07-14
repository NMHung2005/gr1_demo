import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import {
  apiClient,
  AUTH_EXPIRED_EVENT,
  clearStoredAuth,
  getStoredAuth,
  saveStoredAuth
} from '../api/client';

const AuthContext = createContext(null);

function buildAuthData(response) {
  return {
    id: response.id,
    username: response.username,
    fullName: response.fullName,
    email: response.email,
    role: response.role,
    enabled: response.enabled,
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    tokenType: response.tokenType
  };
}

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(() => getStoredAuth());

  useEffect(() => {
    const handleAuthExpired = () => setAuth(null);
    window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);

    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
  }, []);

  const login = useCallback(async (username, password) => {
    const response = await apiClient('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password })
    });
    const nextAuth = buildAuthData(response);
    saveStoredAuth(nextAuth);
    setAuth(nextAuth);
    return nextAuth;
  }, []);

  const register = useCallback(async (form) => {
    const response = await apiClient('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(form)
    });
    const nextAuth = buildAuthData(response);
    saveStoredAuth(nextAuth);
    setAuth(nextAuth);
    return nextAuth;
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = auth?.refreshToken;
    if (refreshToken) {
      try {
        await apiClient('/api/auth/logout', {
          method: 'POST',
          body: JSON.stringify({ refreshToken })
        });
      } catch (error) {

      }
    }
    clearStoredAuth();
    setAuth(null);
  }, [auth?.refreshToken]);

  const value = useMemo(
    () => ({
      auth,
      user: auth,
      isLoggedIn: Boolean(auth?.accessToken),
      isLibrarian: auth?.role === 'LIBRARIAN',
      isReader: auth?.role === 'READER',
      login,
      register,
      logout
    }),
    [auth, login, logout, register]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return value;
}
