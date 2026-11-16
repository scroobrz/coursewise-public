package com.coursewise.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReviewRequest(
        @NotNull @Min(1) @Max(5) Integer workloadRating,
        @NotNull @Min(1) @Max(5) Integer difficultyRating,
        @NotNull @Min(1) @Max(5) Integer enjoymentRating,
        @NotNull @Min(1) @Max(5) Integer lecturerRating,
        @NotNull Boolean wouldRecommend,
        String body
) {}
