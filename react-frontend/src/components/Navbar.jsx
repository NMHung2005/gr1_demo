import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

function Navbar() {
  const { isLoggedIn, isLibrarian, isReader, user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <nav className="navbar">
      <NavLink className="brand" to={isLoggedIn ? '/books' : '/login'}>
        Library App
      </NavLink>

      <div className="nav-links">
        {!isLoggedIn ? (
          <>
            <NavLink to="/login">Login</NavLink>
            <NavLink to="/register">Register</NavLink>
          </>
        ) : null}

        {isLoggedIn ? <NavLink to="/books">Books</NavLink> : null}
        {isLibrarian ? <NavLink to="/dashboard">Dashboard</NavLink> : null}
        {isReader ? <NavLink to="/my-loans">My Loans</NavLink> : null}
        {isLibrarian ? <NavLink to="/readers">Readers</NavLink> : null}
        {isLibrarian ? <NavLink to="/loans">Loans</NavLink> : null}
      </div>

      {isLoggedIn ? (
        <div className="nav-user">
          <span>
            {user?.fullName || user?.username} ({user?.role})
          </span>
          <button type="button" className="outline-button" onClick={handleLogout}>
            Logout
          </button>
        </div>
      ) : null}
    </nav>
  );
}

export default Navbar;
