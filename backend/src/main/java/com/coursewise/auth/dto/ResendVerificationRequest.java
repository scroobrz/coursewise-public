package com.coursewise.auth.dto;

import com.coursewise.auth.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the resend-verification endpoint.
 *
 * @param email The email address to resend the verification link to
 */
public record ResendVerificationRequest(
        @NotBlank(message = "Email is required")
        @ValidEmail
        String email
) {}
