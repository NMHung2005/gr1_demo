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

  const value = useMemo(() => ({
    auth: user,
    user,
    isLoggedIn: Boolean(user),
    isLibrarian: Boolean(user?.roles.includes('LIBRARIAN')),
    isReader: Boolean(user?.roles.includes('READER')),
    login,
    register,
    logout
  }), [user, login, register, logout]);

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
