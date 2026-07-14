import { useEffect, useState } from 'react';
import EmptyState from '../components/EmptyState';
import { apiClient } from '../api/client';
import { formatMoney } from '../utils/formatters';

function DashboardPage() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    apiClient('/api/loans/stats')
      .then((data) => {
        setStats(data);
        setError('');
      })
      .catch((err) => setError(err.message || 'Load dashboard failed'))
      .finally(() => setLoading(false));
  }, []);

  const topBooks = stats?.topBorrowedBooks || [];

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Dashboard</h1>
          <p className="muted">Quick overview for the librarian.</p>
        </div>
      </div>

      {loading ? <p>Loading dashboard...</p> : null}
      {error ? <p className="error">Error: {error}</p> : null}

      {!loading && !error && stats ? (
        <>
          <div className="stats-grid">
            <div className="stat-box">
              <span>Active loans</span>
              <strong>{stats.activeLoans}</strong>
            </div>
            <div className="stat-box">
              <span>Borrowed</span>
              <strong>{stats.borrowedLoans}</strong>
            </div>
            <div className="stat-box danger">
              <span>Overdue</span>
              <strong>{stats.overdueLoans}</strong>
            </div>
            <div className="stat-box">
              <span>Returned</span>
              <strong>{stats.returnedLoans}</strong>
            </div>
            <div className="stat-box">
              <span>Total fine</span>
              <strong>{formatMoney(stats.totalFineAmount)}</strong>
            </div>
          </div>

          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Book ID</th>
                  <th>Book title</th>
                  <th>Borrow count</th>
                </tr>
              </thead>
              <tbody>
                {topBooks.length === 0 ? (
                  <EmptyState colSpan="3" message="No borrowed books yet" />
                ) : (
                  topBooks.map((book) => (
                    <tr key={book.bookId}>
                      <td>{book.bookId}</td>
                      <td>{book.bookTitle}</td>
                      <td>{book.borrowCount}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </>
      ) : null}
    </>
  );
}

export default DashboardPage;
