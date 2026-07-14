import { useEffect, useState } from 'react';
import { apiClient } from '../api/client';
import EmptyState from '../components/EmptyState';
import { formatDate, formatMoney } from '../utils/formatters';

function LoanPage() {
  const [loans, setLoans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchLoans = () => {
    setLoading(true);
    return apiClient('/api/loans')
      .then((data) => {
        setLoans(Array.isArray(data) ? data : []);
        setError('');
      })
      .catch((err) => setError(err.message || 'Load loans failed'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchLoans();
  }, []);

  const handleReturn = (loan) => {
    setError('');
    apiClient(`/api/loans/${loan.id}/return`, { method: 'PUT' })
      .then(() => fetchLoans())
      .catch((err) => setError(err.message || 'Return failed'));
  };

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Loans</h1>
          <p className="muted">View all borrowing records and return books.</p>
        </div>
      </div>

      {loading ? <p>Loading loans...</p> : null}
      {error ? <p className="error">Error: {error}</p> : null}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Reader</th>
              <th>Book</th>
              <th>Borrow date</th>
              <th>Due date</th>
              <th>Return date</th>
              <th>Status</th>
              <th>Fine</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {loans.length === 0 ? (
              <EmptyState colSpan="9" message="No loans" />
            ) : (
              loans.map((loan) => (
                <tr key={loan.id}>
                  <td>{loan.id}</td>
                  <td>{loan.userFullName || loan.username}</td>
                  <td>{loan.bookTitle}</td>
                  <td>{formatDate(loan.borrowDate)}</td>
                  <td>{formatDate(loan.dueDate)}</td>
                  <td>{formatDate(loan.returnDate)}</td>
                  <td>
                    <span className={`badge ${loan.status === 'RETURNED' ? 'ok' : loan.status === 'OVERDUE' ? 'danger' : ''}`}>
                      {loan.status}
                    </span>
                  </td>
                  <td>{formatMoney(loan.fineAmount)}</td>
                  <td>
                    {loan.status !== 'RETURNED' ? (
                      <button className="link" type="button" onClick={() => handleReturn(loan)}>
                        Return
                      </button>
                    ) : (
                      <span className="muted">Done</span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}

export default LoanPage;
