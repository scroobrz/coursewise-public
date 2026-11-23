package com.coursewise.auth;

import com.coursewise.auth.dto.LoginRequest;
import com.coursewise.auth.dto.RegisterRequest;
import com.coursewise.auth.dto.ResendVerificationRequest;
import com.coursewise.auth.dto.UpdateProfileRequest;
import com.coursewise.auth.dto.UserInfoResponse;
import com.coursewise.auth.dto.VerifyEmailRequest;
import com.coursewise.auth.exception.EmailAlreadyRegisteredException;
import com.coursewise.auth.exception.EmailNotVerifiedException;
import com.coursewise.auth.exception.InvalidCredentialsException;
import com.coursewise.auth.exception.InvalidTokenException;
import com.coursewise.auth.exception.ResendTooSoonException;
import com.coursewise.auth.exception.UserNotFoundException;
import com.coursewise.auth.validation.EmailValidator;
import com.coursewise.auth.validation.PasswordValidator;
import com.coursewise.config.AppConfigProperties;
import com.coursewise.config.AppConfigResponse;
import com.coursewise.config.AppConfigResponse.ValidationProperties;
import com.coursewise.config.GlobalExceptionHandler;
import com.coursewise.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        AuthControllerWebTest.MvcConfig.class,
        AuthController.class,
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

    private static RequestPostProcessor authenticatedAs(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }


    @Nested
    class Register {

        @Test
        void register_withValidInput_returns201() throws Exception {
            String email = "s1234567@ed.ac.uk";
            String password = "securePassword123";
            RegisterRequest request = new RegisterRequest(email, password);

            mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(authService).register(email, password);
        }

        @ParameterizedTest()
        @NullAndEmptySource
        @MethodSource("com.coursewise.TestParameters#invalidEmails")
        void register_withInvalidEmail_returns400(String invalidEmail) throws Exception {
            RegisterRequest request = new RegisterRequest(invalidEmail, "securePassword123");

            mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @ParameterizedTest()
        @NullAndEmptySource
        @MethodSource("com.coursewise.TestParameters#invalidPasswords")
        void register_withInvalidPassword_returns400(String invalidPassword) throws Exception {
            RegisterRequest request = new RegisterRequest("s1234567@ed.ac.uk", invalidPassword);

            mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        void register_withVerifiedDuplicateEmail_returns409() throws Exception {
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
    class VerifyEmail {

        @Test
        void verifyEmail_withValidToken_returns200() throws Exception {
            VerifyEmailRequest request = new VerifyEmailRequest("valid-token");
            User mockUser = new User("s1234567@ed.ac.uk");
            ReflectionTestUtils.setField(mockUser, "id", UUID.randomUUID());

            when(authService.verifyRegistrationEmail(eq("valid-token"), any(HttpServletRequest.class)))
                .thenReturn(UserInfoResponse.from(mockUser));

            mockMvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("s1234567@ed.ac.uk"));
        }

        @ParameterizedTest()
        @NullAndEmptySource
        @MethodSource("com.coursewise.TestParameters#blankTokens")
        void verifyEmail_withBlankToken_returns400(String blankToken) throws Exception {
            mockMvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new VerifyEmailRequest(blankToken))))
                    .andExpect(status().isBadRequest())
                    .andExpect(cookie().doesNotExist("JSESSIONID"));

            verifyNoInteractions(authService);
        }

        @ParameterizedTest()
        @MethodSource("com.coursewise.TestParameters#rejectedTokenScenarios")
        void verifyEmail_withInvalidToken_returns400ProblemDetail(String scenario, String serviceMessage) throws Exception {
            VerifyEmailRequest request = new VerifyEmailRequest("rejected-token");
            doThrow(new InvalidTokenException(serviceMessage))
                .when(authService).verifyRegistrationEmail(eq("rejected-token"), any(HttpServletRequest.class));

            mockMvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid Token"))
                    .andExpect(jsonPath("$.detail").value(serviceMessage))
                    .andExpect(cookie().doesNotExist("JSESSIONID"));

            verify(authService).verifyRegistrationEmail(eq("rejected-token"), any(HttpServletRequest.class));
        }
    }

    @Nested
    class Login {

        @Test
        void login_with2faRequired_returns200WithStatus() throws Exception {
            LoginRequest request = new LoginRequest("s1234567@ed.ac.uk", "Password123");
            when(authService.login(anyString(), anyString(), any(), any(HttpServletRequest.class)))
                .thenReturn(Optional.empty());

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

            when(authService.login(anyString(), anyString(), any(), any(HttpServletRequest.class)))
                .thenReturn(Optional.of(UserInfoResponse.from(mockUser)));

            mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("authenticated"))
                    .andExpect(jsonPath("$.user.email").value("s1234567@ed.ac.uk"));
        }

        @ParameterizedTest()
        @NullAndEmptySource
        @MethodSource("com.coursewise.TestParameters#invalidEmails")
        void login_withInvalidEmail_returns400(String invalidEmail) throws Exception {
            LoginRequest request = new LoginRequest(invalidEmail, "Password123");

            mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        void login_withUnverifiedEmail_returns403() throws Exception {
            LoginRequest request = new LoginRequest("s1234567@ed.ac.uk", "Password123");
            when(authService.login(anyString(), anyString(), any(), any(HttpServletRequest.class)))
                .thenThrow(new EmailNotVerifiedException());

            mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void login_withBlankPassword_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest("s1234567@ed.ac.uk", ""))))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        void login_withWrongPassword_returns401() throws Exception {
            LoginRequest request = new LoginRequest("s1234567@ed.ac.uk", "wrong");
            when(authService.login(anyString(), anyString(), any(), any(HttpServletRequest.class)))
                .thenThrow(new InvalidCredentialsException());

            mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Verify2fa {

        @Test
        void verify2fa_withValidToken_returns200WithUserInfo() throws Exception {
            VerifyEmailRequest request = new VerifyEmailRequest("valid-token");
            User mockUser = new User("s1234567@ed.ac.uk");
            ReflectionTestUtils.setField(mockUser, "id", UUID.randomUUID());

            when(authService.verify2fa(eq("valid-token"), any(HttpServletRequest.class)))
                .thenReturn(UserInfoResponse.from(mockUser));

            mockMvc.perform(post("/api/v1/auth/verify-2fa").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("s1234567@ed.ac.uk"));
        }

        @ParameterizedTest()
        @NullAndEmptySource
        @MethodSource("com.coursewise.TestParameters#blankTokens")
        void verify2fa_withBlankToken_returns400(String blankToken) throws Exception {
            mockMvc.perform(post("/api/v1/auth/verify-2fa").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new VerifyEmailRequest(blankToken))))
                    .andExpect(status().isBadRequest())
                    .andExpect(cookie().doesNotExist("JSESSIONID"));

            verifyNoInteractions(authService);
        }

        @ParameterizedTest()
        @MethodSource("com.coursewise.TestParameters#rejectedTokenScenarios")
        void verify2fa_withInvalidToken_returns400ProblemDetail(String scenario, String serviceMessage) throws Exception {
            VerifyEmailRequest request = new VerifyEmailRequest("rejected-token");
            doThrow(new InvalidTokenException(serviceMessage))
                .when(authService).verify2fa(eq("rejected-token"), any(HttpServletRequest.class));

            mockMvc.perform(post("/api/v1/auth/verify-2fa").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid Token"))
                    .andExpect(jsonPath("$.detail").value(serviceMessage))
                    .andExpect(cookie().doesNotExist("JSESSIONID"));

            verify(authService).verify2fa(eq("rejected-token"), any(HttpServletRequest.class));
        }
    }

    @Nested
    class ResendVerification {

        @Test
        void resendVerification_withValidEmail_returns200() throws Exception {
            String email = "s1234567@ed.ac.uk";
            ResendVerificationRequest request = new ResendVerificationRequest(email);

            mockMvc.perform(post("/api/v1/auth/resend-verification").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(authService).resendVerification(email);
        }

        @ParameterizedTest()
        @NullAndEmptySource
        @MethodSource("com.coursewise.TestParameters#invalidEmails")
        void resendVerification_withInvalidEmail_returns400(String invalidEmail) throws Exception {
            ResendVerificationRequest request = new ResendVerificationRequest(invalidEmail);

            mockMvc.perform(post("/api/v1/auth/resend-verification").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        void resendVerification_whenTooSoon_returns429() throws Exception {
            ResendVerificationRequest request = new ResendVerificationRequest("s1234567@ed.ac.uk");

            doThrow(new ResendTooSoonException())
                .when(authService).resendVerification(anyString());

            mockMvc.perform(post("/api/v1/auth/resend-verification").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.title").value("Resend Too Soon"));
        }
    }

    @Nested
    class GetUser {

        @Test
        void me_whenAuthenticated_returns200WithUserInfo() throws Exception {
            UUID userId = UUID.randomUUID();
            User mockUser = new User("s1234567@ed.ac.uk");
            ReflectionTestUtils.setField(mockUser, "id", userId);
            when(authService.getUser(userId)).thenReturn(mockUser);

            mockMvc.perform(get("/api/v1/auth/me").with(authenticatedAs(userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(userId.toString()))
                    .andExpect(jsonPath("$.email").value("s1234567@ed.ac.uk"));
        }

        @Test
        void me_whenNotAuthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized());
            // This request should be stopped at the security filter chain, and never reach the service layer
            verifyNoInteractions(authService);
        }

        @Test
        void me_whenSessionUserNoLongerExists_returns401() throws Exception {
            // Valid session, but the user was deleted since it was created (stale session).
            UUID userId = UUID.randomUUID();

            when(authService.getUser(userId))
                    .thenThrow(new UserNotFoundException(userId));

            mockMvc.perform(get("/api/v1/auth/me").with(authenticatedAs(userId)))
                    .andExpect(status().isUnauthorized());
            
            // verify that the 401 status actually came from the service, rather than getting intercepted in the security filter chain
            verify(authService).getUser(userId);
        }
    }

    @Nested 
    class UpdateProfile {

        @Test
        void updateProfile_whenAuthenticated_returns200WithUserInfo() throws Exception {
            UUID userId = UUID.randomUUID();
            User mockUser = new User("s1234567@ed.ac.uk");
            ReflectionTestUtils.setField(mockUser, "id", userId);
            
            when(authService.updateProfile(eq(userId), any(), any()))
                .thenReturn(UserInfoResponse.from(mockUser));

            mockMvc.perform(patch("/api/v1/auth/profile").with(authenticatedAs(userId)).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateProfileRequest("John", "Doe"))).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(userId.toString()))
                    .andExpect(jsonPath("$.email").value("s1234567@ed.ac.uk"));
        }

        @ParameterizedTest
        @EmptySource 
        @MethodSource("com.coursewise.TestParameters#invalidNames")
        void updateProfile_withInvalidNames_returns400(String invalidName) throws Exception {
            UUID userId = UUID.randomUUID();

            mockMvc.perform(patch("/api/v1/auth/profile")
                                .with(authenticatedAs(userId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new UpdateProfileRequest(invalidName, "Doe")))
                                .with(csrf()))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(patch("/api/v1/auth/profile")
                                .with(authenticatedAs(userId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new UpdateProfileRequest("John", invalidName)))
                                .with(csrf()))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authService);
        }

        @Test
        void updateProfile_acceptsNullNames() throws Exception {
            UUID userId = UUID.randomUUID();

            mockMvc.perform(patch("/api/v1/auth/profile")
                            .with(authenticatedAs(userId)).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateProfileRequest(null, null))).with(csrf()))
                    .andExpect(status().isOk());
        }

        @Test
        void updateProfile_whenNotAuthenticated_returns401() throws Exception {
            mockMvc.perform(patch("/api/v1/auth/profile").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateProfileRequest("John", "Doe"))).with(csrf()))
                    .andExpect(status().isUnauthorized());
            // This request should be stopped at the security filter chain, and never reach the service layer
            verifyNoInteractions(authService);
        }

        @Test
        void updateProfile_whenSessionUserNoLongerExists_returns401() throws Exception {
            // Valid session, but the user was deleted since it was created (stale session).
            UUID userId = UUID.randomUUID();

            when(authService.updateProfile(eq(userId), any(), any()))
                    .thenThrow(new UserNotFoundException(userId));

            mockMvc.perform(patch("/api/v1/auth/profile").with(authenticatedAs(userId)).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new UpdateProfileRequest("John", "Doe"))).with(csrf()))
                    .andExpect(status().isUnauthorized());
            
            // verify that the 401 status actually came from the service, rather than getting intercepted in the security filter chain
            verify(authService).updateProfile(eq(userId), anyString(), anyString());
        }
    }

    @Nested
    class TrustDevice {

        @Test
        void trustDevice_whenAuthenticated_setsCookie() throws Exception {
            UUID userId = UUID.randomUUID();
            User mockUser = new User("s1234567@ed.ac.uk");
            ReflectionTestUtils.setField(mockUser, "id", userId);
            
            when(authService.getUser(userId)).thenReturn(mockUser);
            when(authService.createTrustedDevice(eq(mockUser), any(), any()))
                .thenReturn("new-device-token");

            mockMvc.perform(post("/api/v1/auth/trust-device").with(csrf()).with(authenticatedAs(userId)))
                    .andExpect(status().isOk())
                    .andExpect(cookie().value("coursewise_td", "new-device-token"))
                    .andExpect(cookie().httpOnly("coursewise_td", true));
        }

        @Test
        void trustDevice_whenNotAuthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/trust-device").with(csrf()))
                    .andExpect(status().isUnauthorized());
            // This request should be stopped at the security filter chain, and never reach the service layer
            verifyNoInteractions(authService);
        }

        @Test
        void trustDevice_whenSessionUserNoLongerExists_returns401() throws Exception {
            // Valid session, but the user was deleted since it was created (stale session).
            UUID userId = UUID.randomUUID();

            when(authService.getUser(eq(userId)))
                    .thenThrow(new UserNotFoundException(userId));

            mockMvc.perform(post("/api/v1/auth/trust-device").with(csrf()).with(authenticatedAs(userId)))
                    .andExpect(status().isUnauthorized());
            
            // verify that the 401 status actually came from the service, rather than getting intercepted in the security filter chain
            verify(authService).getUser(userId);
        }
    }

    @Nested 
    class Logout {

        @Test
        void logout_delegatesToServiceAndReturns200() throws Exception {
            UUID userId = UUID.randomUUID();

            mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).with(authenticatedAs(userId)))
                    .andExpect(status().isOk());

            verify(authService).logout(any(HttpServletRequest.class));
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

            mockMvc.perform(post("/api/v1/auth/resend-verification")
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

            mockMvc.perform(post("/api/v1/auth/verify-2fa")
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
