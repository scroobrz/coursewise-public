package com.coursewise.config;

import com.coursewise.TestcontainersConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class AppConfigIntegrationTest {

    @Autowired private RestTestClient restTestClient;
    @Autowired private Environment env;

    @Nested
    class GetConfig {

        /*
        Expected values are read from the raw coursewise.config.shared.* properties (application.yml).
        
        This tests that the application config system successfully reads from config.yml, binds the 
        values to its Spring Boot environment, and serves the public/shared values through the GET 
        /config endpoint
         */
        @Test
        void getConfig_withoutSession_returnsSharedConfigAndNoBackendOnlyFields() {
            restTestClient.get().uri("/api/v1/config")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.trustedDeviceDays").isEqualTo(intProp("trusted-device-days"))
                    .jsonPath("$.resendCooldownSeconds").isEqualTo(intProp("resend-cooldown-seconds"))
                    .jsonPath("$.magicLinkExpiryMinutes").isEqualTo(intProp("magic-link-expiry-minutes"))
                    .jsonPath("$.validation.emailRegex").isEqualTo(prop("validation.email-regex"))
                    .jsonPath("$.validation.passwordMinLength").isEqualTo(intProp("validation.password-min-length"))
                    .jsonPath("$.validation.passwordMaxLength").isEqualTo(intProp("validation.password-max-length"))
                    .jsonPath("$.validation.passwordRegex").isEqualTo(prop("validation.password-regex"))
                    .jsonPath("$.validation.passwordRegexDescription").isEqualTo(prop("validation.password-regex-description"))
                    // Backend-only fields must not leak, and no new field may appear unnoticed
                    .jsonPath("$.cookieSecure").doesNotExist()
                    .jsonPath("$.frontendUrl").doesNotExist()
                    .jsonPath("$.fromAddress").doesNotExist()
                    .jsonPath("$.length()").isEqualTo(4)
                    .jsonPath("$.validation.length()").isEqualTo(5);
        }
    }

    private String prop(String key) {
        return env.getRequiredProperty("coursewise.config.shared." + key);
    }

    private int intProp(String key) {
        return env.getRequiredProperty("coursewise.config.shared." + key, Integer.class);
    }
}
