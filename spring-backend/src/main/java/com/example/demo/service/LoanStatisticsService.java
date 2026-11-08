package com.example.demo.service;

import java.time.LocalDate;

import com.example.demo.dto.LoanStatsResponse;
import com.example.demo.dto.TopBorrowedBookResponse;
import com.example.demo.model.LoanStatus;
import com.example.demo.repository.LoanRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanStatisticsService {
    private final LoanRepository loans;

    public LoanStatisticsService(LoanRepository loans) {
        this.loans = loans;
    }

    @Cacheable(cacheNames = "loanStats", key = "#cacheDate.toString()")
    @Transactional(readOnly = true)
    public LoanStatsResponse getStats(LocalDate cacheDate) {
        long borrowed = loans.countByStatus(LoanStatus.BORROWED);
        long overdue = loans.countByStatus(LoanStatus.OVERDUE);
        long returned = loans.countByStatus(LoanStatus.RETURNED);
        var topBooks = loans.findTopBorrowedBooks(PageRequest.of(0, 5)).stream()
                .map(row -> new TopBorrowedBookResponse((Long) row[0], (String) row[1], (Long) row[2]))
                .toList();
        return new LoanStatsResponse(loans.count(), borrowed + overdue, borrowed, overdue, returned,
                loans.sumFineAmount(), topBooks);
    }
}
