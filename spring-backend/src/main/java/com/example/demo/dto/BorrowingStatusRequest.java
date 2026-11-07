package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;

public record BorrowingStatusRequest(@NotNull Boolean enabled) {
}
