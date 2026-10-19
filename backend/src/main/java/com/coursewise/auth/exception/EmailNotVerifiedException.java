package com.coursewise.auth.exception;

/**
 * Thrown when a user attempts to log in but their email address has
 * not yet been verified via the registration magic link.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Email address has not been verified. Please check your inbox for the verification link.");
    }
}
