package com.example.demo.service;

import java.time.LocalDate;

import com.example.demo.model.Loan;
import com.example.demo.model.LoanStatus;
import com.example.demo.repository.LoanRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OverdueLoanService {
    private final LoanRepository loans;

    public OverdueLoanService(LoanRepository loans) {
        this.loans = loans;
    }

    @Transactional
    @CacheEvict(cacheNames = "loanStats", allEntries = true, condition = "#result > 0")
    public int refresh(LocalDate today) {
        return loans.markOverdue(today);
    }

    @Transactional
    @CacheEvict(cacheNames = "loanStats", allEntries = true, condition = "#result")
    public boolean refreshOne(Loan loan, LocalDate today) {
        if (loan.getStatus() != LoanStatus.BORROWED || !loan.getDueDate().isBefore(today))
            return false;
        loan.setStatus(LoanStatus.OVERDUE);
        loans.save(loan);
        return true;
    }
}
