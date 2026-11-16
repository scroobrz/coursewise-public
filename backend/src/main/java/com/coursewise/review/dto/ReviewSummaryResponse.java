package com.coursewise.review.dto;

public record ReviewSummaryResponse(
        int reviewCount,
        Double avgWorkload,
        Double avgDifficulty,
        Double avgEnjoyment,
        Double avgLecturer,
        Double recommendPercent
) {}
