package com.coursewise.auth.exception;

/**
 * Thrown when a login attempt fails due to incorrect email or password.
 * The error message must be generic ("Invalid email or password") to
 * prevent enumeration attacks — never reveal which field was wrong.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
