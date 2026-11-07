package com.example.demo.service;

import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CurrentUserService {
    private final UserRepository users;
    private final TransactionTemplate transaction;

    public CurrentUserService(UserRepository users, PlatformTransactionManager manager) {
        this.users = users;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public User resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("A verified Keycloak identity is required");
        }
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank() || subject.length() > 255) {
            throw new AccessDeniedException("Invalid user identity");
        }
        Role role = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_LIBRARIAN")) ? Role.LIBRARIAN
                : authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_READER")) ? Role.READER : null;
        if (role == null) throw new AccessDeniedException("No library role has been assigned");

        String username = text(jwt.getClaimAsString("preferred_username"), subject);
        String fullName = text(jwt.getClaimAsString("name"), username);
        String email = jwt.getClaimAsString("email");

        try {
            return transaction.execute(status -> {
                User user = users.findByKeycloakId(subject)
                        .orElseGet(() -> new User(subject, username, fullName, email, role));
                // Identity data and roles come from the verified token. Do not reset enabled:
                // it is the library's independent borrowing-suspension flag.
                user.setUsername(username);
                user.setFullName(fullName);
                user.setEmail(email);
                user.setRole(role);
                return users.saveAndFlush(user);
            });
        } catch (DataIntegrityViolationException conflict) {
            // Another request may have provisioned the same subject. The failed transaction
            // has already rolled back; read its committed winner in a fresh transaction.
            return transaction.execute(status -> users.findByKeycloakId(subject)
                    .orElseThrow(() -> conflict));
        }
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
