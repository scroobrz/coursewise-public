package com.coursewise.auth.dto;

import com.coursewise.auth.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object for password-based login (step 1 of 2FA).
 *
 * @param email    The user's university email
 * @param password The user's password
 */
public record LoginRequest(
        @NotBlank(message = "Email is required")
        @ValidEmail
        String email,

        @NotBlank(message = "Password is required")
        String password
) {}
