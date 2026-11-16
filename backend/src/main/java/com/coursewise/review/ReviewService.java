package com.coursewise.review;

import com.coursewise.auth.User;
import com.coursewise.auth.UserRepository;
import com.coursewise.course.exception.CourseNotFoundException;
import com.coursewise.course.CourseRepository;
import com.coursewise.review.dto.ReviewRequest;
import com.coursewise.review.dto.ReviewResponse;
import com.coursewise.review.dto.ReviewSummaryResponse;
import com.coursewise.review.exception.ReviewNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    public List<ReviewResponse> getReviewsForCourse(String courseCode) {
        List<Review> reviews = reviewRepository.findByCourse_CodeOrderByCreatedAtDesc(courseCode);
        log.info("Fetched {} reviews for course: {}", reviews.size(), courseCode);
        return reviews.stream().map(ReviewResponse::fromEntity).toList();
    }

    public ReviewSummaryResponse getSummaryForCourse(String courseCode) {
        List<Review> reviews = reviewRepository.findByCourse_CodeOrderByCreatedAtDesc(courseCode);
        if (reviews.isEmpty()) {
            return new ReviewSummaryResponse(0, null, null, null, null, null);
        }
        double avgWorkload   = reviews.stream().mapToInt(Review::getWorkloadRating).average().orElse(0);
        double avgDifficulty = reviews.stream().mapToInt(Review::getDifficultyRating).average().orElse(0);
        double avgEnjoyment  = reviews.stream().mapToInt(Review::getEnjoymentRating).average().orElse(0);
        double avgLecturer   = reviews.stream().mapToInt(Review::getLecturerRating).average().orElse(0);
        double recommendPct  = reviews.stream().filter(r -> Boolean.TRUE.equals(r.getWouldRecommend())).count() * 100.0 / reviews.size();

        log.info("Computed review summary for course {}", courseCode);
        return new ReviewSummaryResponse(reviews.size(), avgWorkload, avgDifficulty, avgEnjoyment, avgLecturer, recommendPct);
    }

    @Transactional
    public ReviewResponse upsertReview(UUID userId, String courseCode, ReviewRequest request) {
        if (!courseRepository.existsById(courseCode)) {
            throw new CourseNotFoundException(courseCode);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));

        Review review = reviewRepository.findByUserIdAndCourse_Code(userId, courseCode)
                .orElseGet(() -> new Review(user, courseRepository.getReferenceById(courseCode),
                        request.workloadRating(), request.difficultyRating(),
                        request.enjoymentRating(), request.lecturerRating(),
                        request.wouldRecommend(), request.body()));

        if (review.getId() != null) {
            review.update(request.workloadRating(), request.difficultyRating(),
                    request.enjoymentRating(), request.lecturerRating(),
                    request.wouldRecommend(), request.body());
        }

        reviewRepository.save(review);
        log.info("Saved review {} for user {} and course {}", review.getId(), userId, courseCode);
        return ReviewResponse.fromEntity(review);
    }

    @Transactional
    public void deleteReview(UUID userId, String courseCode) {
        Review review = reviewRepository.findByUserIdAndCourse_Code(userId, courseCode)
                .orElseThrow(() -> new ReviewNotFoundException(userId, courseCode));
        reviewRepository.delete(review);
        log.info("Deleted review {} for user {} and course {}", review.getId(), userId, courseCode);
    }
}
