package com.coursewise.course;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coursewise.course.dto.CourseResponse;
import com.coursewise.course.exception.CourseNotFoundException;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseService courseService;

    private Course course(String code, String title) {
        Course course = new Course();
        ReflectionTestUtils.setField(course, "code", code);
        ReflectionTestUtils.setField(course, "title", title);
        return course;
    }

    @Nested
    class ListCourses {

        @Test
        void listCourses_returnsAllCoursesMappedToDtosInTheOrderTheRepositoryGivesThem() {
            when(courseRepository.findAll(Sort.by("code"))).thenReturn(List.of(
                course("INFR10069", "Informatics 1"),
                course("INFR10070", "Informatics 2"),
                course("INFR11159", "Algorithms and Data Structures"),
                course("MATH08057", "Linear Algebra")
            ));

            List<CourseResponse> result = courseService.listCourses();

            assertThat(result).hasSize(4);
            assertThat(result).extracting(CourseResponse::code)
                    .containsExactly("INFR10069", "INFR10070", "INFR11159", "MATH08057");
            assertThat(result).extracting(CourseResponse::title)
                    .containsExactly("Informatics 1", "Informatics 2", "Algorithms and Data Structures", "Linear Algebra");
        }

        @Test
        void listCourses_returnsEmptyList_whenNoCourses() {
            when(courseRepository.findAll(Sort.by("code"))).thenReturn(List.of());

            assertThat(courseService.listCourses()).isEmpty();
        }
    }

    @Nested
    class GetCourse {

        @Test
        void getCourse_found_returnsCourseMappedToDto() {
            when(courseRepository.findById("INFR10069"))
            .thenReturn(Optional.of(course("INFR10069", "Informatics 1")));

            CourseResponse result = courseService.getCourse("INFR10069");

            assertThat(result.code()).isEqualTo("INFR10069");
            assertThat(result.title()).isEqualTo("Informatics 1");
        }

        @ParameterizedTest
        @ValueSource(strings = {"INFR10069", "infr10069", "Infr10069"})
        void getCourse_codeIsCaseInsensitive(String code) {
            when(courseRepository.findById("INFR10069"))
            .thenReturn(Optional.of(course("INFR10069", "Informatics 1")));

            CourseResponse result = courseService.getCourse(code);

            assertThat(result.code()).isEqualTo("INFR10069");
            assertThat(result.title()).isEqualTo("Informatics 1");
        }

        @Test
        void getCourse_notFound_throwsCourseNotFoundException() {
            when(courseRepository.findById("NOTFOUND")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.getCourse("NOTFOUND"))
                    .isInstanceOf(CourseNotFoundException.class)
                    .hasMessageContaining("NOTFOUND");
        }
    }
}
