package com.example.demo.dto;

import com.example.demo.model.Role;
import com.example.demo.model.User;

public class UserResponse {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private Role role;
    private boolean enabled;
    private String keycloakId;

    public UserResponse() {
    }

    public UserResponse(Long id, String username, String fullName, String email, Role role, boolean enabled) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.enabled = enabled;
    }

    public static UserResponse fromUser(User user) {
        UserResponse response = new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled());
        response.keycloakId = user.getKeycloakId();
        return response;
    }

    public String getKeycloakId() {
        return keycloakId;
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
}
