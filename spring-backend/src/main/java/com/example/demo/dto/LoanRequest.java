package com.example.demo.dto;

import java.time.LocalDate;

public class LoanRequest {
    private Long bookId;
    private Long userId;
    private LocalDate dueDate;

    public LoanRequest() {
    }

    public LoanRequest(Long bookId, Long userId, LocalDate dueDate) {
        this.bookId = bookId;
        this.userId = userId;
        this.dueDate = dueDate;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }
}
