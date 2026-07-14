package com.example.demo.dto;

import com.example.demo.model.Role;
import com.example.demo.model.User;

public class AuthResponse {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private Role role;
    private boolean enabled;
    private String accessToken;
    private String refreshToken;
    private String tokenType;

    public AuthResponse() {
    }

    public AuthResponse(Long id, String username, String fullName, String email, Role role, boolean enabled) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.enabled = enabled;
    }

    public AuthResponse(
            Long id,
            String username,
            String fullName,
            String email,
            Role role,
            boolean enabled,
            String accessToken,
            String refreshToken,
            String tokenType) {
        this(id, username, fullName, email, role, enabled);
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
    }

    public static AuthResponse fromUser(User user) {
        return new AuthResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled());
    }

    public static AuthResponse fromUserWithTokens(User user, String accessToken, String refreshToken) {
        return new AuthResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                accessToken,
                refreshToken,
                "Bearer");
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
}
