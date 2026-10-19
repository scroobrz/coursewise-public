package com.coursewise.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object for verifying a magic link token.
 *
 * @param token The magic link token extracted from the URL
 */
public record MagicLinkVerifyRequest(
        @NotBlank(message = "Token is required")
        String token
) {}
