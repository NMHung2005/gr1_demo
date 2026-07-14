package com.example.demo.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.demo.model.Loan;
import com.example.demo.model.LoanStatus;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByUser_Id(Long userId);

    List<Loan> findByBook_Id(Long bookId);

    List<Loan> findByStatus(LoanStatus status);

    List<Loan> findByStatusIn(Collection<LoanStatus> statuses);

    List<Loan> findByDueDateBeforeAndStatusIn(LocalDate date, Collection<LoanStatus> statuses);

    boolean existsByBook_IdAndStatusIn(Long bookId, Collection<LoanStatus> statuses);

    boolean existsByUser_Id(Long userId);

    boolean existsByUser_IdAndBook_IdAndStatusIn(Long userId, Long bookId, Collection<LoanStatus> statuses);

    long countByBook_IdAndStatusIn(Long bookId, Collection<LoanStatus> statuses);

    long countByStatus(LoanStatus status);

    long countByStatusIn(Collection<LoanStatus> statuses);

    @Query("""
            select l.book.id, l.book.title, count(l)
            from Loan l
            group by l.book.id, l.book.title
            order by count(l) desc
            """)
    List<Object[]> findTopBorrowedBooks(Pageable pageable);

    @Query("select coalesce(sum(l.fineAmount), 0) from Loan l")
    BigDecimal sumFineAmount();
}
