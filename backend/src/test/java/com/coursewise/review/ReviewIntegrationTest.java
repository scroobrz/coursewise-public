package com.coursewise.review;

import com.coursewise.TestFixtures;
import com.coursewise.TestcontainersConfiguration;
import com.coursewise.auth.dto.RegisterRequest;
import com.coursewise.auth.dto.VerifyEmailRequest;
import com.coursewise.course.Course;
import com.coursewise.course.CourseRepository;
import com.coursewise.review.dto.ReviewRequest;
import com.coursewise.review.dto.ReviewResponse;
import com.coursewise.review.dto.ReviewSummaryResponse;

import org.springframework.core.ParameterizedTypeReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.coursewise.auth.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
@TestPropertySource(properties = {"management.health.mail.enabled=false"})
class ReviewIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private RestClient.Builder restClientBuilder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CourseRepository courseRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReviewRepository reviewRepository;
    @MockitoBean  private JavaMailSender mailSender;

    private String COURSE_CODE;
    private String EMAIL;
    private final String PASSWORD = "Password123";

    @BeforeEach
    void seedCourse() {
        //cleanup first
        jdbcTemplate.execute("TRUNCATE TABLE users, magic_links, courses CASCADE");

        COURSE_CODE = TestFixtures.uniqueCourseCode("INFR");
        EMAIL = TestFixtures.uniqueEdinburghEmail();

        Course course = new Course();
        ReflectionTestUtils.setField(course, "code", COURSE_CODE);
        ReflectionTestUtils.setField(course, "title", "course title");
        courseRepository.save(course);
    }

    @Nested
    class GetReviews {

        @Test
        void getReviews_returnsAllReviewResponsesInCreationOrder() {
            // One review per user+course, so each seeded review needs its own user.
            seedReview(seedUser(), new ReviewRequest(4, 3, 5, 4, true, "Great course!"), 30);
            seedReview(seedUser(), new ReviewRequest(5, 4, 2, 3, false, "Avoid this course!"), 20);
            seedReview(seedUser(), new ReviewRequest(3, 2, 3, 3, false, "It's okay"), 10);

            ResponseEntity<List<ReviewResponse>> response = getReviews(COURSE_CODE);

            List<ReviewResponse> reviews = response.getBody();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(reviews).hasSize(3);
            assertThat(reviews.get(0).workloadRating()).isEqualTo(3);
            assertThat(reviews.get(0).difficultyRating()).isEqualTo(2);
            assertThat(reviews.get(0).enjoymentRating()).isEqualTo(3);
            assertThat(reviews.get(0).lecturerRating()).isEqualTo(3);
            assertThat(reviews.get(0).wouldRecommend()).isFalse();
            assertThat(reviews.get(0).body()).isEqualTo("It's okay");
            assertThat(reviews.get(1).workloadRating()).isEqualTo(5);
            assertThat(reviews.get(1).difficultyRating()).isEqualTo(4);
            assertThat(reviews.get(1).enjoymentRating()).isEqualTo(2);
            assertThat(reviews.get(1).lecturerRating()).isEqualTo(3);
            assertThat(reviews.get(1).wouldRecommend()).isFalse();
            assertThat(reviews.get(1).body()).isEqualTo("Avoid this course!");
            assertThat(reviews.get(2).workloadRating()).isEqualTo(4);
            assertThat(reviews.get(2).difficultyRating()).isEqualTo(3);
            assertThat(reviews.get(2).enjoymentRating()).isEqualTo(5);
            assertThat(reviews.get(2).lecturerRating()).isEqualTo(4);
            assertThat(reviews.get(2).wouldRecommend()).isTrue();
            assertThat(reviews.get(2).body()).isEqualTo("Great course!");
        }

        @Test
        void getReviews_whenNoReviews_returns200WithEmptyList() {
            ResponseEntity<List<ReviewResponse>> response = getReviews(COURSE_CODE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }

        @Test 
        void getReviews_withUnkownCourse_returnsNotFound(){
            assertThatThrownBy(() -> getReviews("NOTFOUND"))
                    .isInstanceOf(HttpClientErrorException.NotFound.class);
        }

        @Test
        void getReviews_withoutLogin_isAllowed() {
            ResponseEntity<List<ReviewResponse>> response = getReviews(COURSE_CODE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    class GetSummary {

        @Test
        void getSummary_returnsSummaryResponseWithCorrectAverages() {
            // One review per user+course, so each seeded review needs its own user.
            seedReview(seedUser(), new ReviewRequest(4, 3, 5, 4, true, "Great course!"), 30);
            seedReview(seedUser(), new ReviewRequest(5, 4, 2, 3, false, "Avoid this course!"), 20);
            seedReview(seedUser(), new ReviewRequest(3, 2, 3, 3, false, "It's okay"), 10);

            ResponseEntity<ReviewSummaryResponse> response = getSummary(COURSE_CODE);

            ReviewSummaryResponse summary = response.getBody();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(summary).isNotNull();
            assertThat(summary.reviewCount()).isEqualTo(3);
            assertThat(summary.avgWorkload()).isEqualTo(4.0);
            assertThat(summary.avgDifficulty()).isEqualTo(3.0);
            assertThat(summary.avgEnjoyment()).isEqualTo(3.33);
            assertThat(summary.avgLecturer()).isEqualTo(3.33);
            assertThat(summary.recommendPercent()).isEqualTo(33.33);
        }

        @Test 
        void getSummary_withUnkownCourse_returnsNotFound(){
            assertThatThrownBy(() -> getSummary("NOTFOUND"))
                    .isInstanceOf(HttpClientErrorException.NotFound.class);
        }

        @Test
        void getSummary_whenNoReviews_returns200AndSummaryResponseWithZeroCount() {
            ResponseEntity<ReviewSummaryResponse> response = getSummary(COURSE_CODE);

            ReviewSummaryResponse summary = response.getBody();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(summary).isNotNull();
            assertThat(summary.reviewCount()).isZero();
            assertThat(summary.avgWorkload()).isNull();
            assertThat(summary.avgDifficulty()).isNull();
            assertThat(summary.avgEnjoyment()).isNull();
            assertThat(summary.avgLecturer()).isNull();
            assertThat(summary.recommendPercent()).isNull();
        }
    }

    @Nested
    class SubmitReview {

        @Test
        void submitReview_whenAuthenticated_returnsCreatedAndSavesReviewAndReturnsReviewResponse() {
            String session = registerAndVerifyAndGetSession(EMAIL, PASSWORD);
            UUID userId = userIdFor(EMAIL);
            
            ReviewRequest review = new ReviewRequest(4, 3, 5, 4, true, "Great course!");
            ResponseEntity<ReviewResponse> response = submitReview(session, review, COURSE_CODE);

            Review saved = reviewRepository.findByUserIdAndCourse_Code(userId, COURSE_CODE).orElseThrow();
            assertThat(saved.getWorkloadRating()).isEqualTo(4);
            assertThat(saved.getDifficultyRating()).isEqualTo(3);
            assertThat(saved.getEnjoymentRating()).isEqualTo(5);
            assertThat(saved.getLecturerRating()).isEqualTo(4);
            assertThat(saved.getWouldRecommend()).isTrue();
            assertThat(saved.getBody()).isEqualTo("Great course!");

            ReviewResponse body = response.getBody();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(body).isNotNull();
            assertThat(body.workloadRating()).isEqualTo(4);
            assertThat(body.difficultyRating()).isEqualTo(3);
            assertThat(body.enjoymentRating()).isEqualTo(5);
            assertThat(body.lecturerRating()).isEqualTo(4);
            assertThat(body.wouldRecommend()).isTrue();
            assertThat(body.body()).isEqualTo("Great course!");
        }

        @Test
        void submitReview_acceptsEmptyReviewBody() {
            String session = registerAndVerifyAndGetSession(EMAIL, PASSWORD);
            UUID userId = userIdFor(EMAIL);

            ReviewRequest nullBodyRequest = new ReviewRequest(4, 3, 5, 4, true, null);
            ResponseEntity<ReviewResponse> response = submitReview(session, nullBodyRequest, COURSE_CODE);

            Review saved = reviewRepository.findByUserIdAndCourse_Code(userId, COURSE_CODE).orElseThrow();
            assertThat(saved.getBody()).isNull();

            ReviewResponse body = response.getBody();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(body).isNotNull();
            assertThat(body.body()).isNull();
        }

        @Test
        void submitReview_twice_updatesExistingReview() {
            String session = registerAndVerifyAndGetSession(EMAIL, PASSWORD);

            ReviewRequest initial = new ReviewRequest(4, 3, 5, 4, true, "Great course!");
            submitReview(session, initial, COURSE_CODE);
            ReviewRequest updated = new ReviewRequest(5, 3, 5, 4, true, "Updated body");
            submitReview(session, updated, COURSE_CODE);

            ResponseEntity<List<ReviewResponse>> response = getReviews(COURSE_CODE);
            
            List<Review> saved = reviewRepository.findByCourse_CodeOrderByCreatedAtDesc(COURSE_CODE);
            assertThat(saved).hasSize(1);
            assertThat(saved.get(0).getWorkloadRating()).isEqualTo(5);
            assertThat(saved.get(0).getBody()).isEqualTo("Updated body");

            List<ReviewResponse> reviews = response.getBody();
            assertThat(reviews).hasSize(1);
            assertThat(reviews.get(0).workloadRating()).isEqualTo(5);
            assertThat(reviews.get(0).body()).isEqualTo("Updated body");
        }

        @ParameterizedTest
        @MethodSource("com.coursewise.TestParameters#invalidReviewRequests")
        void submitReview_withInvalidRating_returns400(ReviewRequest badRequest) {
            String session = registerAndVerifyAndGetSession(EMAIL, PASSWORD);
            assertThatThrownBy(() -> 
                    submitReview(
                        session,
                        badRequest,
                        COURSE_CODE))
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }

        @Test
        void submitReview_withUnknownCourse_returnsNotFound(){
            String session = registerAndVerifyAndGetSession(EMAIL, PASSWORD);
            assertThatThrownBy(() -> 
                    submitReview(
                        session,
                        new ReviewRequest(4, 3, 5, 4, true, "Great course!"),
                        "NOTFOUND"))
                    .isInstanceOf(HttpClientErrorException.NotFound.class);
        }

        @Test
        void submitReview_unauthenticated_returns401() {
            assertThatThrownBy(() ->
                    submitReview(new ReviewRequest(4, 3, 5, 4, true, "Great course!"), COURSE_CODE))
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        }

        @Test
        void submitReview_whenSessionUserDeleted_returns401AndSavesNothing() {
            String session = registerAndVerifyAndGetSession(EMAIL, PASSWORD);
            deleteUser(EMAIL);

            assertThatThrownBy(() -> submitReview(session, new ReviewRequest(4, 3, 5, 4, true, "Great course!"), COURSE_CODE))
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class);
            assertThat(reviewRepository.findByCourse_CodeOrderByCreatedAtDesc(COURSE_CODE)).isEmpty();
        }
    }

    @Nested
    class DeleteReview {

        @Test
        void deleteReview_removesReviewFromDatabase() {
            String session = registerAndVerifyAndGetSession(EMAIL, PASSWORD);
            seedReview(userIdFor(EMAIL), new ReviewRequest(4, 3, 5, 4, true, "Great course!"), 10);
            List<Review> saved = reviewRepository.findByCourse_CodeOrderByCreatedAtDesc(COURSE_CODE);
            assertThat(saved).hasSize(1);

            ResponseEntity<Void> deleteResponse = deleteReview(session, COURSE_CODE);

            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            saved = reviewRepository.findByCourse_CodeOrderByCreatedAtDesc(COURSE_CODE);
            assertThat(saved).hasSize(0);
        }

        @Test
        void deleteReview_withUnknownCourse_returnsNotFound(){
            String session = registerAndVerifyAndGetSession(EMAIL, PASSWORD);
            assertThatThrownBy(() -> deleteReview(session, "NOTFOUND"))
                    .isInstanceOf(HttpClientErrorException.NotFound.class);
        }

        @Test
        void deleteReview_withNoReviewForKnownCourse_returnsNotFound(){
            String session = registerAndVerifyAndGetSession(EMAIL, PASSWORD);
            assertThatThrownBy(() -> deleteReview(session, COURSE_CODE))
                    .isInstanceOf(HttpClientErrorException.NotFound.class);
        }

        @Test
        void deleteReview_unauthenticated_returns401(){
            assertThatThrownBy(() -> deleteReview(COURSE_CODE))
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        }

        @Test
        void deleteReview_whenSessionUserDeleted_returns401() {
            String session = registerAndVerifyAndGetSession(EMAIL, PASSWORD);
            deleteUser(EMAIL);

            assertThatThrownBy(() -> deleteReview(session, COURSE_CODE))
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        }
    }

    // --- Helpers ---

    private void deleteUser(String email) {
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
    }

    private RestClient client() {
        return restClientBuilder.baseUrl("http://localhost:" + port).build();
    }

    private String registerAndVerifyAndGetSession(String email, String password) {
        registerUser(email, password);
        String token = latestUnusedTokenFor(email);
        ResponseEntity<Void> response = client().post().uri("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VerifyEmailRequest(token))
                .retrieve()
                .toBodilessEntity();

        return TestFixtures.extractCookie(response, "JSESSIONID");
    }

    private UUID seedUser() {
        String email = TestFixtures.uniqueEdinburghEmail();
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, student_number, email_verified) VALUES (?, ?, ?, TRUE)",
                id, email, email.substring(0, email.indexOf('@')));
        return id;
    }

    private void seedReview(UUID userId, ReviewRequest review, int minutesAgo) {
        jdbcTemplate.update("""
                INSERT INTO reviews (user_id, course_code, workload_rating, difficulty_rating,
                                     enjoyment_rating, lecturer_rating, would_recommend, body, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now() - make_interval(mins => ?), now() - make_interval(mins => ?))
                """,
                userId, COURSE_CODE, review.workloadRating(), review.difficultyRating(), review.enjoymentRating(),
                review.lecturerRating(), review.wouldRecommend(), review.body(), minutesAgo, minutesAgo);
    }

    private void registerUser(String email, String password) {
        client().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, password))
                .retrieve()
                .toBodilessEntity();
    }

    private String latestUnusedTokenFor(String email) {
        SimpleMailMessage message = Mockito.mockingDetails(mailSender).getInvocations().stream()
                .map(invocation -> (SimpleMailMessage) invocation.getArgument(0))
                .filter(m -> Arrays.asList(m.getTo()).contains(email))
                .reduce((first, second) -> second)
                .orElseThrow();

        Matcher matcher = Pattern.compile("token=(\\S+)").matcher(message.getText());
        if (!matcher.find()) {
            throw new IllegalStateException("No token found in email body");
        }
        return matcher.group(1);
    }

    private UUID userIdFor(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private ResponseEntity<List<ReviewResponse>> getReviews(String courseCode) {
        return client().get()
                .uri("/api/v1/courses/{code}/reviews", courseCode)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<ReviewResponse>>() {});
    }

    private ResponseEntity<ReviewSummaryResponse> getSummary(String courseCode) {
        return client().get()
                .uri("/api/v1/courses/{code}/reviews/summary", courseCode)
                .retrieve()
                .toEntity(ReviewSummaryResponse.class);
    }

    private ResponseEntity<ReviewResponse> submitReview(String session, ReviewRequest request, String courseCode) {
        return client().post()
                    .uri("/api/v1/courses/{code}/reviews", courseCode)
                    .header(HttpHeaders.COOKIE, session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(ReviewResponse.class);
    }

    // sends an unauthenticated submit request
    private ResponseEntity<ReviewResponse> submitReview(ReviewRequest request, String courseCode) {
        return client().post()
                    .uri("/api/v1/courses/{code}/reviews", courseCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(ReviewResponse.class);
    }

    private ResponseEntity<Void> deleteReview(String session, String courseCode) {
        return client().delete()
                .uri("/api/v1/courses/{code}/reviews", courseCode)
                .header(HttpHeaders.COOKIE, session)
                .retrieve()
                .toBodilessEntity();
    }

    // sends an unauthenticated delete request
    private ResponseEntity<Void> deleteReview(String courseCode) {
        return client().delete()
                .uri("/api/v1/courses/{code}/reviews", courseCode)
                .retrieve()
                .toBodilessEntity();
    }
}
