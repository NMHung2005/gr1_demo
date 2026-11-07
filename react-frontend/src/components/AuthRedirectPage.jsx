import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { safeReturnPath } from '../auth/returnPath';

// Credentials are entered in Keycloak's form; React only starts the OIDC flow.
function AuthRedirectPage({ registration = false }) {
  const { login, register } = useAuth();
  const location = useLocation();
  const started = useRef(false);
  const [error, setError] = useState('');
  const title = registration ? 'Đăng ký' : 'Đăng nhập';
  const returnTo = safeReturnPath(location.state?.from);

  const redirect = useCallback(async () => {
    setError('');
    try {
      await (registration ? register(returnTo) : login(returnTo));
    } catch {
      setError(registration
        ? 'Không thể mở trang đăng ký. Vui lòng thử lại.'
        : 'Không thể mở trang đăng nhập. Vui lòng thử lại.');
    }
  }, [login, register, registration, returnTo]);

  useEffect(() => {
    if (!started.current) {
      started.current = true;
      redirect();
    }
  }, [redirect]);

  return (
    <section className="auth-page">
      <div className="simple-form auth-form">
        <h1>{title}</h1>
        {error ? (
          <>
            <p className="error" role="alert">{error}</p>
            <button className="auth-submit" type="button" onClick={redirect}>Thử lại</button>
          </>
        ) : (
          <p className="muted" role="status">
            {registration ? 'Đang mở trang tạo tài khoản…' : 'Đang mở trang nhập tài khoản và mật khẩu…'}
          </p>
        )}
        <p className="auth-footer">
          {registration ? 'Đã có tài khoản? ' : 'Chưa có tài khoản? '}
          <Link to={registration ? '/login' : '/register'} state={{ from: returnTo }}>
            {registration ? 'Đăng nhập' : 'Đăng ký'}
          </Link>
        </p>
      </div>
    </section>
  );
}

export default AuthRedirectPage;
