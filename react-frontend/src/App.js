import './App.css';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';
import PublicRoute from './components/PublicRoute';
import BookPage from './pages/BookPage';
import DashboardPage from './pages/DashboardPage';
import LoanPage from './pages/LoanPage';
import LoginPage from './pages/LoginPage';
import MyLoansPage from './pages/MyLoansPage';
import ReaderPage from './pages/ReaderPage';
import RegisterPage from './pages/RegisterPage';

function App() {
  return (
    <BrowserRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
      <AuthProvider>
        <div className="App">
          <Navbar />
          <main className="page">
            <Routes>
              <Route path="/" element={<Navigate to="/books" replace />} />
              <Route
                path="/login"
                element={
                  <PublicRoute>
                    <LoginPage />
                  </PublicRoute>
                }
              />
              <Route
                path="/register"
                element={
                  <PublicRoute>
                    <RegisterPage />
                  </PublicRoute>
                }
              />
              <Route
                path="/books"
                element={
                  <ProtectedRoute>
                    <BookPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/dashboard"
                element={
                  <ProtectedRoute roles={['LIBRARIAN']}>
                    <DashboardPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/readers"
                element={
                  <ProtectedRoute roles={['LIBRARIAN']}>
                    <ReaderPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/loans"
                element={
                  <ProtectedRoute roles={['LIBRARIAN']}>
                    <LoanPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/my-loans"
                element={
                  <ProtectedRoute roles={['READER']}>
                    <MyLoansPage />
                  </ProtectedRoute>
                }
              />
            </Routes>
          </main>
        </div>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
