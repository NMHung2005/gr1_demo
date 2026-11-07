package com.example.demo.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.demo.model.Loan;
import com.example.demo.model.LoanStatus;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Loan l set l.status = com.example.demo.model.LoanStatus.OVERDUE
            where l.status = com.example.demo.model.LoanStatus.BORROWED and l.dueDate < :today
            """)
    int markOverdue(@Param("today") LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id")
    Optional<Loan> findByIdForUpdate(@Param("id") Long id);
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
