import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

function ProtectedRoute({ children, roles }) {
  const { isLoggedIn, user } = useAuth();

  if (!isLoggedIn) {
    return <Navigate to="/login" replace />;
  }

  if (roles && !roles.includes(user?.role)) {
    return <Navigate to="/books" replace />;
  }

  return children;
}

export default ProtectedRoute;
