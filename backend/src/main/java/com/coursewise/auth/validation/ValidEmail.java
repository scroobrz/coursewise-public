package com.coursewise.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom Bean Validation constraint that enforces university email rules.
 *
 * <p>The validation regex is loaded at runtime from
 * {@code coursewise.config.validation.email-regex} via {@link EmailValidator},
 * so changing the regex in {@code application.yml} is the only change needed
 * to update the rule across both backend validation and the frontend
 * (which receives the regex via {@code GET /api/v1/config}).
 *
 * <p>Usage:
 * <pre>{@code
 * @ValidEmail
 * String email;
 * }</pre>
 */
@Constraint(validatedBy = EmailValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValidEmail {

    String message() default "Must be a valid university student email (e.g. s1234567@ed.ac.uk)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
