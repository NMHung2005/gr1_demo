import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { safeReturnPath } from '../auth/returnPath';

function PublicRoute({ children }) {
  const { isLoggedIn } = useAuth();
  const location = useLocation();

  if (isLoggedIn) {
    return <Navigate to={safeReturnPath(location.state?.from)} replace />;
  }

  return children;
}

export default PublicRoute;
