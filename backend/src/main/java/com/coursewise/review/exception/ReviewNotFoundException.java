package com.coursewise.review.exception;

import java.util.UUID;

public class ReviewNotFoundException extends RuntimeException {
    public ReviewNotFoundException(UUID userId, String courseCode) {
        super("No review found for user " + userId + " on course " + courseCode);
    }
}
