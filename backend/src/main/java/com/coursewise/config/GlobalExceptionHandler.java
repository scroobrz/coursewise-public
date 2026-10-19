package com.coursewise.config;

import com.coursewise.auth.exception.EmailAlreadyRegisteredException;
import com.coursewise.auth.exception.EmailNotVerifiedException;
import com.coursewise.auth.exception.InvalidCredentialsException;
import com.coursewise.auth.exception.InvalidTokenException;
import com.coursewise.auth.exception.ResendTooSoonException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Global exception handler providing centralized error handling across all controllers.
 * Formats errors into consistent RFC 9457 ProblemDetail responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception ex) {
        log.error("An unexpected error occurred: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        problem.setType(URI.create("https://coursewise.app/errors/internal-error"));
        problem.setTitle("Internal Server Error");
        return problem;
    }

    /**
     * Handles validation errors for request bodies or method arguments annotated with @Valid.
     *
     * @param ex The MethodArgumentNotValidException thrown during validation
     * @return A ProblemDetail response detailing the validation errors
     */
    // Fires when @Valid fails on a request body or @ModelAttribute object
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation failed for method argument: {}", detail);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create("https://coursewise.app/errors/validation"));
        problem.setTitle("Validation Failed");
        return problem;
    }

    /**
     * Handles validation errors for method-level validation (e.g., @PathVariable, @RequestParam).
     *
     * @param ex The ConstraintViolationException
     * @return A ProblemDetail response detailing the constraint violations
     */
    // Fires when @Validated method-level validation fails (e.g. @PathVariable, @RequestParam)
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(e -> {
                    String path = e.getPropertyPath().toString();
                    String name = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return name + ": " + e.getMessage();
                })
                .collect(Collectors.joining(", "));

        log.warn("Constraint violation: {}", detail);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create("https://coursewise.app/errors/validation"));
        problem.setTitle("Validation Failed");
        return problem;
    }

    /**
     * Handles exceptions related to invalid or expired magic link tokens.
     *
     * @param ex The InvalidTokenException
     * @return A ProblemDetail response with a 400 Bad Request status
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidTokenException(InvalidTokenException ex) {
        log.warn("Invalid token: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://coursewise.app/errors/invalid-token"));
        problem.setTitle("Invalid Token");
        return problem;
    }

    /**
     * Handles invalid login credentials (wrong email or password).
     * Returns 401 Unauthorized with a generic message.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        log.warn("Invalid login credentials");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setType(URI.create("https://coursewise.app/errors/invalid-credentials"));
        problem.setTitle("Authentication Failed");
        return problem;
    }

    /**
     * Handles attempts to log in with an unverified email address.
     * Returns 403 Forbidden.
     */
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ProblemDetail handleEmailNotVerified(EmailNotVerifiedException ex) {
        log.warn("Login attempt with unverified email");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setType(URI.create("https://coursewise.app/errors/email-not-verified"));
        problem.setTitle("Email Not Verified");
        return problem;
    }

    /**
     * Handles duplicate email registration attempts.
     * Returns 409 Conflict.
     */
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        log.warn("Duplicate registration attempt");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://coursewise.app/errors/email-already-registered"));
        problem.setTitle("Email Already Registered");
        return problem;
    }

    /**
     * Handles attempts to resend a verification email before the cooldown
     * period has elapsed. Returns 429 Too Many Requests.
     */
    @ExceptionHandler(ResendTooSoonException.class)
    public ProblemDetail handleResendTooSoon(ResendTooSoonException ex) {
        log.warn("Resend verification attempted too soon");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setType(URI.create("https://coursewise.app/errors/resend-too-soon"));
        problem.setTitle("Resend Too Soon");
        return problem;
    }

    /**
     * Handles general illegal arguments (e.g., adding a duplicate course).
     *
     * @param ex The IllegalArgumentException
     * @return A ProblemDetail response with a 400 Bad Request status
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid request: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://coursewise.app/errors/bad-request"));
        problem.setTitle("Bad Request");
        return problem;
    }
}
