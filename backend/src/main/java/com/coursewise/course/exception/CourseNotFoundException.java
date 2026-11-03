package com.coursewise.course.exception;

/**
 * Exception thrown when a requested course cannot be found in the database.
 * Mapped to a 404 response by the global exception handler.
 */
public class CourseNotFoundException extends RuntimeException {
    /**
     * Constructs a new CourseNotFoundException.
     *
     * @param code The code of the course that was not found
     */
    public CourseNotFoundException(String code) {
        super("Course not found: " + code);
    }
}
