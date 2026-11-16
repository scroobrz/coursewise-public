package com.coursewise.review;

import com.coursewise.review.dto.ReviewRequest;
import com.coursewise.review.dto.ReviewResponse;
import com.coursewise.review.dto.ReviewSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j 
@RestController
@RequestMapping("/api/v1/courses/{courseCode}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public List<ReviewResponse> getReviews(@PathVariable String courseCode) {
        log.info("Received request for reviews for course: {}", courseCode);
        return reviewService.getReviewsForCourse(courseCode);
    }

    @GetMapping("/summary")
    public ReviewSummaryResponse getSummary(@PathVariable String courseCode) {
        log.info("Received request for review summary for course: {}", courseCode);
        return reviewService.getSummaryForCourse(courseCode);
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> upsertReview(
            @PathVariable String courseCode,
            @Valid @RequestBody ReviewRequest request,
            Authentication authentication) {
        log.info("Received request to upsert review for course: {}", courseCode);
        UUID userId = UUID.fromString(authentication.getName());
        ReviewResponse response = reviewService.upsertReview(userId, courseCode, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteReview(
            @PathVariable String courseCode,
            Authentication authentication) {
        log.info("Received request to delete review for course: {}", courseCode);
        UUID userId = UUID.fromString(authentication.getName());
        reviewService.deleteReview(userId, courseCode);
        return ResponseEntity.noContent().build();
    }
}
