package com.coursewise.auth.dto;

import com.coursewise.auth.validation.ValidEmail;
import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object for requesting a new magic link.
 *
 * @param email The university student email address
 */
public record MagicLinkRequest(
        @NotBlank(message = "Email is required")
        @ValidEmail
        String email) {
}
