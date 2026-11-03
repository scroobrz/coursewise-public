package com.coursewise.course;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coursewise.course.dto.CourseResponse;
import com.coursewise.course.exception.CourseNotFoundException;

import java.util.List;

/**
 * Service class for managing courses.
 * Handles business logic, transaction management, and mapping to response DTOs.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;

    /**
     * Lists the courses currently in the system.
     *
     * @return A list of mapped {@link CourseResponse} records
     */
    public List<CourseResponse> getCourses() {
        return courseRepository.findAll().stream()
                .map(CourseResponse::fromEntity)
                .toList();
    }

    /**
     * Retrieves a single course by its code.
     *
     * @param code The unique course code
     * @return The mapped {@link CourseResponse}
     * @throws {@link CourseNotFoundException} if the course does not exist.
     */
    public CourseResponse getCourse(String code) {
        return courseRepository.findById(code)
                .map(CourseResponse::fromEntity)
                .orElseThrow(() -> {
                    return new CourseNotFoundException(code);
                });
    }
}
