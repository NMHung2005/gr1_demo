import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

function PublicRoute({ children }) {
  const { isLoggedIn, isLibrarian } = useAuth();

  if (isLoggedIn) {
    return <Navigate to={isLibrarian ? '/dashboard' : '/books'} replace />;
  }

  return children;
}

export default PublicRoute;
