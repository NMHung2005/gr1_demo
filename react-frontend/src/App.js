import './App.css';
import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';
import PublicRoute from './components/PublicRoute';
import BookPage from './pages/BookPage';
import LoginPage from './pages/LoginPage';
import ReaderPage from './pages/ReaderPage';
import RegisterPage from './pages/RegisterPage';

function HomeRoute() {
  const { isLoggedIn } = useAuth();
  return <Navigate to={isLoggedIn ? '/books' : '/login'} replace />;
}

function UpdatingPage({ title }) {
  return (
    <section className="simple-form status-page">
      <span className="section-label">LIBRARY APP</span>
      <h1>{title}</h1>
      <p>Tính năng này đang được cập nhật. Bạn vẫn có thể tra cứu sách trong thư viện.</p>
      <Link className="button-link" to="/books">Xem danh mục sách</Link>
    </section>
  );
}

function NotFoundPage() {
  return (
    <section className="simple-form status-page">
      <h1>Không tìm thấy trang</h1>
      <p>Đường dẫn bạn mở không tồn tại.</p>
      <Link className="button-link" to="/">Về trang chủ</Link>
    </section>
  );
}

function App() {
  return (
    <BrowserRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
      <AuthProvider>
        <div className="App">
          <Navbar />
          <main className="page" id="main-content">
            <Routes>
              <Route path="/" element={<HomeRoute />} />
              <Route path="/login" element={<PublicRoute><LoginPage /></PublicRoute>} />
              <Route path="/register" element={<PublicRoute><RegisterPage /></PublicRoute>} />
              <Route path="/books" element={
                <ProtectedRoute roles={['READER', 'LIBRARIAN']}><BookPage readOnly /></ProtectedRoute>
              } />
              <Route path="/readers" element={
                <ProtectedRoute roles={['LIBRARIAN']}><ReaderPage readOnly /></ProtectedRoute>
              } />
              <Route path="/dashboard" element={
                <ProtectedRoute roles={['LIBRARIAN']}><UpdatingPage title="Tổng quan thư viện" /></ProtectedRoute>
              } />
              <Route path="/loans" element={
                <ProtectedRoute roles={['LIBRARIAN']}><UpdatingPage title="Quản lý mượn trả" /></ProtectedRoute>
              } />
              <Route path="/my-loans" element={
                <ProtectedRoute roles={['READER']}><UpdatingPage title="Phiếu mượn của tôi" /></ProtectedRoute>
              } />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </main>
        </div>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
