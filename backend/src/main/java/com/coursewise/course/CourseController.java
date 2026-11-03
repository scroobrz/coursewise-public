package com.coursewise.course;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.coursewise.course.dto.CourseResponse;

/**
 * REST controller for exposing course data to clients.
 * Provides endpoints to list and retrieve individual courses.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /**
     * Retrieves a list of all courses available in the system, sorted by course code.
     *
     * @return A list of {@link CourseResponse} objects
     */
    @GetMapping
    public List<CourseResponse> listCourses() {
        log.info("Fetching all courses");
        return courseService.getCourses();
    }

    /**
     * Retrieves a specific course by its unique code.
     *
     * @param code The unique course code (e.g., INFR11159) - this is case-insensitive and will be normalized to uppercase.
     * @return The corresponding {@link CourseResponse}
     */
    @GetMapping("/{code}")
    public CourseResponse getCourse(@PathVariable String code) {
        log.info("Fetching course with code: {}", code);
        return courseService.getCourse(code);
    }
}
