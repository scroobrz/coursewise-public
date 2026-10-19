package com.coursewise.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object for email verification during either registration or 2FA login
 *
 * @param token The magic link token sent to the user's email
 */
public record VerifyEmailRequest(
        @NotBlank(message = "Token is required")
        String token
) {}
