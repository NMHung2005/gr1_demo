package com.example.demo.dto;

public class TopBorrowedBookResponse {
    private Long bookId;
    private String bookTitle;
    private long borrowCount;

    public TopBorrowedBookResponse() {
    }

    public TopBorrowedBookResponse(Long bookId, String bookTitle, long borrowCount) {
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.borrowCount = borrowCount;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public long getBorrowCount() {
        return borrowCount;
    }

    public void setBorrowCount(long borrowCount) {
        this.borrowCount = borrowCount;
    }
}
