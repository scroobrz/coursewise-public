package com.coursewise.auth.exception;

import java.util.UUID;

/**
 * Thrown when an authenticated session refers to a user that no longer exists
 * (e.g. the account was deleted while the session was still live).
 * Mapped to 401 so clients treat the session as invalid.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("User not found: " + id);
    }
}
