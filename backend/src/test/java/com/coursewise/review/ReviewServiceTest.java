package com.coursewise.review;

import com.coursewise.TestFixtures;
import com.coursewise.auth.User;
import com.coursewise.auth.UserRepository;
import com.coursewise.auth.exception.UserNotFoundException;
import com.coursewise.course.Course;
import com.coursewise.course.CourseRepository;
import com.coursewise.course.exception.CourseNotFoundException;
import com.coursewise.review.dto.ReviewRequest;
import com.coursewise.review.dto.ReviewResponse;
import com.coursewise.review.dto.ReviewSummaryResponse;
import com.coursewise.review.exception.ReviewNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseRepository courseRepository;

    @InjectMocks
    private ReviewService reviewService;

    private UUID USER_ID;
    private User USER;
    private String COURSE_CODE;
    private Course COURSE;

    @BeforeEach
    void setUp() {
        USER_ID = UUID.randomUUID();
        USER = new User("s1234567@ed.ac.uk");
        ReflectionTestUtils.setField(USER, "id", USER_ID);

        COURSE_CODE = TestFixtures.uniqueCourseCode("INFR");
        COURSE = new Course();
        ReflectionTestUtils.setField(COURSE, "code", COURSE_CODE);
        ReflectionTestUtils.setField(COURSE, "title", "course title");
    }

    @Nested
    class GetReviewsForCourse {

        @Test
        void getReviewsForCourse_mapsEveryFieldAndPreservesRepositoryOrder() {
            Review newer = persistedReview(USER, 5, 4, 2, 3, false, "Avoid this course!");
            Review older = persistedReview(otherUser(), 4, 3, 5, 4, true, "Great course!");
            when(courseRepository.existsById(COURSE_CODE)).thenReturn(true);
            when(reviewRepository.findByCourse_CodeOrderByCreatedAtDesc(COURSE_CODE))
                    .thenReturn(List.of(newer, older));

            List<ReviewResponse> responses = reviewService.getReviewsForCourse(COURSE_CODE);

            assertThat(responses).containsExactly(
                    ReviewResponse.fromEntity(newer),
                    ReviewResponse.fromEntity(older));
            ReviewResponse first = responses.get(0);
            assertThat(first.reviewerId()).isEqualTo(USER_ID);
            assertThat(first.workloadRating()).isEqualTo(5);
            assertThat(first.difficultyRating()).isEqualTo(4);
            assertThat(first.enjoymentRating()).isEqualTo(2);
            assertThat(first.lecturerRating()).isEqualTo(3);
            assertThat(first.wouldRecommend()).isFalse();
            assertThat(first.body()).isEqualTo("Avoid this course!");
            assertThat(first.createdAt()).isEqualTo(newer.getCreatedAt());
        }

        @Test
        void getReviewsForCourse_whenNoReviews_returnsEmptyList() {
            when(courseRepository.existsById(COURSE_CODE)).thenReturn(true);
            when(reviewRepository.findByCourse_CodeOrderByCreatedAtDesc(COURSE_CODE)).thenReturn(List.of());

            assertThat(reviewService.getReviewsForCourse(COURSE_CODE)).isEmpty();
        }

        @Test
        void getReviewsForCourse_withUnknownCourse_throwsCourseNotFoundWithoutQueryingReviews() {
            when(courseRepository.existsById("NOTFOUND")).thenReturn(false);

            assertThatThrownBy(() -> reviewService.getReviewsForCourse("NOTFOUND"))
                    .isInstanceOf(CourseNotFoundException.class)
                    .hasMessageContaining("NOTFOUND");

            verifyNoInteractions(userRepository, reviewRepository);
        }
    }

    @Nested
    class GetSummaryForCourse {

        @Test
        void getSummaryForCourse_calculatesAveragesAndRecommendPercent() {
            stubReviewsForCourse(
                    persistedReview(USER, 4, 3, 5, 4, true, "Great!"),
                    persistedReview(otherUser(), 2, 5, 1, 2, false, "Bad"));

            ReviewSummaryResponse summary = reviewService.getSummaryForCourse(COURSE_CODE);

            assertThat(summary.reviewCount()).isEqualTo(2);
            assertThat(summary.avgWorkload()).isEqualTo(3.0);
            assertThat(summary.avgDifficulty()).isEqualTo(4.0);
            assertThat(summary.avgEnjoyment()).isEqualTo(3.0);
            assertThat(summary.avgLecturer()).isEqualTo(3.0);
            assertThat(summary.recommendPercent()).isEqualTo(50.0);
        }

        @Test
        void getSummaryForCourse_roundsAveragesAndPercentageToTwoDecimalPlaces() {
            stubReviewsForCourse(
                    persistedReview(USER, 4, 3, 5, 4, true, "a"),
                    persistedReview(otherUser(), 5, 4, 2, 3, true, "b"),
                    persistedReview(otherUser(), 5, 2, 3, 3, false, "c"));

            ReviewSummaryResponse summary = reviewService.getSummaryForCourse(COURSE_CODE);

            assertThat(summary.reviewCount()).isEqualTo(3);
            assertThat(summary.avgWorkload()).isEqualTo(4.67);     // 14 / 3 = 4.666…
            assertThat(summary.avgDifficulty()).isEqualTo(3.0);
            assertThat(summary.avgEnjoyment()).isEqualTo(3.33);    // 10 / 3 = 3.333…
            assertThat(summary.avgLecturer()).isEqualTo(3.33);     // 10 / 3 = 3.333…
            assertThat(summary.recommendPercent()).isEqualTo(66.67); // 2 / 3 = 66.666…
        }

        @Test
        void getSummaryForCourse_whenEveryoneRecommends_returns100Percent() {
            stubReviewsForCourse(
                    persistedReview(USER, 3, 3, 3, 3, true, "a"),
                    persistedReview(otherUser(), 3, 3, 3, 3, true, "b"));

            assertThat(reviewService.getSummaryForCourse(COURSE_CODE).recommendPercent()).isEqualTo(100.0);
        }

        @Test
        void getSummaryForCourse_whenNobodyRecommends_returnsZeroPercent() {
            stubReviewsForCourse(
                    persistedReview(USER, 3, 3, 3, 3, false, "a"),
                    persistedReview(otherUser(), 3, 3, 3, 3, false, "b"));

            assertThat(reviewService.getSummaryForCourse(COURSE_CODE).recommendPercent()).isEqualTo(0.0);
        }

        @Test
        void getSummaryForCourse_withSingleReview_returnsThatReviewsValues() {
            stubReviewsForCourse(persistedReview(USER, 1, 2, 3, 4, true, "only"));

            ReviewSummaryResponse summary = reviewService.getSummaryForCourse(COURSE_CODE);

            assertThat(summary).isEqualTo(new ReviewSummaryResponse(1, 1.0, 2.0, 3.0, 4.0, 100.0));
        }

        @Test
        void getSummaryForCourse_whenNoReviews_returnsZeroCountAndNullAggregates() {
            stubReviewsForCourse();

            ReviewSummaryResponse summary = reviewService.getSummaryForCourse(COURSE_CODE);

            assertThat(summary).isEqualTo(new ReviewSummaryResponse(0, null, null, null, null, null));
        }

        @Test
        void getSummaryForCourse_withUnknownCourse_throwsCourseNotFoundWithoutQueryingReviews() {
            when(courseRepository.existsById("NOTFOUND")).thenReturn(false);

            assertThatThrownBy(() -> reviewService.getSummaryForCourse("NOTFOUND"))
                    .isInstanceOf(CourseNotFoundException.class)
                    .hasMessageContaining("NOTFOUND");

            verifyNoInteractions(reviewRepository);
        }
    }

    @Nested
    class UpsertReview {

        @Test
        void upsertReview_withNoExistingReview_savesNewReviewBuiltFromRequest() {
            ReviewRequest request = new ReviewRequest(4, 3, 5, 2, true, "Great course!");
            when(courseRepository.existsById(COURSE_CODE)).thenReturn(true);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(USER));
            when(reviewRepository.findByUserIdAndCourse_Code(USER_ID, COURSE_CODE)).thenReturn(Optional.empty());
            when(courseRepository.getReferenceById(COURSE_CODE)).thenReturn(COURSE);

            ReviewResponse response = reviewService.upsertReview(USER_ID, COURSE_CODE, request);

            Review saved = captureSavedReview();
            assertThat(saved.getUser()).isSameAs(USER);
            assertThat(saved.getCourse()).isSameAs(COURSE);
            assertThat(saved.getWorkloadRating()).isEqualTo(4);
            assertThat(saved.getDifficultyRating()).isEqualTo(3);
            assertThat(saved.getEnjoymentRating()).isEqualTo(5);
            assertThat(saved.getLecturerRating()).isEqualTo(2);
            assertThat(saved.getWouldRecommend()).isTrue();
            assertThat(saved.getBody()).isEqualTo("Great course!");

            assertThat(response.reviewerId()).isEqualTo(USER_ID);
            assertThat(response.workloadRating()).isEqualTo(4);
            assertThat(response.difficultyRating()).isEqualTo(3);
            assertThat(response.enjoymentRating()).isEqualTo(5);
            assertThat(response.lecturerRating()).isEqualTo(2);
            assertThat(response.wouldRecommend()).isTrue();
            assertThat(response.body()).isEqualTo("Great course!");
        }

        @Test
        void upsertReview_withNullBody_savesReviewWithNullBody() {
            ReviewRequest request = new ReviewRequest(4, 3, 5, 4, true, null);
            when(courseRepository.existsById(COURSE_CODE)).thenReturn(true);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(USER));
            when(reviewRepository.findByUserIdAndCourse_Code(USER_ID, COURSE_CODE)).thenReturn(Optional.empty());
            when(courseRepository.getReferenceById(COURSE_CODE)).thenReturn(COURSE);

            ReviewResponse response = reviewService.upsertReview(USER_ID, COURSE_CODE, request);

            assertThat(captureSavedReview().getBody()).isNull();
            assertThat(response.body()).isNull();
        }

        @Test
        void upsertReview_withExistingReview_updatesItInPlaceInsteadOfCreatingANewOne() {
            Review existing = persistedReview(USER, 4, 3, 5, 4, true, "Great!");
            OffsetDateTime originalCreatedAt = existing.getCreatedAt();
            ReviewRequest request = new ReviewRequest(1, 2, 3, 1, false, "Updated!");
            when(courseRepository.existsById(COURSE_CODE)).thenReturn(true);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(USER));
            when(reviewRepository.findByUserIdAndCourse_Code(USER_ID, COURSE_CODE)).thenReturn(Optional.of(existing));

            ReviewResponse response = reviewService.upsertReview(USER_ID, COURSE_CODE, request);

            assertThat(captureSavedReview()).isSameAs(existing);
            verify(courseRepository, never()).getReferenceById(any());
            assertThat(existing.getWorkloadRating()).isEqualTo(1);
            assertThat(existing.getDifficultyRating()).isEqualTo(2);
            assertThat(existing.getEnjoymentRating()).isEqualTo(3);
            assertThat(existing.getLecturerRating()).isEqualTo(1);
            assertThat(existing.getWouldRecommend()).isFalse();
            assertThat(existing.getBody()).isEqualTo("Updated!");

            assertThat(response.workloadRating()).isEqualTo(1);
            assertThat(response.wouldRecommend()).isFalse();
            assertThat(response.body()).isEqualTo("Updated!");
            assertThat(response.createdAt()).isEqualTo(originalCreatedAt);
        }

        @Test
        void upsertReview_withUnknownCourse_throwsCourseNotFoundAndSavesNothing() {
            ReviewRequest request = new ReviewRequest(4, 3, 5, 4, true, "Great!");
            when(courseRepository.existsById("NOTFOUND")).thenReturn(false);

            assertThatThrownBy(() -> reviewService.upsertReview(USER_ID, "NOTFOUND", request))
                    .isInstanceOf(CourseNotFoundException.class)
                    .hasMessageContaining("NOTFOUND");

            verifyNoInteractions(userRepository, reviewRepository);
        }

        @Test
        void upsertReview_withUnknownUser_throwsUserNotFoundAndSavesNothing() {
            ReviewRequest request = new ReviewRequest(4, 3, 5, 4, true, "Great!");
            when(courseRepository.existsById(COURSE_CODE)).thenReturn(true);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.upsertReview(USER_ID, COURSE_CODE, request))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(USER_ID.toString());

            verify(reviewRepository, never()).save(any());
        }
    }

    @Nested
    class DeleteReview {

        @Test
        void deleteReview_withExistingReview_deletesIt() {
            Review existing = persistedReview(USER, 4, 3, 5, 4, true, "Great!");
            when(courseRepository.existsById(COURSE_CODE)).thenReturn(true);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(USER));
            when(reviewRepository.findByUserIdAndCourse_Code(USER_ID, COURSE_CODE)).thenReturn(Optional.of(existing));

            reviewService.deleteReview(USER_ID, COURSE_CODE);

            verify(reviewRepository).delete(existing);
        }

        @Test
        void deleteReview_withUnknownCourse_throwsCourseNotFoundWithoutQueryingReviews() {
            when(courseRepository.existsById("NOTFOUND")).thenReturn(false);

            assertThatThrownBy(() -> reviewService.deleteReview(USER_ID, "NOTFOUND"))
                    .isInstanceOf(CourseNotFoundException.class)
                    .hasMessageContaining("NOTFOUND");

            verifyNoInteractions(reviewRepository);
        }

        @Test
        void deleteReview_withUnknownUser_throwsUserNotFoundAndDeletesNothing() {
            when(courseRepository.existsById(COURSE_CODE)).thenReturn(true);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.deleteReview(USER_ID, COURSE_CODE))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(USER_ID.toString());

            verifyNoInteractions(reviewRepository);
        }

        @Test
        void deleteReview_withNoReview_throwsReviewNotFoundAndDeletesNothing() {
            when(courseRepository.existsById(COURSE_CODE)).thenReturn(true);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(USER));
            when(reviewRepository.findByUserIdAndCourse_Code(USER_ID, COURSE_CODE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.deleteReview(USER_ID, COURSE_CODE))
                    .isInstanceOf(ReviewNotFoundException.class)
                    .hasMessageContaining(USER_ID.toString())
                    .hasMessageContaining(COURSE_CODE);

            verify(reviewRepository, never()).delete(any());
        }
    }

    // Internal Helpers

    private User otherUser() {
        User user = new User(TestFixtures.uniqueEdinburghEmail());
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    /** A review as it would look after being loaded from the DB (id and createdAt populated). */
    private Review persistedReview(User user, int workload, int difficulty, int enjoyment, int lecturer,
                                   boolean wouldRecommend, String body) {
        Review review = new Review(user, COURSE, workload, difficulty, enjoyment, lecturer, wouldRecommend, body);
        ReflectionTestUtils.setField(review, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(review, "createdAt", OffsetDateTime.now());
        return review;
    }

    private void stubReviewsForCourse(Review... reviews) {
        when(courseRepository.existsById(COURSE_CODE)).thenReturn(true);
        when(reviewRepository.findByCourse_CodeOrderByCreatedAtDesc(COURSE_CODE))
                .thenReturn(List.of(reviews));
    }

    private Review captureSavedReview() {
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(captor.capture());
        return captor.getValue();
    }
}
