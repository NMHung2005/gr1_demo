package com.example.demo.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.LoanRequest;
import com.example.demo.dto.LoanResponse;
import com.example.demo.dto.LoanStatsResponse;
import com.example.demo.model.Book;
import com.example.demo.model.Loan;
import com.example.demo.model.LoanStatus;
import com.example.demo.model.User;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.LoanRepository;
import com.example.demo.repository.UserRepository;

@Service
public class LoanService {
    private static final int DEFAULT_LOAN_DAYS = 14;
    private static final BigDecimal FINE_PER_DAY = BigDecimal.valueOf(5000);
    private static final List<LoanStatus> ACTIVE_LOAN_STATUSES = List.of(
            LoanStatus.BORROWED,
            LoanStatus.OVERDUE);

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final LoanStatisticsService statistics;
    private final OverdueLoanService overdueLoans;

    public LoanService(
            LoanRepository loanRepository,
            BookRepository bookRepository,
            UserRepository userRepository,
            LoanStatisticsService statistics,
            OverdueLoanService overdueLoans) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.statistics = statistics;
        this.overdueLoans = overdueLoans;
    }

    @Transactional
    public List<LoanResponse> getAllLoans() {
        updateOverdueLoans();
        return loanRepository.findAll()
                .stream()
                .map(LoanResponse::fromLoan)
                .toList();
    }

    @Transactional
    public LoanResponse getLoanById(Long id) {
        Loan loan = findLoanById(id);
        updateOverdueStatusIfNeeded(loan);
        return LoanResponse.fromLoan(loan);
    }

    @Transactional
    public List<LoanResponse> getLoansByUser(Long userId) {
        findUserById(userId);
        updateOverdueLoans();
        return loanRepository.findByUser_Id(userId)
                .stream()
                .map(LoanResponse::fromLoan)
                .toList();
    }

    @Transactional
    public List<LoanResponse> getOverdueLoans() {
        updateOverdueLoans();
        return loanRepository.findByStatus(LoanStatus.OVERDUE)
                .stream()
                .map(LoanResponse::fromLoan)
                .toList();
    }

    public LoanStatsResponse getStats() {
        LocalDate today = LocalDate.now();
        // A separate transaction commits overdue changes/eviction before the cached read.
        overdueLoans.refresh(today);
        return statistics.getStats(today);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "books", key = "'all'"),
            @CacheEvict(cacheNames = "loanStats", allEntries = true)
    })
    public LoanResponse borrow(LoanRequest request) {
        Book book = findBookByIdForUpdate(request.getBookId());
        User user = findUserById(request.getUserId());

        if (user.getKeycloakId() == null || user.getRole() != com.example.demo.model.Role.READER) {
            throw new IllegalArgumentException("Select a reader linked to Keycloak");
        }

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("User is disabled");
        }
        if (book.getAvailable() <= 0) {
            throw new IllegalArgumentException("Book is not available");
        }
        if (loanRepository.existsByUser_IdAndBook_IdAndStatusIn(user.getId(), book.getId(), ACTIVE_LOAN_STATUSES)) {
            throw new IllegalArgumentException("User already has an active loan for this book");
        }

        LocalDate borrowDate = LocalDate.now();
        LocalDate dueDate = request.getDueDate() != null
                ? request.getDueDate()
                : borrowDate.plusDays(DEFAULT_LOAN_DAYS);

        if (dueDate.isBefore(borrowDate)) {
            throw new IllegalArgumentException("Due date cannot be before borrow date");
        }

        Loan loan = new Loan(book, user, borrowDate, dueDate);
        book.setAvailable(book.getAvailable() - 1);

        Loan savedLoan = loanRepository.save(loan);
        bookRepository.save(book);

        return LoanResponse.fromLoan(savedLoan);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "books", key = "'all'"),
            @CacheEvict(cacheNames = "loanStats", allEntries = true)
    })
    public LoanResponse borrowForUser(LoanRequest request, Long userId) {
        LoanRequest readerRequest = new LoanRequest(request.getBookId(), userId, request.getDueDate());
        return borrow(readerRequest);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "books", key = "'all'"),
            @CacheEvict(cacheNames = "loanStats", allEntries = true)
    })
    public LoanResponse returnBook(Long loanId) {
        Loan loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        if (loan.getStatus() == LoanStatus.RETURNED) {
            throw new IllegalArgumentException("Loan has already been returned");
        }

        LocalDate returnDate = LocalDate.now();
        loan.setReturnDate(returnDate);
        loan.setStatus(LoanStatus.RETURNED);
        loan.setFineAmount(calculateFine(returnDate, loan.getDueDate()));

        Book book = findBookByIdForUpdate(loan.getBook().getId());
        book.setAvailable(book.getAvailable() + 1);

        Loan savedLoan = loanRepository.save(loan);
        bookRepository.save(book);

        return LoanResponse.fromLoan(savedLoan);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "books", key = "'all'"),
            @CacheEvict(cacheNames = "loanStats", allEntries = true)
    })
    public LoanResponse returnBookForUser(Long loanId, Long userId) {
        Loan loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));
        if (!loan.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You can only return your own loans");
        }
        return returnBook(loanId);
    }

    @Transactional
    public LoanResponse getLoanByIdForUser(Long loanId, Long userId) {
        Loan loan = findLoanById(loanId);
        if (!loan.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You can only view your own loans");
        }
        updateOverdueStatusIfNeeded(loan);
        return LoanResponse.fromLoan(loan);
    }

    private Book findBookByIdForUpdate(Long bookId) {
        if (bookId == null) {
            throw new IllegalArgumentException("Book id is required");
        }
        return bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found"));
    }

    private User findUserById(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private Loan findLoanById(Long loanId) {
        if (loanId == null) {
            throw new IllegalArgumentException("Loan id is required");
        }
        return loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));
    }

    private BigDecimal calculateFine(LocalDate returnDate, LocalDate dueDate) {
        if (!returnDate.isAfter(dueDate)) {
            return BigDecimal.ZERO;
        }
        long lateDays = ChronoUnit.DAYS.between(dueDate, returnDate);
        return FINE_PER_DAY.multiply(BigDecimal.valueOf(lateDays));
    }

    private void updateOverdueLoans() {
        // A conditional database update cannot overwrite a concurrently committed
        // return.
        overdueLoans.refresh(LocalDate.now());
    }

    private void updateOverdueStatusIfNeeded(Loan loan) {
        overdueLoans.refreshOne(loan, LocalDate.now());
    }
}
