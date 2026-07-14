package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.example.demo.model.Book;
import com.example.demo.service.BookService;

@RestController
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/api/books")
    public List<Book> getBooks() {
        return bookService.getAllBooks();
    }

    @PostMapping("/api/books")
    public Book createBook(@RequestBody Book book) {
        return bookService.handleCreateBook(book);
    }

    @PutMapping("/api/books/{id}")
    public Book updateBook(@PathVariable("id") long id, @RequestBody Book book) {
        return bookService.handleUpdateBook(id, book);
    }

    @DeleteMapping("/api/books/{id}")
    public void deleteBook(@PathVariable("id") long id) {
        bookService.handleDeleteBook(id);
    }
}
