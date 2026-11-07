package com.example.demo.controller;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.LoanRequest;
import com.example.demo.dto.LoanResponse;
import com.example.demo.dto.LoanStatsResponse;
import com.example.demo.service.LoanService;
import com.example.demo.service.CurrentUserService;

@RestController
@RequestMapping("/api/loans")
public class LoanController {
    private final LoanService loanService;
    private final CurrentUserService currentUser;

    public LoanController(LoanService loanService, CurrentUserService currentUser) {
        this.loanService = loanService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<LoanResponse> getAllLoans(Authentication authentication) {
        if (isLibrarian(authentication)) {
            return loanService.getAllLoans();
        }
        return loanService.getLoansByUser(currentUser.resolve(authentication).getId());
    }

    @GetMapping("/me")
    public List<LoanResponse> getMyLoans(Authentication authentication) {
        return loanService.getLoansByUser(currentUser.resolve(authentication).getId());
    }

    @GetMapping("/overdue")
    public List<LoanResponse> getOverdueLoans(Authentication authentication) {
        requireLibrarian(authentication);
        return loanService.getOverdueLoans();
    }

    @GetMapping("/stats")
    public LoanStatsResponse getStats(Authentication authentication) {
        requireLibrarian(authentication);
        return loanService.getStats();
    }

    @GetMapping("/{id}")
    public LoanResponse getLoanById(@PathVariable Long id, Authentication authentication) {
        if (isLibrarian(authentication)) {
            return loanService.getLoanById(id);
        }
        return loanService.getLoanByIdForUser(id, currentUser.resolve(authentication).getId());
    }

    @PostMapping
    public LoanResponse borrow(@RequestBody LoanRequest request, Authentication authentication) {
        if (isLibrarian(authentication)) {
            return loanService.borrow(request);
        }
        return loanService.borrowForUser(request, currentUser.resolve(authentication).getId());
    }

    @PutMapping("/{id}/return")
    public LoanResponse returnBook(@PathVariable Long id, Authentication authentication) {
        if (isLibrarian(authentication)) {
            return loanService.returnBook(id);
        }
        return loanService.returnBookForUser(id, currentUser.resolve(authentication).getId());
    }

    private boolean isLibrarian(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_LIBRARIAN"));
    }

    private void requireLibrarian(Authentication authentication) {
        if (!isLibrarian(authentication)) {
            throw new AccessDeniedException("Only librarians can access this endpoint");
        }
    }
}
