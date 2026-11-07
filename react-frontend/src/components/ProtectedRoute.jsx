import { Link, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

function ProtectedRoute({ children, roles }) {
  const { isLoggedIn, user } = useAuth();
  const location = useLocation();

  if (!isLoggedIn) {
    const from = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to="/login" state={{ from }} replace />;
  }

  if (roles && !roles.some(role => user?.roles?.includes(role))) {
    return (
      <section className="simple-form status-page">
        <h1>Bạn không có quyền truy cập</h1>
        <p>Vai trò của tài khoản hiện tại không cho phép mở trang này.</p>
        <Link className="button-link" to="/">Về trang chủ</Link>
      </section>
    );
  }

  return children;
}

export default ProtectedRoute;
