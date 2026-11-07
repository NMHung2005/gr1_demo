import { useEffect, useState } from 'react';
import { apiClient } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import EmptyState from '../components/EmptyState';
import { filterBooks } from '../utils/bookFilters';

function BookPage({ readOnly = false }) {
  const { isLibrarian, isReader } = useAuth();
  const showActions = !readOnly && (isLibrarian || isReader);
  const [books, setBooks] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [keyword, setKeyword] = useState('');
  const [form, setForm] = useState({
    title: '',
    author: '',
    category: '',
    year: '',
    quantity: ''
  });

  const resetForm = () => {
    setForm({ title: '', author: '', category: '', year: '', quantity: '' });
    setEditingId(null);
  };

  const fetchBooks = () => {
    setLoading(true);
    return apiClient('/api/books')
      .then((data) => {
        setBooks(Array.isArray(data) ? data : []);
        setError('');
      })
      .catch((err) => {
        setError(err.message || 'Fetch failed');
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    fetchBooks();
  }, []);

  const handleChange = (event) => {
    setForm({ ...form, [event.target.name]: event.target.value });
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    setSaving(true);
    setError('');

    const payload = {
      title: form.title.trim(),
      author: form.author.trim(),
      category: form.category.trim(),
      year: Number(form.year) || 0,
      quantity: Number(form.quantity) || 0
    };

    const url = editingId ? `/api/books/${editingId}` : '/api/books';
    const method = editingId ? 'PUT' : 'POST';

    apiClient(url, {
      method,
      body: JSON.stringify(payload)
    })
      .then(() => {
        resetForm();
        return fetchBooks();
      })
      .catch((err) => {
        setError(err.message || 'Create failed');
      })
      .finally(() => {
        setSaving(false);
      });
  };

  const handleEdit = (book) => {
    setEditingId(book.id);
    setForm({
      title: book.title || '',
      author: book.author || '',
      category: book.category || '',
      year: book.year ?? '',
      quantity: book.quantity ?? ''
    });
    setError('');
  };

  const handleCancelEdit = () => {
    resetForm();
    setError('');
  };

  const handleDelete = (book) => {
    const confirmed = window.confirm(`Delete book "${book.title}"?`);
    if (!confirmed) {
      return;
    }

    setError('');
    apiClient(`/api/books/${book.id}`, { method: 'DELETE' })
      .then(() => fetchBooks())
      .catch((err) => {
        setError(err.message || 'Delete failed');
      });
  };

  const handleBorrow = (book) => {
    setError('');
    apiClient('/api/loans', {
      method: 'POST',
      body: JSON.stringify({ bookId: book.id })
    })
      .then(() => fetchBooks())
      .catch((err) => {
        setError(err.message || 'Borrow failed');
      });
  };

  const filteredBooks = filterBooks(books, keyword);

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Books</h1>
          <p className="muted">{readOnly ? 'Tra cứu sách theo tên, tác giả hoặc thể loại.' : 'Manage books and borrow available copies.'}</p>
        </div>
      </div>

      <div className="simple-form book-search field">
        <label htmlFor="bookSearch">Search books</label>
        <input
          id="bookSearch"
          type="search"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder="Search by title, author, or category"
        />
      </div>

      {isLibrarian && !readOnly ? (
        <form className="simple-form grid-form" onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="title">Title</label>
            <input id="title" name="title" value={form.title} onChange={handleChange} required />
          </div>
          <div className="field">
            <label htmlFor="author">Author</label>
            <input id="author" name="author" value={form.author} onChange={handleChange} required />
          </div>
          <div className="field">
            <label htmlFor="category">Category</label>
            <input id="category" name="category" value={form.category} onChange={handleChange} required />
          </div>
          <div className="field">
            <label htmlFor="year">Year</label>
            <input id="year" name="year" type="number" value={form.year} onChange={handleChange} min="0" />
          </div>
          <div className="field">
            <label htmlFor="quantity">Quantity</label>
            <input id="quantity" name="quantity" type="number" value={form.quantity} onChange={handleChange} min="0" />
          </div>
          <div className="form-actions">
            <button type="submit" disabled={saving}>
              {saving ? 'Saving...' : editingId ? 'Update Book' : 'Add Book'}
            </button>
            {editingId ? (
              <button type="button" className="secondary-button" onClick={handleCancelEdit}>
                Cancel
              </button>
            ) : null}
          </div>
        </form>
      ) : null}

      {loading ? <p>Loading...</p> : null}
      {error ? <p className="error">Error: {error}</p> : null}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Title</th>
              <th>Author</th>
              <th>Category</th>
              <th>Year</th>
              <th>Quantity</th>
              <th>Available</th>
              {showActions && <th>Actions</th>}
            </tr>
          </thead>
          <tbody>
            {filteredBooks.length === 0 ? (
              <EmptyState
                colSpan={showActions ? 8 : 7}
                message={books.length === 0 ? 'No books' : 'No books match your search'}
              />
            ) : (
              filteredBooks.map((book) => (
                <tr key={book.id}>
                  <td>{book.id}</td>
                  <td>{book.title}</td>
                  <td>{book.author}</td>
                  <td>{book.category}</td>
                  <td>{book.year}</td>
                  <td>{book.quantity}</td>
                  <td>
                    <span className={book.available > 0 ? 'badge ok' : 'badge danger'}>
                      {book.available ?? 0}
                    </span>
                  </td>
                  {showActions && <td>
                    {isReader ? (
                      <button
                        className={(book.available ?? 0) > 0 ? 'link' : 'link unavailable'}
                        type="button"
                        onClick={() => handleBorrow(book)}
                        disabled={(book.available ?? 0) <= 0}
                      >
                        {(book.available ?? 0) > 0 ? 'Borrow' : 'Out of stock'}
                      </button>
                    ) : null}
                    {isLibrarian ? (
                      <>
                        <button className="link" type="button" onClick={() => handleEdit(book)}>
                          Edit
                        </button>
                        <button className="link danger" type="button" onClick={() => handleDelete(book)}>
                          Delete
                        </button>
                      </>
                    ) : null}
                  </td>}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}

export default BookPage;
