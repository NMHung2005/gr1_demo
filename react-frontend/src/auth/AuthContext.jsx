import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState
} from 'react';

import keycloak from './keycloak';
import { safeReturnPath } from './returnPath';
import { apiClient } from '../api/client';

const AuthContext = createContext(null);

function readUser() {
  if (!keycloak.authenticated || !keycloak.tokenParsed) {
    return null;
  }

  const claims = keycloak.tokenParsed;
  const roles = claims.realm_access?.roles || [];

  const role = roles.includes('LIBRARIAN')
    ? 'LIBRARIAN'
    : roles.includes('READER')
      ? 'READER'
      : null;

  return {
    subject: claims.sub,
    username: claims.preferred_username,
    fullName: claims.name || claims.preferred_username,
    email: claims.email,
    roles,
    role
  };
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readUser);
  const [profile, setProfile] = useState(null);
  const [profileLoading, setProfileLoading] = useState(Boolean(user));
  const [profileError, setProfileError] = useState('');
  const [profileAttempt, setProfileAttempt] = useState(0);
  const subject = user?.subject;

  useEffect(() => {
    if (!subject) {
      setProfile(null);
      setProfileLoading(false);
      setProfileError('');
      return;
    }
    let active = true;
    setProfileLoading(true);
    setProfileError('');
    apiClient('/api/auth/me')
      .then(data => {
        if (!data?.id || data.subject !== subject) throw new Error('Invalid profile');
        if (active) setProfile(data);
      })
      .catch(() => {
        if (active) setProfileError('Không thể tải hồ sơ. Vui lòng thử lại.');
      })
      .finally(() => { if (active) setProfileLoading(false); });
    return () => { active = false; };
  }, [subject, profileAttempt]);

  const retryProfile = useCallback(() => setProfileAttempt(value => value + 1), []);

  useEffect(() => {
    const syncUser = () => setUser(readUser());

    keycloak.onAuthSuccess = syncUser;
    keycloak.onAuthRefreshSuccess = syncUser;
    keycloak.onAuthLogout = syncUser;

    keycloak.onAuthRefreshError = () => {
      keycloak.clearToken();
      syncUser();
    };

    keycloak.onTokenExpired = () => {
      keycloak.updateToken(30).catch(() => {
        keycloak.clearToken();
        syncUser();
      });
    };

    return () => {
      keycloak.onAuthSuccess = undefined;
      keycloak.onAuthRefreshSuccess = undefined;
      keycloak.onAuthLogout = undefined;
      keycloak.onAuthRefreshError = undefined;
      keycloak.onTokenExpired = undefined;
    };
  }, []);

  const login = useCallback((returnTo = '/books') => {
    return keycloak.login({
      redirectUri: `${window.location.origin}${safeReturnPath(returnTo)}`
    });
  }, []);

  const register = useCallback((returnTo = '/books') => {
    return keycloak.register({
      redirectUri: `${window.location.origin}${safeReturnPath(returnTo)}`
    });
  }, []);

  const logout = useCallback(() => {
    return keycloak.logout({
      redirectUri: `${window.location.origin}/login`
    });
  }, []);

  const libraryUser = useMemo(() => user ? {
    ...user,
    id: profile?.subject === user.subject ? profile.id : undefined,
    enabled: profile?.subject === user.subject ? profile.enabled : true
  } : null, [user, profile]);

  const value = useMemo(() => ({
    auth: libraryUser,
    user: libraryUser,
    isLoggedIn: Boolean(user),
    isLibrarian: Boolean(user?.roles.includes('LIBRARIAN')),
    isReader: user?.role === 'READER',
    login,
    register,
    logout,
    profileLoading,
    profileError,
    retryProfile
  }), [user, libraryUser, login, register, logout, profileLoading, profileError, retryProfile]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }

  return context;
}
