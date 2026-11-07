package com.coursewise.course;

import com.coursewise.config.GlobalExceptionHandler;
import com.coursewise.config.SecurityConfig;
import com.coursewise.course.dto.CourseResponse;
import com.coursewise.course.exception.CourseNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        CourseControllerWebTest.MvcConfig.class,
        CourseController.class,
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
@WebAppConfiguration
class CourseControllerWebTest {

    private static final String COURSES = "/api/v1/courses";

    @TestConfiguration
    @EnableWebMvc
    static class MvcConfig {
    }

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private CourseService courseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Nested
    class ListCourses {

        @Test
        void listCourses_returns200WithCoursesInTheOrderTheServiceGivesThem() throws Exception {
            when(courseService.listCourses()).thenReturn(List.of(
                    new CourseResponse("INFR10069", "Informatics 1"),
                    new CourseResponse("MATH08057", "Linear Algebra"),
                    new CourseResponse("INFR11159", "Algorithms and Data Structures"),
                    new CourseResponse("INFR10070", "Informatics 2")));

            mockMvc.perform(get(COURSES))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(4)))
                    .andExpect(jsonPath("$[0].code").value("INFR10069"))
                    .andExpect(jsonPath("$[0].title").value("Informatics 1"))
                    .andExpect(jsonPath("$[1].code").value("MATH08057"))
                    .andExpect(jsonPath("$[1].title").value("Linear Algebra"))
                    .andExpect(jsonPath("$[2].code").value("INFR11159"))
                    .andExpect(jsonPath("$[2].title").value("Algorithms and Data Structures"))
                    .andExpect(jsonPath("$[3].code").value("INFR10070"))
                    .andExpect(jsonPath("$[3].title").value("Informatics 2"));
        }

        @Test
        void listCourses_whenNoCourses_returns200WithEmptyArray() throws Exception {
            when(courseService.listCourses()).thenReturn(List.of());

            mockMvc.perform(get(COURSES))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void listCourses_exposesOnlyCodeAndTitle() throws Exception {
            when(courseService.listCourses())
                    .thenReturn(List.of(new CourseResponse("INFR10069", "Informatics 1")));

            mockMvc.perform(get(COURSES))
                    .andExpect(jsonPath("$[0].*", hasSize(2)));
        }

        @Test
        void listCourses_withoutLogin_isAllowed() throws Exception {
            mockMvc.perform(get(COURSES))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class GetCourse {

        @Test
        void getCourse_withKnownCode_returns200WithCourse() throws Exception {
            when(courseService.getCourse("INFR10069"))
                    .thenReturn(new CourseResponse("INFR10069", "Informatics 1"));

            mockMvc.perform(get(COURSES + "/INFR10069"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("INFR10069"))
                    .andExpect(jsonPath("$.title").value("Informatics 1"));
        }

        @Test
        void getCourse_withUnknownCode_returns404() throws Exception {
            when(courseService.getCourse("NOPE")).thenThrow(new CourseNotFoundException("NOPE"));

            mockMvc.perform(get(COURSES + "/NOPE"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.title").value("Course Not Found"))
                    .andExpect(jsonPath("$.detail").value("Course not found: NOPE"))
                    .andExpect(jsonPath("$.type").value("https://coursewise.app/errors/not-found"));
        }

        @Test
        void getCourse_withoutLogin_isAllowed() throws Exception {
            when(courseService.getCourse("INFR10069"))
                    .thenReturn(new CourseResponse("INFR10069", "Informatics 1"));

            mockMvc.perform(get(COURSES + "/INFR10069"))
                    .andExpect(status().isOk());
        }
    }
}
