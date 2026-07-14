export function filterBooks(books, keyword) {
  const searchText = keyword.trim().toLowerCase();

  if (!searchText) {
    return books;
  }

  return books.filter((book) => {
    const title = String(book.title || '').toLowerCase();
    const author = String(book.author || '').toLowerCase();
    const category = String(book.category || '').toLowerCase();

    return title.includes(searchText) || author.includes(searchText) || category.includes(searchText);
  });
}
