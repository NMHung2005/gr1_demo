package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.time.LocalDate;
import com.example.demo.model.Loan;
import com.example.demo.model.LoanStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.example.demo.model.Book;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.BookRepository;
import com.example.demo.repository.LoanRepository;
import com.example.demo.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KeycloakIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BookRepository books;
    @Autowired LoanRepository loans;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void reset() {
        loans.deleteAll();
        users.deleteAll();
        books.deleteAll();
    }

    private RequestPostProcessor reader(String subject) {
        return identity(subject, subject, "READER");
    }

    private RequestPostProcessor identity(String subject, String username, String role) {
        return jwt().jwt(token -> token.subject(subject)
            .claim("preferred_username", username).claim("name", "Reader " + username)
            .claim("email", username + "@example.com"))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private long profile(String subject) throws Exception {
        String json = mvc.perform(get("/api/auth/me").with(reader(subject)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").isNumber())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long book(int quantity) {
        return books.saveAndFlush(new Book("Test book", "Author", "Code", 2026, quantity)).getId();
    }

    @Test
    void createsOneProfileAndKeepsItWhenUsernameChanges() throws Exception {
        long id = profile("stable-subject");
        mvc.perform(get("/api/auth/me").with(identity("stable-subject", "renamed", "READER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.subject").value("stable-subject"))
            .andExpect(jsonPath("$.username").value("renamed"))
            .andExpect(jsonPath("$.password").doesNotExist());
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void sameUsernameDoesNotTakeOverALegacyProfile() throws Exception {
        User legacy = users.saveAndFlush(new User(null, "reused", "Legacy reader", "old@example.com", Role.READER));
        mvc.perform(get("/api/auth/me").with(identity("new-subject", "reused", "READER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").isNumber());
        assertThat(users.count()).isEqualTo(2);
        assertThat(users.findById(legacy.getId()).orElseThrow().getFullName()).isEqualTo("Legacy reader");
    }

    @Test
    void concurrentFirstRequestsCreateOnlyOneProfile() throws Exception {
        var executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Long>> calls = new ArrayList<>();
            for (int i = 0; i < 6; i++) calls.add(() -> profile("concurrent"));
            var futures = executor.invokeAll(calls);
            long first = futures.get(0).get();
            for (var future : futures) assertThat(future.get()).isEqualTo(first);
            assertThat(users.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void loginDoesNotUndoBorrowingSuspension() throws Exception {
        long id = profile("suspended");
        User user = users.findById(id).orElseThrow();
        user.setEnabled(false);
        users.saveAndFlush(user);
        mvc.perform(get("/api/auth/me").with(reader("suspended")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/loans").with(reader("suspended"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":" + book(1) + "}"))
            .andExpect(status().isBadRequest());
        assertThat(loans.count()).isZero();
    }

    @Test
    void readerCannotChooseAnotherBorrowerOrReadOrReturnTheirLoan() throws Exception {
        long ownId = profile("owner");
        long otherId = profile("other");
        String response = mvc.perform(post("/api/loans").with(reader("owner"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"bookId\":" + book(2) + ",\"userId\":" + otherId + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(ownId))
            .andReturn().getResponse().getContentAsString();
        long loanId = ((Number) JsonPath.read(response, "$.id")).longValue();
        mvc.perform(get("/api/loans/" + loanId).with(reader("other"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/loans/" + loanId + "/return").with(reader("other"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/loans/me").with(reader("other")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/loans/" + loanId + "/return").with(reader("owner")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETURNED"));
    }

    @Test
    void concurrentBorrowingCannotAllocateTheLastCopyTwice() throws Exception {
        profile("borrower-a");
        profile("borrower-b");
        long bookId = book(1);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<Integer>> results = new ArrayList<>();
            for (String subject : List.of("borrower-a", "borrower-b")) {
                results.add(executor.submit(() -> {
                    start.await();
                    return mvc.perform(post("/api/loans").with(reader(subject))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":" + bookId + "}"))
                        .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 400);
            assertThat(loans.count()).isEqualTo(1);
            assertThat(books.findById(bookId).orElseThrow().getAvailable()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rolesProtectBookWritesStatsAndBorrowingStatus() throws Exception {
        long id = profile("reader");
        mvc.perform(get("/api/books")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/books").with(reader("reader")).contentType(MediaType.APPLICATION_JSON)
            .content("{}" )).andExpect(status().isForbidden());
        mvc.perform(get("/api/loans/stats").with(reader("reader"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/users/" + id + "/borrowing-status").with(reader("reader"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/users/" + id + "/borrowing-status").with(identity("librarian", "librarian", "LIBRARIAN"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/users").with(identity("librarian", "librarian", "LIBRARIAN"))
            .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
    }

    @Test
    void overdueUpdateNeverRestoresAReturnedLoan() throws Exception {
        long readerId = profile("overdue-reader");
        Book book = books.findById(book(1)).orElseThrow();
        User reader = users.findById(readerId).orElseThrow();
        Loan returned = new Loan(book, reader, LocalDate.now().minusDays(20), LocalDate.now().minusDays(1));
        returned.setStatus(LoanStatus.RETURNED);
        returned.setReturnDate(LocalDate.now());
        returned = loans.saveAndFlush(returned);
        Loan outstanding = loans.saveAndFlush(new Loan(book, reader,
                LocalDate.now().minusDays(20), LocalDate.now().minusDays(1)));
        int changed = new TransactionTemplate(transactionManager).execute(status -> loans.markOverdue(LocalDate.now()));
        assertThat(changed).isEqualTo(1);
        assertThat(loans.findById(returned.getId()).orElseThrow().getStatus()).isEqualTo(LoanStatus.RETURNED);
        assertThat(loans.findById(outstanding.getId()).orElseThrow().getStatus()).isEqualTo(LoanStatus.OVERDUE);
    }

    @Test
    void librarianCanCreateUpdateAndDeleteAnUnborrowedBook() throws Exception {
        var librarian = identity("book-admin", "book-admin", "LIBRARIAN");
        String json = mvc.perform(post("/api/books").with(librarian).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"New book\",\"author\":\"Author\",\"category\":\"Code\",\"year\":2026,\"quantity\":2}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(2))
            .andReturn().getResponse().getContentAsString();
        long bookId = ((Number) JsonPath.read(json, "$.id")).longValue();
        mvc.perform(put("/api/books/" + bookId).with(librarian).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Updated book\",\"author\":\"Author\",\"category\":\"Code\",\"year\":2026,\"quantity\":3}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(3));
        mvc.perform(delete("/api/books/" + bookId).with(librarian)).andExpect(status().isOk());
        assertThat(books.existsById(bookId)).isFalse();
    }

    @Test
    void concurrentReturnsIncrementInventoryOnlyOnce() throws Exception {
        profile("return-reader");
        long bookId = book(1);
        String json = mvc.perform(post("/api/loans").with(reader("return-reader"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":" + bookId + "}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long loanId = ((Number) JsonPath.read(json, "$.id")).longValue();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> action = () -> {
                start.await();
                return mvc.perform(put("/api/loans/" + loanId + "/return").with(reader("return-reader")))
                    .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(action);
            var second = executor.submit(action);
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 400);
            assertThat(books.findById(bookId).orElseThrow().getAvailable()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
