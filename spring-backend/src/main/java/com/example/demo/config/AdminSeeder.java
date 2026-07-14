package com.example.demo.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.model.Book;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.UserRepository;

@Component
public class AdminSeeder implements CommandLineRunner {
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(
            UserRepository userRepository,
            BookRepository bookRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUser(ADMIN_USERNAME, ADMIN_PASSWORD, "Default Librarian", "admin@example.com", Role.LIBRARIAN);
        seedUser("reader01", "reader123", "Nguyen Van An", "reader01@example.com", Role.READER);
        seedUser("reader02", "reader123", "Tran Thi Binh", "reader02@example.com", Role.READER);
        seedUser("reader03", "reader123", "Le Minh Chau", "reader03@example.com", Role.READER);

        seedBook("Clean Code", "Robert C. Martin", "Programming", 2008, 5);
        seedBook("Effective Java", "Joshua Bloch", "Programming", 2018, 4);
        seedBook("Spring in Action", "Craig Walls", "Programming", 2022, 3);
        seedBook("Design Patterns", "Erich Gamma", "Programming", 1994, 3);
        seedBook("The Pragmatic Programmer", "Andrew Hunt", "Programming", 2019, 4);
        seedBook("Atomic Habits", "James Clear", "Self Help", 2018, 6);
        seedBook("Thinking, Fast and Slow", "Daniel Kahneman", "Psychology", 2011, 4);
        seedBook("Sapiens", "Yuval Noah Harari", "History", 2015, 5);
        seedBook("A Brief History of Time", "Stephen Hawking", "Science", 1988, 3);
        seedBook("The Little Prince", "Antoine de Saint-Exupery", "Novel", 1943, 5);
        seedBook("To Kill a Mockingbird", "Harper Lee", "Novel", 1960, 4);
        seedBook("The Alchemist", "Paulo Coelho", "Novel", 1988, 5);
    }

    private void seedUser(String username, String password, String fullName, String email, Role role) {
        if (userRepository.existsByUsername(username)) {
            return;
        }

        User user = new User(username, passwordEncoder.encode(password), fullName, email, role);
        user.setEnabled(true);
        userRepository.save(user);
    }

    private void seedBook(String title, String author, String category, int year, int quantity) {
        if (bookRepository.existsByTitle(title)) {
            return;
        }

        bookRepository.save(new Book(title, author, category, year, quantity));
    }
}
