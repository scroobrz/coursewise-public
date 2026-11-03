package com.coursewise.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Course} entities.
 * Provides basic CRUD operations and custom query methods.
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, String> {}
