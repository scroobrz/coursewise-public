package com.coursewise.auth.dto;

import com.coursewise.auth.validation.ValidEmail;
import com.coursewise.auth.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

/**
 * Data Transfer Object for user registration.
 *
 * <p>Validation rules (email regex, password length and complexity) are enforced
 * by the custom {@link ValidEmail} and {@link ValidPassword} constraints, which
 * load their rules from {@code coursewise.config.validation.*} in {@code application.yml}.
 * This makes {@code application.yml} the single source of truth shared with the
 * Angular frontend via {@code GET /api/v1/config}.
 *
 * @param email    The university student email address (e.g. {@code s1234567@ed.ac.uk})
 * @param password The user's chosen password
 */
public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @ValidEmail
        String email,

        @NotBlank(message = "Password is required")
        @ValidPassword
        String password
) {}
