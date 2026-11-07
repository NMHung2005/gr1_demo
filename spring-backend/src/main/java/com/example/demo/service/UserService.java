package com.example.demo.service;

import java.util.List;
import com.example.demo.dto.UserResponse;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) { this.users = users; }

    public List<UserResponse> getAllUsers() {
        return users.findAll().stream().map(UserResponse::fromUser).toList();
    }

    public List<UserResponse> getReaders() {
        return users.findByRole(Role.READER).stream().map(UserResponse::fromUser).toList();
    }

    public UserResponse getUserById(Long id) {
        return UserResponse.fromUser(findUser(id));
    }

    @Transactional
    public UserResponse updateBorrowingStatus(Long id, boolean enabled) {
        User user = findUser(id);
        if (user.getRole() != Role.READER) throw new IllegalArgumentException("Only readers have a borrowing status");
        user.setEnabled(enabled);
        return UserResponse.fromUser(users.save(user));
    }

    private User findUser(Long id) {
        return users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
