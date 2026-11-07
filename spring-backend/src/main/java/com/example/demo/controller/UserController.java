package com.example.demo.controller;

import java.util.List;
import jakarta.validation.Valid;
import com.example.demo.dto.BorrowingStatusRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) { this.users = users; }

    @GetMapping
    public List<UserResponse> getAllUsers() { return users.getAllUsers(); }

    @GetMapping("/readers")
    public List<UserResponse> getReaders() { return users.getReaders(); }

    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable Long id) { return users.getUserById(id); }

    @PutMapping("/{id}/borrowing-status")
    public UserResponse updateBorrowingStatus(@PathVariable Long id, @Valid @RequestBody BorrowingStatusRequest request) {
        return users.updateBorrowingStatus(id, request.enabled());
    }
}
