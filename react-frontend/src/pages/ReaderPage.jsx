import { useEffect, useState } from 'react';
import { apiClient } from '../api/client';
import EmptyState from '../components/EmptyState';

function ReaderPage() {
  const [readers, setReaders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState(null);
  const [error, setError] = useState('');

  const fetchReaders = () => {
    setLoading(true);
    return apiClient('/api/users/readers')
      .then(data => { setReaders(Array.isArray(data) ? data : []); setError(''); })
      .catch(err => setError(err.message || 'Không thể tải danh sách độc giả.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchReaders(); }, []);

  const toggleBorrowing = async reader => {
    setSavingId(reader.id);
    setError('');
    try {
      await apiClient(`/api/users/${reader.id}/borrowing-status`, {
        method: 'PUT', body: JSON.stringify({ enabled: !reader.enabled })
      });
      await fetchReaders();
    } catch (err) {
      setError(err.message || 'Không thể cập nhật quyền mượn.');
    } finally {
      setSavingId(null);
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Readers</h1>
          <p className="muted">Quản lý hồ sơ và quyền mượn sách của độc giả.</p>
        </div>
      </div>
      {loading && <p role="status">Đang tải độc giả…</p>}
      {error && <p className="error" role="alert">{error}</p>}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th><th>Username</th><th>Full name</th><th>Email</th>
              <th>Tài khoản</th><th>Quyền mượn</th><th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {readers.length === 0 ? <EmptyState colSpan={7} message="Chưa có độc giả" /> : readers.map(reader => (
              <tr key={reader.id}>
                <td>{reader.id}</td><td>{reader.username}</td><td>{reader.fullName}</td><td>{reader.email}</td>
                <td>{reader.keycloakId ? 'Đã liên kết' : 'Hồ sơ cũ, chưa liên kết'}</td>
                <td><span className={reader.enabled ? 'badge ok' : 'badge danger'}>
                  {reader.enabled ? 'Được mượn' : 'Tạm dừng'}
                </span></td>
                <td><button type="button" className="link" disabled={savingId !== null}
                  onClick={() => toggleBorrowing(reader)}>
                  {savingId === reader.id ? 'Đang lưu…' : reader.enabled ? 'Tạm dừng mượn' : 'Cho phép mượn'}
                </button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

export default ReaderPage;
