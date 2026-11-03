package com.coursewise.course.dto;

import com.coursewise.course.Course;

/**
 * Data Transfer Object representing the public response for a {@link Course}.
 *
 * @param code The unique course code
 * @param title The title of the course
 */
public record CourseResponse(
        String code,
        String title
) {
    /**
     * Factory method to map a {@link Course} entity to a {@link CourseResponse}.
     *
     * @param course The course entity to map
     * @return A mapped CourseResponse record
     */
    public static CourseResponse fromEntity(Course course) {
        return new CourseResponse(
                course.getCode(),
                course.getTitle()
        );
    }
}
