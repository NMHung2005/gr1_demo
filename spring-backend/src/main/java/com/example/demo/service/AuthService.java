package com.example.demo.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RefreshTokenRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.model.RefreshToken;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.CustomUserDetailsService;
import com.example.demo.security.JwtUtil;

@Service
@Transactional
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshTokenExpirationMs;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            CustomUserDetailsService userDetailsService,
            JwtUtil jwtUtil,
            @Value("${app.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public AuthResponse register(RegisterRequest request) {
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
        user.setEnabled(true);

        User savedUser = userRepository.save(user);
        return buildTokenResponse(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        validateRequired(request.getUsername(), "Username is required");
        validateRequired(request.getPassword(), "Password is required");

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("User is disabled");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        return buildTokenResponse(user);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        validateRequired(request.getRefreshToken(), "Refresh token is required");

        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            throw new IllegalArgumentException("Refresh token has been revoked");
        }
        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token has expired");
        }
        if (!refreshToken.getUser().isEnabled()) {
            throw new IllegalArgumentException("User is disabled");
        }

        User user = refreshToken.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String accessToken = jwtUtil.generateAccessToken(userDetails);

        return AuthResponse.fromUserWithTokens(user, accessToken, refreshToken.getToken());
    }

    public void logout(RefreshTokenRequest request) {
        validateRequired(request.getRefreshToken(), "Refresh token is required");

        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    public AuthResponse getByUsername(String username) {
        validateRequired(username, "Username is required");

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return AuthResponse.fromUser(user);
    }

    private AuthResponse buildTokenResponse(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String accessToken = jwtUtil.generateAccessToken(userDetails);
        RefreshToken refreshToken = createRefreshToken(user);

        return AuthResponse.fromUserWithTokens(user, accessToken, refreshToken.getToken());
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken(
                generateSecureToken(),
                user,
                Instant.now().plusMillis(refreshTokenExpirationMs));
        return refreshTokenRepository.save(refreshToken);
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private void validateRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }
}
