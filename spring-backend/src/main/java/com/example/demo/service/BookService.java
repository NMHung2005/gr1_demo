package com.example.demo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.model.Book;
import com.example.demo.model.LoanStatus;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.LoanRepository;

@Service
public class BookService {
    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;

    private static final List<LoanStatus> ACTIVE_LOAN_STATUSES = List.of(
            LoanStatus.BORROWED,
            LoanStatus.OVERDUE);

    public BookService(BookRepository bookRepository, LoanRepository loanRepository) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
    }

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    public Book handleCreateBook(Book book) {
        validateQuantity(book.getQuantity());
        book.setAvailable(book.getQuantity());
        return bookRepository.save(book);
    }

    public Book handleUpdateBook(long id, Book updated) {
        Book existing = bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));

        validateQuantity(updated.getQuantity());
        long borrowedCount = loanRepository.countByBook_IdAndStatusIn(id, ACTIVE_LOAN_STATUSES);
        if (updated.getQuantity() < borrowedCount) {
            throw new IllegalArgumentException("Quantity cannot be less than borrowed copies");
        }

        existing.setTitle(updated.getTitle());
        existing.setAuthor(updated.getAuthor());
        existing.setCategory(updated.getCategory());
        existing.setYear(updated.getYear());
        existing.setQuantity(updated.getQuantity());
        existing.setAvailable((int) (updated.getQuantity() - borrowedCount));

        return bookRepository.save(existing);
    }

    public void handleDeleteBook(long id) {
        if (!bookRepository.existsById(id)) {
            throw new IllegalArgumentException("Book not found");
        }
        if (loanRepository.existsByBook_IdAndStatusIn(id, ACTIVE_LOAN_STATUSES)) {
            throw new IllegalArgumentException("Cannot delete book with active loans");
        }
        bookRepository.deleteById(id);
    }

    private void validateQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must be greater than or equal to 0");
        }
    }
}
