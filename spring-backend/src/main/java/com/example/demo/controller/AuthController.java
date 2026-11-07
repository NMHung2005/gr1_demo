package com.example.demo.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.demo.service.CurrentUserService;
import com.example.demo.model.User;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final CurrentUserService currentUser;

    public AuthController(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    public Map<String, Object> me(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {

        Map<String, Object> result = new LinkedHashMap<>();
        User user = currentUser.resolve(authentication);

        result.put("id", user.getId());
        result.put("subject", jwt.getSubject());
        result.put("username", jwt.getClaimAsString("preferred_username"));
        result.put("email", jwt.getClaimAsString("email"));
        result.put("fullName", user.getFullName());
        result.put("role", user.getRole());
        result.put("enabled", user.isEnabled());
        result.put("authorities", authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList());

        return result;
    }
}
