import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

function Navbar() {
  const { isLoggedIn, isLibrarian, isReader, user, logout } = useAuth();
  const [error, setError] = useState('');
  const [loggingOut, setLoggingOut] = useState(false);

  const handleLogout = async () => {
    setError('');
    setLoggingOut(true);
    try {
      await logout();
    } catch {
      setError('Không thể đăng xuất. Vui lòng thử lại.');
      setLoggingOut(false);
    }
  };

  return (
    <header className="site-header">
      <nav className="navbar" aria-label="Điều hướng chính">
        <NavLink className="brand" to={isLoggedIn ? '/books' : '/login'}>
          <span className="brand-mark" aria-hidden="true">L</span> Library App
        </NavLink>
        <div className="nav-links">
          {!isLoggedIn && <NavLink to="/login">Đăng nhập</NavLink>}
          {!isLoggedIn && <NavLink to="/register">Đăng ký</NavLink>}
          {isLoggedIn && <NavLink to="/books">Sách</NavLink>}
          {isLibrarian && <NavLink to="/dashboard">Tổng quan</NavLink>}
          {isReader && <NavLink to="/my-loans">Phiếu mượn của tôi</NavLink>}
          {isLibrarian && <NavLink to="/readers">Độc giả</NavLink>}
          {isLibrarian && <NavLink to="/loans">Mượn trả</NavLink>}
        </div>
        {isLoggedIn && (
          <div className="nav-user">
            <div className="user-identity">
              <strong>{user?.fullName || user?.username}</strong>
              <span>{isLibrarian ? 'Thủ thư' : isReader ? 'Độc giả' : 'Chưa được cấp quyền'}</span>
            </div>
            <button type="button" className="outline-button" onClick={handleLogout} disabled={loggingOut}>
              {loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}
            </button>
          </div>
        )}
      </nav>
      {error && <p className="navbar-error error" role="alert">{error}</p>}
    </header>
  );
}

export default Navbar;
