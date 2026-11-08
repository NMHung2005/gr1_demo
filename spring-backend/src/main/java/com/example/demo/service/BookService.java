package com.example.demo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.BookResponse;
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

    /*
     * public List<Book> getAllBooks() {
     * return bookRepository.findAll();
     * }
     */

    @Cacheable(cacheNames = "books", key = "'all'")
    public List<BookResponse> getAllBooks() {
        return bookRepository.findAll()
                .stream()
                .map(BookResponse::fromBook)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "books", key = "'all'"),
            @CacheEvict(cacheNames = "loanStats", allEntries = true)
    })
    public Book handleCreateBook(Book book) {
        validateQuantity(book.getQuantity());
        book.setAvailable(book.getQuantity());
        return bookRepository.save(book);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "books", key = "'all'"),
            @CacheEvict(cacheNames = "loanStats", allEntries = true)
    })
    public Book handleUpdateBook(long id, Book updated) {
        Book existing = bookRepository.findByIdForUpdate(id)
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

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "books", key = "'all'"),
            @CacheEvict(cacheNames = "loanStats", allEntries = true)
    })
    public void handleDeleteBook(long id) {
        bookRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));
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
