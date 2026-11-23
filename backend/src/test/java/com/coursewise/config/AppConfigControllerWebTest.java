package com.coursewise.config;

import com.coursewise.config.AppConfigResponse.ValidationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        AppConfigControllerWebTest.MvcConfig.class,
        AppConfigController.class,
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
@WebAppConfiguration
class AppConfigControllerWebTest {

    static final int    TEST_TRUSTED_DEVICE_DAYS   = 30;
    static final int    TEST_RESEND_COOLDOWN_SECS   = 60;
    static final int    TEST_MAGIC_LINK_EXPIRY_MINS = 15;
    static final String TEST_FRONTEND_URL           = "http://localhost:4200";
    static final String TEST_FROM_ADDRESS           = "noreply@coursewise.com";
    static final String TEST_EMAIL_REGEX            = "^[sS]\\d{7}@ed\\.ac\\.uk$";
    static final int    TEST_PASSWORD_MIN           = 8;
    static final int    TEST_PASSWORD_MAX           = 128;
    static final String TEST_PASSWORD_REGEX         = "^(?=.*[A-Z])(?=.*[^a-zA-Z]).+$";
    static final String TEST_PASSWORD_REGEX_DESC    = "Must contain at least one uppercase letter and one non-alphabetic character";

    static final AppConfigProperties TEST_CONFIG = new AppConfigProperties(
            true, TEST_FRONTEND_URL, TEST_FROM_ADDRESS,
            new AppConfigResponse(
                    TEST_TRUSTED_DEVICE_DAYS, TEST_RESEND_COOLDOWN_SECS, TEST_MAGIC_LINK_EXPIRY_MINS,
                    new ValidationProperties(
                            TEST_EMAIL_REGEX, TEST_PASSWORD_MIN, TEST_PASSWORD_MAX,
                            TEST_PASSWORD_REGEX, TEST_PASSWORD_REGEX_DESC
                    )
            )
    );

    @TestConfiguration
    @EnableWebMvc
    static class MvcConfig {

        @Bean
        public AppConfigProperties appConfig() {
            return TEST_CONFIG;
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Nested
    class GetConfig {

        /*
        Expected values are the hand-written TEST_* constants, injected as the AppConfigProperties bean
        in MvcConfig. application.yml is never read here.

        This tests that, given an AppConfigProperties, the controller serves only its public/shared 
        values as JSON with the field names the frontend expects, and never exposes the backend-only 
        fields (cookieSecure, frontendUrl, fromAddress). It also tests that the endpoint is reachable 
        without a session under SecurityConfig. Whether the real application.yml binds into those 
        properties correctly is covered by AppConfigIntegrationTest.
         */
        @Test
        void getConfig_returns200WithPublicConfigWithoutBackendOnlyFields() throws Exception {
            mockMvc.perform(get("/api/v1/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.trustedDeviceDays").value(TEST_TRUSTED_DEVICE_DAYS))
                    .andExpect(jsonPath("$.resendCooldownSeconds").value(TEST_RESEND_COOLDOWN_SECS))
                    .andExpect(jsonPath("$.magicLinkExpiryMinutes").value(TEST_MAGIC_LINK_EXPIRY_MINS))
                    .andExpect(jsonPath("$.validation.emailRegex").value(TEST_EMAIL_REGEX))
                    .andExpect(jsonPath("$.validation.passwordMinLength").value(TEST_PASSWORD_MIN))
                    .andExpect(jsonPath("$.validation.passwordMaxLength").value(TEST_PASSWORD_MAX))
                    .andExpect(jsonPath("$.validation.passwordRegex").value(TEST_PASSWORD_REGEX))
                    .andExpect(jsonPath("$.validation.passwordRegexDescription").value(TEST_PASSWORD_REGEX_DESC))
                    // Backend-only fields must NOT be present in the response
                    .andExpect(jsonPath("$.cookieSecure").doesNotExist())
                    .andExpect(jsonPath("$.frontendUrl").doesNotExist())
                    .andExpect(jsonPath("$.fromAddress").doesNotExist());
        }
    }
}
