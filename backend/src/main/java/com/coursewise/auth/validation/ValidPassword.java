package com.coursewise.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom Bean Validation constraint that enforces password complexity rules.
 *
 * <p>The rules (min/max length and complexity regex) are loaded at runtime from
 * {@code coursewise.config.validation.*} via {@link PasswordValidator}, making
 * {@code application.yml} the single source of truth. The frontend receives
 * the same values via {@code GET /api/v1/config}.
 *
 * <p>Usage:
 * <pre>{@code
 * @ValidPassword
 * String password;
 * }</pre>
 */
@Constraint(validatedBy = PasswordValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValidPassword {

    String message() default "Invalid password";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
