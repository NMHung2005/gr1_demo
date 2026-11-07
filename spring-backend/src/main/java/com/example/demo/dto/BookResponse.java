package com.example.demo.dto;

import com.example.demo.model.Book;

public record BookResponse(
        Long id,
        String title,
        String author,
        String category,
        int year,
        int quantity,
        int available) {

    public static BookResponse fromBook(Book book) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getCategory(),
                book.getYear(),
                book.getQuantity(),
                book.getAvailable());
    }
}
