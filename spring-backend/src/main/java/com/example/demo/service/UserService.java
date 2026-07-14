package com.example.demo.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.demo.dto.UserRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.LoanRepository;
import com.example.demo.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final LoanRepository loanRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, LoanRepository loanRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.loanRepository = loanRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::fromUser)
                .toList();
    }

    public List<UserResponse> getReaders() {
        return userRepository.findByRole(Role.READER)
                .stream()
                .map(UserResponse::fromUser)
                .toList();
    }

    public UserResponse getUserById(Long id) {
        return UserResponse.fromUser(findUserById(id));
    }

    public UserResponse createReader(UserRequest request) {
        validateRequired(request.getUsername(), "Username is required");
        validateRequired(request.getPassword(), "Password is required");
        validateRequired(request.getFullName(), "Full name is required");

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = new User(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName(),
                request.getEmail(),
                Role.READER);
        user.setEnabled(request.getEnabled() == null || request.getEnabled());

        return UserResponse.fromUser(userRepository.save(user));
    }

    public UserResponse updateUser(Long id, UserRequest request) {
        User existing = findUserById(id);

        validateRequired(request.getUsername(), "Username is required");
        validateRequired(request.getFullName(), "Full name is required");

        boolean usernameChanged = !existing.getUsername().equals(request.getUsername());
        if (usernameChanged && userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        existing.setUsername(request.getUsername());
        existing.setFullName(request.getFullName());
        existing.setEmail(request.getEmail());
        if (!isBlank(request.getPassword())) {
            existing.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getEnabled() != null) {
            existing.setEnabled(request.getEnabled());
        }

        return UserResponse.fromUser(userRepository.save(existing));
    }

    public void deleteUser(Long id) {
        User existing = findUserById(id);

        if (loanRepository.existsByUser_Id(id)) {
            throw new IllegalArgumentException("Cannot delete user with loan history. Disable the user instead.");
        }

        userRepository.delete(existing);
    }

    private User findUserById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("User id is required");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private void validateRequired(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
