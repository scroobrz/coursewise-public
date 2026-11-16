package com.coursewise.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByCourse_CodeOrderByCreatedAtDesc(String courseCode);

    Optional<Review> findByUserIdAndCourse_Code(UUID userId, String courseCode);
}
