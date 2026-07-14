package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.List;

public class LoanStatsResponse {
    private long totalLoans;
    private long activeLoans;
    private long borrowedLoans;
    private long overdueLoans;
    private long returnedLoans;
    private BigDecimal totalFineAmount;
    private List<TopBorrowedBookResponse> topBorrowedBooks;

    public LoanStatsResponse() {
    }

    public LoanStatsResponse(
            long totalLoans,
            long activeLoans,
            long borrowedLoans,
            long overdueLoans,
            long returnedLoans,
            BigDecimal totalFineAmount,
            List<TopBorrowedBookResponse> topBorrowedBooks) {
        this.totalLoans = totalLoans;
        this.activeLoans = activeLoans;
        this.borrowedLoans = borrowedLoans;
        this.overdueLoans = overdueLoans;
        this.returnedLoans = returnedLoans;
        this.totalFineAmount = totalFineAmount;
        this.topBorrowedBooks = topBorrowedBooks;
    }

    public long getTotalLoans() {
        return totalLoans;
    }

    public void setTotalLoans(long totalLoans) {
        this.totalLoans = totalLoans;
    }

    public long getActiveLoans() {
        return activeLoans;
    }

    public void setActiveLoans(long activeLoans) {
        this.activeLoans = activeLoans;
    }

    public long getBorrowedLoans() {
        return borrowedLoans;
    }

    public void setBorrowedLoans(long borrowedLoans) {
        this.borrowedLoans = borrowedLoans;
    }

    public long getOverdueLoans() {
        return overdueLoans;
    }

    public void setOverdueLoans(long overdueLoans) {
        this.overdueLoans = overdueLoans;
    }

    public long getReturnedLoans() {
        return returnedLoans;
    }

    public void setReturnedLoans(long returnedLoans) {
        this.returnedLoans = returnedLoans;
    }

    public BigDecimal getTotalFineAmount() {
        return totalFineAmount;
    }

    public void setTotalFineAmount(BigDecimal totalFineAmount) {
        this.totalFineAmount = totalFineAmount;
    }

    public List<TopBorrowedBookResponse> getTopBorrowedBooks() {
        return topBorrowedBooks;
    }

    public void setTopBorrowedBooks(List<TopBorrowedBookResponse> topBorrowedBooks) {
        this.topBorrowedBooks = topBorrowedBooks;
    }
}
