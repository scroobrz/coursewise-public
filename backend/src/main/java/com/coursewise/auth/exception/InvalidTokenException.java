package com.coursewise.auth.exception;

/**
 * Exception thrown when a magic link token is invalid, expired, or already used.
 * Handled globally to return a 400 Bad Request response.
 */
public class InvalidTokenException extends RuntimeException {
    /**
     * Constructs a new InvalidTokenException.
     *
     * @param message The detailed error message
     */
    public InvalidTokenException(String message) {
        super(message);
    }
}
