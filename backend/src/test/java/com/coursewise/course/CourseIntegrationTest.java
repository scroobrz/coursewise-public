package com.coursewise.course;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.junit.jupiter.api.AfterEach;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

import com.coursewise.TestcontainersConfiguration;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class CourseIntegrationTest {

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("TRUNCATE TABLE users, magic_links, courses CASCADE");
    }

    private Course course(String code, String title) {
        Course course = new Course();
        ReflectionTestUtils.setField(course, "code", code);
        ReflectionTestUtils.setField(course, "title", title);
        return course;
    }

    @Nested
    class ListCourses {

        @Test
        void listCourses_returnsAllCourses() {
            insertCourses(List.of(
                course("INFR10069", "Informatics 1"),
                course("INFR10070", "Informatics 2"),
                course("INFR11159", "Algorithms and Data Structures"),
                course("MATH08057", "Linear Algebra")
            ));

            restTestClient.get().uri("/api/v1/courses")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].code").isEqualTo("INFR10069")
                    .jsonPath("$[0].title").isEqualTo("Informatics 1")
                    .jsonPath("$[1].code").isEqualTo("INFR10070")
                    .jsonPath("$[1].title").isEqualTo("Informatics 2")
                    .jsonPath("$[2].code").isEqualTo("INFR11159")
                    .jsonPath("$[2].title").isEqualTo("Algorithms and Data Structures")
                    .jsonPath("$[3].code").isEqualTo("MATH08057")
                    .jsonPath("$[3].title").isEqualTo("Linear Algebra");
        }

        @Test
        void listCourses_returnsAllCoursesSortedByCode_regardlessOfInsertionOrder() {
            insertCourse("MATH08057", "Linear Algebra");
            insertCourse("INFR10069", "Informatics 1");
            insertCourse("INFR11159", "Algorithms and Data Structures");
            insertCourse("INFR10070", "Informatics 2");

            restTestClient.get().uri("/api/v1/courses")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[0].code").isEqualTo("INFR10069")
                    .jsonPath("$[0].title").isEqualTo("Informatics 1")
                    .jsonPath("$[1].code").isEqualTo("INFR10070")
                    .jsonPath("$[1].title").isEqualTo("Informatics 2")
                    .jsonPath("$[2].code").isEqualTo("INFR11159")
                    .jsonPath("$[2].title").isEqualTo("Algorithms and Data Structures")
                    .jsonPath("$[3].code").isEqualTo("MATH08057")
                    .jsonPath("$[3].title").isEqualTo("Linear Algebra");
        }

        @Test
        void listCourses_whenNoCourses_returns200WithEmptyArray() {
            restTestClient.get().uri("/api/v1/courses")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").isEmpty();
        }

        @Test
        void listCourses_withoutLogin_isAllowed() {
            restTestClient.get().uri("/api/v1/courses")
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Nested
    class GetCourse {

        @Test
        void getCourse_withKnownCode_returns200WithCourse() {
            insertCourse("INFR10069", "Informatics 1");

            restTestClient.get().uri("/api/v1/courses/" + "INFR10069")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("INFR10069")
                    .jsonPath("$.title").isEqualTo("Informatics 1");
        }

        @ParameterizedTest
        @ValueSource(strings = {"INFR10069", "infr10069", "Infr10069"})
        void getCourse_withAnyLetterCase_returnsTheStoredCourse(String code) {
            insertCourse("INFR10069", "Informatics 1");

            restTestClient.get().uri("/api/v1/courses/" + code)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("INFR10069")
                    .jsonPath("$.title").isEqualTo("Informatics 1");
        }

        @Test
        void getCourse_withUnknownCode_returns404() {
            restTestClient.get().uri("/api/v1/courses/" + "NOPE")
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        void getCourse_withoutLogin_isAllowed() {
            insertCourse("INFR10069", "Informatics 1");
            restTestClient.get().uri("/api/v1/courses/" + "INFR10069")
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    // ── Database Helpers ──────────────────────────────────────────────────────────────

    private void insertCourse(String code, String title) {
        jdbcTemplate.update("INSERT INTO courses (code, title) VALUES (?, ?)", code, title);
    }

    private void insertCourses(List<Course> courses) {
        for (Course course : courses) {
            insertCourse(course.getCode(), course.getTitle());
        }
    }
}
