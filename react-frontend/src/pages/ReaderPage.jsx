import { useEffect, useState } from 'react';
import { apiClient } from '../api/client';
import EmptyState from '../components/EmptyState';

const emptyForm = {
  username: '',
  password: '',
  fullName: '',
  email: '',
  enabled: true
};

function ReaderPage() {
  const [readers, setReaders] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const fetchReaders = () => {
    setLoading(true);
    return apiClient('/api/users/readers')
      .then((data) => {
        setReaders(Array.isArray(data) ? data : []);
        setError('');
      })
      .catch((err) => setError(err.message || 'Load readers failed'))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchReaders();
  }, []);

  const resetForm = () => {
    setForm(emptyForm);
    setEditingId(null);
  };

  const handleChange = (event) => {
    const { name, value, type, checked } = event.target;
    setForm((prev) => ({ ...prev, [name]: type === 'checkbox' ? checked : value }));
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    setSaving(true);
    setError('');

    const payload = {
      username: form.username.trim(),
      password: form.password,
      fullName: form.fullName.trim(),
      email: form.email.trim(),
      enabled: form.enabled
    };

    if (editingId && !payload.password) {
      delete payload.password;
    }

    apiClient(editingId ? `/api/users/${editingId}` : '/api/users', {
      method: editingId ? 'PUT' : 'POST',
      body: JSON.stringify(payload)
    })
      .then(() => {
        resetForm();
        return fetchReaders();
      })
      .catch((err) => setError(err.message || 'Save reader failed'))
      .finally(() => setSaving(false));
  };

  const handleEdit = (reader) => {
    setEditingId(reader.id);
    setForm({
      username: reader.username || '',
      password: '',
      fullName: reader.fullName || '',
      email: reader.email || '',
      enabled: Boolean(reader.enabled)
    });
    setError('');
  };

  const handleDelete = (reader) => {
    if (!window.confirm(`Delete reader "${reader.username}"?`)) {
      return;
    }

    apiClient(`/api/users/${reader.id}`, { method: 'DELETE' })
      .then(() => fetchReaders())
      .catch((err) => setError(err.message || 'Delete reader failed'));
  };

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Readers</h1>
          <p className="muted">Create and update reader accounts.</p>
        </div>
      </div>

      <form className="simple-form grid-form" onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="fullName">Full name</label>
          <input id="fullName" name="fullName" value={form.fullName} onChange={handleChange} required />
        </div>
        <div className="field">
          <label htmlFor="email">Email</label>
          <input id="email" name="email" type="email" value={form.email} onChange={handleChange} required />
        </div>
        <div className="field">
          <label htmlFor="username">Username</label>
          <input id="username" name="username" value={form.username} onChange={handleChange} required />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            value={form.password}
            onChange={handleChange}
            required={!editingId}
            placeholder={editingId ? 'Leave blank to keep old password' : ''}
          />
        </div>
        <label className="check-field">
          <input name="enabled" type="checkbox" checked={form.enabled} onChange={handleChange} />
          Enabled
        </label>
        <div className="form-actions">
          <button type="submit" disabled={saving}>
            {saving ? 'Saving...' : editingId ? 'Update Reader' : 'Add Reader'}
          </button>
          {editingId ? (
            <button type="button" className="secondary-button" onClick={resetForm}>
              Cancel
            </button>
          ) : null}
        </div>
      </form>

      {loading ? <p>Loading...</p> : null}
      {error ? <p className="error">Error: {error}</p> : null}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Username</th>
              <th>Full name</th>
              <th>Email</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {readers.length === 0 ? (
              <EmptyState colSpan="6" message="No readers" />
            ) : (
              readers.map((reader) => (
                <tr key={reader.id}>
                  <td>{reader.id}</td>
                  <td>{reader.username}</td>
                  <td>{reader.fullName}</td>
                  <td>{reader.email}</td>
                  <td>
                    <span className={reader.enabled ? 'badge ok' : 'badge danger'}>
                      {reader.enabled ? 'Enabled' : 'Disabled'}
                    </span>
                  </td>
                  <td>
                    <button className="link" type="button" onClick={() => handleEdit(reader)}>
                      Edit
                    </button>
                    <button className="link danger" type="button" onClick={() => handleDelete(reader)}>
                      Delete
                    </button>
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

export default ReaderPage;
