package com.coursewise.review.dto;

import com.coursewise.review.Review;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewResponse(
        UUID reviewerId,
        int workloadRating,
        int difficultyRating,
        int enjoymentRating,
        int lecturerRating,
        boolean wouldRecommend,
        String body,
        OffsetDateTime createdAt
) {
    public static ReviewResponse fromEntity(Review review) {
        return new ReviewResponse(
                review.getUser().getId(),
                review.getWorkloadRating(),
                review.getDifficultyRating(),
                review.getEnjoymentRating(),
                review.getLecturerRating(),
                review.getWouldRecommend(),
                review.getBody(),
                review.getCreatedAt()
        );
    }
}
