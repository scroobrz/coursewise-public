package com.coursewise.auth.exception;

/**
 * Thrown when a user attempts to register with an email address that
 * already has an account in the system.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account with this email address already exists");
    }
}
