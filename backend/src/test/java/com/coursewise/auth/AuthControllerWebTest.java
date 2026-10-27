package com.coursewise.auth;

import com.coursewise.auth.dto.LoginRequest;
import com.coursewise.auth.dto.RegisterRequest;
import com.coursewise.auth.dto.ResendVerificationRequest;
import com.coursewise.auth.dto.UpdateProfileRequest;
import com.coursewise.auth.dto.VerifyEmailRequest;
import com.coursewise.auth.exception.EmailAlreadyRegisteredException;
import com.coursewise.auth.exception.EmailNotVerifiedException;
import com.coursewise.auth.exception.InvalidCredentialsException;
import com.coursewise.auth.exception.InvalidTokenException;
import com.coursewise.auth.exception.ResendTooSoonException;
import com.coursewise.auth.validation.EmailValidator;
import com.coursewise.auth.validation.PasswordValidator;
import com.coursewise.config.AppConfigController;
import com.coursewise.config.AppConfigProperties;
import com.coursewise.config.AppConfigResponse;
import com.coursewise.config.AppConfigResponse.ValidationProperties;
import com.coursewise.config.GlobalExceptionHandler;
import com.coursewise.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        AuthControllerWebTest.MvcConfig.class,
        AuthController.class,
        AppConfigController.class,
        PasswordValidator.class,
        EmailValidator.class,
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
@WebAppConfiguration
class AuthControllerWebTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }


    @Nested
    class ConfigEndpoint {

        @Test
        void getConfig_returnsPublicConfigWithoutBackendOnlyFields() throws Exception {
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

    @Nested
    class RegisterEndpoint {

        @Test
        void register_withValidInput_returns201() throws Exception {
            RegisterRequest request = new RegisterRequest("s1234567@ed.ac.uk", "securePassword123");

            mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        void register_withInvalidEmail_returns400() throws Exception {
            RegisterRequest request = new RegisterRequest("not-an-email", "securePassword123");

            mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void register_withShortPassword_returns400() throws Exception {
            RegisterRequest request = new RegisterRequest("s1234567@ed.ac.uk", "short");

            mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void register_withDuplicateEmail_returns409() throws Exception {
            RegisterRequest request = new RegisterRequest("s1234567@ed.ac.uk", "securePassword123");
            doThrow(new EmailAlreadyRegisteredException())
                    .when(authService).register(anyString(), anyString());

            mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class VerifyEmailEndpoint {

        @Test
        void verifyEmail_withValidToken_returns200() throws Exception {
            VerifyEmailRequest request = new VerifyEmailRequest("valid-token");
            User mockUser = new User("s1234567@ed.ac.uk");
            ReflectionTestUtils.setField(mockUser, "id", UUID.randomUUID());

            when(authService.verifyRegistrationEmail(eq("valid-token"))).thenReturn(mockUser);

            mockMvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("s1234567@ed.ac.uk"));
        }

        @Test
        void verifyEmail_withInvalidToken_returns400() throws Exception {
            VerifyEmailRequest request = new VerifyEmailRequest("bad-token");
            doThrow(new InvalidTokenException("Invalid token"))
                    .when(authService).verifyRegistrationEmail("bad-token");

            mockMvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class LoginEndpoint {

        @Test
        void login_with2faRequired_returns200WithStatus() throws Exception {
            LoginRequest request = new LoginRequest("s1234567@ed.ac.uk", "Password123");
            when(authService.login(anyString(), anyString(), any())).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("2fa_required"))
                    .andExpect(jsonPath("$.user").doesNotExist());
        }

        @Test
        void login_withTrustedDevice_returns200WithUserInfo() throws Exception {
            LoginRequest request = new LoginRequest("s1234567@ed.ac.uk", "Password123");
            User mockUser = new User("s1234567@ed.ac.uk");
            ReflectionTestUtils.setField(mockUser, "id", UUID.randomUUID());

            when(authService.login(anyString(), anyString(), any())).thenReturn(Optional.of(mockUser));

            mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("authenticated"))
                    .andExpect(jsonPath("$.user.email").value("s1234567@ed.ac.uk"));
        }

        @Test
        void login_withWrongCredentials_returns401() throws Exception {
            LoginRequest request = new LoginRequest("s1234567@ed.ac.uk", "wrong");
            when(authService.login(anyString(), anyString(), any()))
                    .thenThrow(new InvalidCredentialsException());

            mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void login_withUnverifiedEmail_returns403() throws Exception {
            LoginRequest request = new LoginRequest("s1234567@ed.ac.uk", "Password123");
            when(authService.login(anyString(), anyString(), any()))
                    .thenThrow(new EmailNotVerifiedException());

            mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Verify2faEndpoint {

        @Test
        void verify2fa_withValidToken_returns200AndSetsSession() throws Exception {
            VerifyEmailRequest request = new VerifyEmailRequest("valid-token");
            User mockUser = new User("s1234567@ed.ac.uk");
            ReflectionTestUtils.setField(mockUser, "id", UUID.randomUUID());

            when(authService.verify2fa(eq("valid-token")))
                    .thenReturn(mockUser);

            mockMvc.perform(post("/api/v1/auth/verify-2fa").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("s1234567@ed.ac.uk"));
        }

        @Test
        void verify2fa_withInvalidToken_returns400() throws Exception {
            VerifyEmailRequest request = new VerifyEmailRequest("bad-token");
            doThrow(new InvalidTokenException("Invalid token"))
                    .when(authService).verify2fa(anyString());

            mockMvc.perform(post("/api/v1/auth/verify-2fa").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class ResendVerificationEndpoint {

        @Test
        void resendVerification_returns200_always() throws Exception {
            ResendVerificationRequest request = new ResendVerificationRequest("s1234567@ed.ac.uk");

            mockMvc.perform(post("/api/v1/auth/resend-verification").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        void resendVerification_withInvalidEmail_returns400() throws Exception {
            ResendVerificationRequest request = new ResendVerificationRequest("not-an-email");

            mockMvc.perform(post("/api/v1/auth/resend-verification").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void resendVerification_whenTooSoon_returns429() throws Exception {
            ResendVerificationRequest request = new ResendVerificationRequest("s1234567@ed.ac.uk");
            
            doThrow(new ResendTooSoonException())
                    .when(authService).resendVerification(anyString());

            mockMvc.perform(post("/api/v1/auth/resend-verification").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests());
        }
    }

    @Nested
    class ProtectedEndpoints {

        @Test
        void me_whenAuthenticated_returns200WithUserInfo() throws Exception {
            MockHttpSession session = new MockHttpSession();
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            UUID userId = UUID.randomUUID();
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
            securityContext.setAuthentication(auth);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

            User mockUser = new User("s1234567@ed.ac.uk");
            ReflectionTestUtils.setField(mockUser, "id", userId);
            when(authService.getUser(userId)).thenReturn(mockUser);

            mockMvc.perform(get("/api/v1/auth/me").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(userId.toString()))
                    .andExpect(jsonPath("$.email").value("s1234567@ed.ac.uk"));
        }

        @Test
        void me_whenNotAuthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void profile_whenAuthenticated_returns200WithUserInfo() throws Exception {
            MockHttpSession session = new MockHttpSession();
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            UUID userId = UUID.randomUUID();
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
            securityContext.setAuthentication(auth);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

            User mockUser = new User("s1234567@ed.ac.uk");
            ReflectionTestUtils.setField(mockUser, "id", userId);
            when(authService.updateProfile(eq(userId), any(), any())).thenReturn(mockUser);

            mockMvc.perform(patch("/api/v1/auth/profile").session(session).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateProfileRequest("John", "Doe"))).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(userId.toString()))
                    .andExpect(jsonPath("$.email").value("s1234567@ed.ac.uk"));
        }

        @Test
        void profile_whenNotAuthenticated_returns401() throws Exception {
            mockMvc.perform(patch("/api/v1/auth/profile").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateProfileRequest("John", "Doe"))).with(csrf()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void trustDevice_whenAuthenticated_setsCookie() throws Exception {
            MockHttpSession session = new MockHttpSession();
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            UUID userId = UUID.randomUUID();
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
            securityContext.setAuthentication(auth);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

            User mockUser = new User("s1234567@ed.ac.uk");
            ReflectionTestUtils.setField(mockUser, "id", userId);
            when(authService.getUser(userId)).thenReturn(mockUser);
            when(authService.createTrustedDevice(eq(mockUser), any(), any())).thenReturn("new-device-token");

            mockMvc.perform(post("/api/v1/auth/trust-device").with(csrf()).session(session))
                    .andExpect(status().isOk())
                    .andExpect(cookie().value("coursewise_td", "new-device-token"))
                    .andExpect(cookie().httpOnly("coursewise_td", true));
        }

        @Test
        void trustDevice_whenNotAuthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/trust-device").with(csrf()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void logout_clearsSession() throws Exception {
            MockHttpSession session = new MockHttpSession();
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            UUID userId = UUID.randomUUID();
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
            securityContext.setAuthentication(auth);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

            mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session))
                    .andExpect(status().isOk());
        }

        @Test
        void logout_whenNotAuthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout").with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class CsrfProtection {

        // Intentionally omitting .with(csrf())

        @Test
        void register_withoutCsrfToken_returns403() throws Exception {
            RegisterRequest request = new RegisterRequest("s1234567@ed.ac.uk", "securePassword123");

            mockMvc.perform(post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void verifyEmail_withoutCsrfToken_returns403() throws Exception {
            VerifyEmailRequest request = new VerifyEmailRequest("valid-token");

            mockMvc.perform(post("/api/v1/auth/verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void resendVerification_withoutCsrfToken_returns403() throws Exception {
            ResendVerificationRequest request = new ResendVerificationRequest("s1234567@ed.ac.uk");

            mockMvc.perform(post("/api/v1/auth/resend-verification-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void login_withoutCsrfToken_returns403() throws Exception {
            LoginRequest request = new LoginRequest("s1234567@ed.ac.uk", "securePassword123");

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void verify2fa_withoutCsrfToken_returns403() throws Exception {
            VerifyEmailRequest request = new VerifyEmailRequest("valid-token");

            mockMvc.perform(post("/api/v1/auth/verify2fa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void trustDevice_withoutCsrfToken_returns403() throws Exception {
            mockMvc.perform(post("/api/v1/auth/trust-device"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void logout_withoutCsrfToken_returns403() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isForbidden());
        }
    }
}
