package com.example.demo.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.example.demo.model.Book;
import com.example.demo.repository.BookRepository;

@Component
@ConditionalOnProperty(name = "app.seed-books", havingValue = "true", matchIfMissing = true)
public class BookSeeder implements CommandLineRunner {
    private final BookRepository books;

    public BookSeeder(BookRepository books) { this.books = books; }

    @Override
    public void run(String... args) {
        seed("Clean Code", "Robert C. Martin", "Programming", 2008, 5);
        seed("Effective Java", "Joshua Bloch", "Programming", 2018, 4);
        seed("Spring in Action", "Craig Walls", "Programming", 2022, 3);
        seed("Design Patterns", "Erich Gamma", "Programming", 1994, 3);
        seed("The Pragmatic Programmer", "Andrew Hunt", "Programming", 2019, 4);
        seed("Atomic Habits", "James Clear", "Self Help", 2018, 6);
        seed("Thinking, Fast and Slow", "Daniel Kahneman", "Psychology", 2011, 4);
        seed("Sapiens", "Yuval Noah Harari", "History", 2015, 5);
        seed("A Brief History of Time", "Stephen Hawking", "Science", 1988, 3);
        seed("The Little Prince", "Antoine de Saint-Exupery", "Novel", 1943, 5);
        seed("To Kill a Mockingbird", "Harper Lee", "Novel", 1960, 4);
        seed("The Alchemist", "Paulo Coelho", "Novel", 1988, 5);
    }

    private void seed(String title, String author, String category, int year, int quantity) {
        if (!books.existsByTitle(title)) books.save(new Book(title, author, category, year, quantity));
    }
}
