package com.coursewise.auth.exception;

/**
 * Thrown when a user attempts to resend a verification email
 * before the configured cooldown period ({@code coursewise.config.resend-cooldown-seconds})
 * has elapsed.
 */
public class ResendTooSoonException extends RuntimeException {

    public ResendTooSoonException() {
        super("Please wait before requesting another verification email.");
    }
}
