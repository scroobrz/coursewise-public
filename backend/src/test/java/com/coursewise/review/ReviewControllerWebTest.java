package com.coursewise.review;

import com.coursewise.auth.exception.UserNotFoundException;
import com.coursewise.config.GlobalExceptionHandler;
import com.coursewise.course.exception.CourseNotFoundException;
import com.coursewise.review.dto.ReviewRequest;
import com.coursewise.review.dto.ReviewResponse;
import com.coursewise.review.dto.ReviewSummaryResponse;
import com.coursewise.review.exception.ReviewNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        ReviewControllerWebTest.MvcConfig.class,
        ReviewController.class,
        GlobalExceptionHandler.class
})
@WebAppConfiguration
class ReviewControllerWebTest {

    @TestConfiguration
    @EnableWebMvc
    static class MvcConfig {
    }

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private ReviewService reviewService;

    private MockMvc mockMvc;

    private static final OffsetDateTime FIXED_CREATED_AT = OffsetDateTime.parse("2026-01-15T10:30:00Z");
    private static final String FIXED_CREATED_AT_JSON = "2026-01-15T10:30:00Z";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();
    private final UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Nested
    class GetReviews {

        @Test
        void getReviews_returns200AndReviewResponsesForAllReviews() throws Exception {
            UUID reviewerId1 = UUID.randomUUID();
            UUID reviewerId2 = UUID.randomUUID();
            OffsetDateTime now = FIXED_CREATED_AT;

            when(reviewService.getReviewsForCourse(eq("INFR11159")))
                .thenReturn(List.of(
                    new ReviewResponse(reviewerId1, 4, 3, 5, 4, true, "Great course!", now),
                    new ReviewResponse(reviewerId2, 5, 4, 2, 3, false, "Avoid this course!", now)));

            mockMvc.perform(get("/api/v1/courses/INFR11159/reviews"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].reviewerId").value(reviewerId1.toString()))
                    .andExpect(jsonPath("$[0].workloadRating").value(4))
                    .andExpect(jsonPath("$[0].difficultyRating").value(3))
                    .andExpect(jsonPath("$[0].enjoymentRating").value(5))
                    .andExpect(jsonPath("$[0].lecturerRating").value(4))
                    .andExpect(jsonPath("$[0].wouldRecommend").value(true))
                    .andExpect(jsonPath("$[0].body").value("Great course!"))
                    .andExpect(jsonPath("$[0].createdAt").value(FIXED_CREATED_AT_JSON))
                    .andExpect(jsonPath("$[1].reviewerId").value(reviewerId2.toString()))
                    .andExpect(jsonPath("$[1].workloadRating").value(5))
                    .andExpect(jsonPath("$[1].difficultyRating").value(4))
                    .andExpect(jsonPath("$[1].enjoymentRating").value(2))
                    .andExpect(jsonPath("$[1].lecturerRating").value(3))
                    .andExpect(jsonPath("$[1].wouldRecommend").value(false))
                    .andExpect(jsonPath("$[1].body").value("Avoid this course!"))
                    .andExpect(jsonPath("$[1].createdAt").value(FIXED_CREATED_AT_JSON));
        }

        @Test
        void getReviews_withUnknownCourse_returnsNotFound() throws Exception {
            when(reviewService.getReviewsForCourse("NOTFOUND"))
                .thenThrow(new CourseNotFoundException("NOTFOUND"));

            mockMvc.perform(get("/api/v1/courses/NOTFOUND/reviews"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Course Not Found"))
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    class GetReviewSummary {

        @Test
        void getSummary_returns200AndReviewSummaryResponse() throws Exception {
            when(reviewService.getSummaryForCourse("INFR11159"))
                .thenReturn(new ReviewSummaryResponse(10, 4.0, 3.5, 4.5, 4.2, 90.0));

            mockMvc.perform(get("/api/v1/courses/INFR11159/reviews/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reviewCount").value(10))
                    .andExpect(jsonPath("$.avgWorkload").value(4.0))
                    .andExpect(jsonPath("$.avgDifficulty").value(3.5))
                    .andExpect(jsonPath("$.avgEnjoyment").value(4.5))
                    .andExpect(jsonPath("$.avgLecturer").value(4.2))
                    .andExpect(jsonPath("$.recommendPercent").value(90.0));
        }

        @Test
        void getSummary_withUnknownCourse_returnsNotFound() throws Exception {
            when(reviewService.getSummaryForCourse("NOTFOUND"))
                .thenThrow(new CourseNotFoundException("NOTFOUND"));

            mockMvc.perform(get("/api/v1/courses/NOTFOUND/reviews/summary"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Course Not Found"))
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    class UpsertReview {

        @Test
        void upsertReview_withValidPayload_returnsCreatedAndReviewResponse() throws Exception {
            UUID reviewerId = UUID.randomUUID();
            OffsetDateTime now = FIXED_CREATED_AT;
            ReviewRequest request = new ReviewRequest(
                4, 3, 5, 4, true, "Great course!"
            );
            ReviewResponse response = new ReviewResponse(
                reviewerId, 4, 3, 5, 4, true, "Great course!", now
            );

            when(reviewService.upsertReview(eq(userId), eq("INFR11159"), any(ReviewRequest.class)))
                .thenReturn(response);

            mockMvc.perform(post("/api/v1/courses/INFR11159/reviews")
                            .principal(principal)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reviewerId").value(reviewerId.toString()))
                    .andExpect(jsonPath("$.workloadRating").value(4))
                    .andExpect(jsonPath("$.difficultyRating").value(3))
                    .andExpect(jsonPath("$.enjoymentRating").value(5))
                    .andExpect(jsonPath("$.lecturerRating").value(4))
                    .andExpect(jsonPath("$.wouldRecommend").value(true))
                    .andExpect(jsonPath("$.body").value("Great course!"))
                    .andExpect(jsonPath("$.createdAt").value(FIXED_CREATED_AT_JSON));
        }

        @ParameterizedTest
        @MethodSource("com.coursewise.TestParameters#invalidReviewRequests")
        void upsertReview_invalidPayload_returnsBadRequest(ReviewRequest badRequest) throws Exception {
            mockMvc.perform(post("/api/v1/courses/INFR11159/reviews")
                            .principal(principal)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        void upsertReview_acceptsNullBody() throws Exception {
            mockMvc.perform(post("/api/v1/courses/INFR11159/reviews")
                            .principal(principal)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ReviewRequest(4, 3, 5, 4, true, null))))
                    .andExpect(status().isCreated());
        }

        @Test
        void upsertReview_withUnknownCourse_returnsNotFound() throws Exception {
            ReviewRequest request = new ReviewRequest(4, 3, 5, 4, true, "Great!");

            when(reviewService.upsertReview(eq(userId), eq("NOTFOUND"), any(ReviewRequest.class)))
                    .thenThrow(new CourseNotFoundException("NOTFOUND"));

            mockMvc.perform(post("/api/v1/courses/NOTFOUND/reviews")
                            .principal(principal)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Course Not Found"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void upsertReview_whenSessionUserNoLongerExists_returnsUnauthorized() throws Exception {
            ReviewRequest request = new ReviewRequest(4, 3, 5, 4, true, "Great!");

            when(reviewService.upsertReview(eq(userId), eq("INFR11159"), any(ReviewRequest.class)))
                    .thenThrow(new UserNotFoundException(userId));

            mockMvc.perform(post("/api/v1/courses/INFR11159/reviews")
                            .principal(principal)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("Authentication Failed"))
                    .andExpect(jsonPath("$.status").value(401));
        }
    }

    @Nested
    class DeleteReview {

        @Test
        void deleteReview_success_returnsNoContent() throws Exception {
            mockMvc.perform(delete("/api/v1/courses/INFR11159/reviews").principal(principal))
                    .andExpect(status().isNoContent());

            verify(reviewService).deleteReview(userId, "INFR11159");
        }

        @Test 
        void deletereview_whenNoReview_returnsNotFound() throws Exception {
            doThrow(new ReviewNotFoundException(userId, "INFR11159"))
                    .when(reviewService).deleteReview(eq(userId), eq("INFR11159"));

            mockMvc.perform(delete("/api/v1/courses/INFR11159/reviews").principal(principal))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Review Not Found"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test 
        void deletereview_withUnknownCourse_returnsNotFound() throws Exception {
            doThrow(new CourseNotFoundException( "NOTFOUND"))
                    .when(reviewService).deleteReview(eq(userId), eq("NOTFOUND"));

            mockMvc.perform(delete("/api/v1/courses/NOTFOUND/reviews").principal(principal))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Course Not Found"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        void deleteReview_whenSessionUserNoLongerExists_returnsUnauthorized() throws Exception {
            doThrow(new UserNotFoundException(userId))
                    .when(reviewService).deleteReview(eq(userId), eq("INFR11159"));

            mockMvc.perform(delete("/api/v1/courses/INFR11159/reviews").principal(principal))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("Authentication Failed"))
                    .andExpect(jsonPath("$.status").value(401));
        }
    }
}
