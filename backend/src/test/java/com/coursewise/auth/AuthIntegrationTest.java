package com.coursewise.auth;

import com.coursewise.TestFixtures;
import com.coursewise.TestcontainersConfiguration;
import com.coursewise.auth.dto.LoginRequest;
import com.coursewise.auth.dto.LoginResponse;
import com.coursewise.auth.dto.RegisterRequest;
import com.coursewise.auth.dto.ResendVerificationRequest;
import com.coursewise.auth.dto.UpdateProfileRequest;
import com.coursewise.auth.dto.UserInfoResponse;
import com.coursewise.auth.dto.VerifyEmailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class AuthIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private RestClient.Builder restClientBuilder;
    @Autowired private MagicLinkRepository magicLinkRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TrustedDeviceRepository trustedDeviceRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("TRUNCATE TABLE users, magic_links, trusted_devices CASCADE");
    }

    private static final String VERIFY_EMAIL_PATH = "/api/v1/auth/verify-email";
    private static final String VERIFY_2FA_PATH = "/api/v1/auth/verify-2fa";

    // Rejection reasons produced by MagicLinkService.validateAndConsumeToken.
    private static final String TOKEN_EXPIRED = "This link has expired";
    private static final String TOKEN_ALREADY_USED = "This link has already been used";
    private static final String TOKEN_INVALID = "Invalid token";
    private static final String TOKEN_NO_ACCOUNT = "No account found for this email";

    private String uniqueEmail() {
        return TestFixtures.uniqueEdinburghEmail();
    }

    @Nested
    class Registration {

        @Test
        void register_createsUserAndSendsEmailVerification() {
            String email = uniqueEmail();
            registerUser(email, "Password123");

            assertThat(userRepository.count()).isEqualTo(1);
            User saved = userRepository.findByEmail(email).orElseThrow();
            assertThat(saved.isEmailVerified()).isFalse();

            assertThat(magicLinkRepository.count()).isEqualTo(1);
            MagicLink ml = magicLinkRepository.findAll().get(0);
            assertThat(ml.getEmail()).isEqualTo(email);
            assertThat(ml.isUsed()).isFalse();
        }

        @ParameterizedTest()
        @NullAndEmptySource
        @ValueSource(strings = {
                "   ",                       // blank
                "not-an-email",              // no @ or domain
                "s1234567@",                 // no domain
                "s1234567@ed.ac.uk.evil.com",// domain with suffix appended
                "s1234567@gmail.com",        // wrong domain
                "s1234567@ac.uk",            // truncated domain
                "s1234567@ED.AC.UK",         // capitalised domain
                "s123456@ed.ac.uk",          // 6 digits (too few)
                "s12345678@ed.ac.uk",        // 8 digits (too many)
                "sabcdefg@ed.ac.uk",         // non-digit id
                "x1234567@ed.ac.uk",         // wrong prefix letter
                "1234567@ed.ac.uk",          // missing prefix letter
                " s1234567@ed.ac.uk",        // leading whitespace
                "s1234567@ed.ac.uk ",        // trailing whitespace
                "s1234567@ed.ac.uk\n"        // trailing newline
        })
        void register_withInvalidEmail_returns400(String invalidEmail) throws Exception {
            assertThatThrownBy(() -> registerUser(invalidEmail, "Password123"))
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);

            assertThat(userRepository.count()).isZero();
            assertThat(magicLinkRepository.count()).isZero();
            verifyNoInteractions(mailSender);
        }

        @ParameterizedTest()
        @NullAndEmptySource
        @ValueSource(strings = {
                "        ",     // blank
                "Pass1",        // too short
                "Passwo1",      // 7 chars: just below min (8)
                "ThisIsMyPasswordAndItIsWaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaayyyyyyyyyyyyyyyyyyTooLong123",
                "WhereasThisPasswordIsOnlyJuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuustTooLong123", // 129 chars - just over the max (128)
                "password123",  // no uppercase
                "PasswordOnly", // no non-alphabetic character
                "PASSWORDONLY", // uppercase only, no non-alphabetic
                "12345678",     // no uppercase
                "!!!!!!!!"      // no uppercase
        })
        void register_withInvalidPassword_returns400(String invalidPassword) throws Exception {
            assertThatThrownBy(() -> registerUser("s1234567@ed.ac.uk", invalidPassword))
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);

            assertThat(userRepository.count()).isZero();
            assertThat(magicLinkRepository.count()).isZero();
            verifyNoInteractions(mailSender);
        }

        @Test
        void register_withVerifiedDuplicateEmail_returns409() {
            String email = uniqueEmail();
            String password = "Password123";
            registerVerifyAndGetSession(email, password);

            assertThatThrownBy(() -> registerUser(email, password))
                    .isInstanceOf(HttpClientErrorException.Conflict.class);
        }

        @Test
        void register_withUnverifiedDuplicateEmail_returns201AndResendsVerificationAndUpdatesPassword() {
            String email = uniqueEmail();
            String oldPassword = "Password123";
            String newPassword = "NewPassword456";
            registerUser(email, oldPassword);
            String firstToken = latestTokenSentTo(email);
            String oldHash = userRepository.findByEmail(email).orElseThrow().getPasswordHash();
            int mailsBefore = mailCount();

            ResponseEntity<Void> response = registerUser(email, newPassword);

            // Same account is reused, not duplicated, and stays unverified with the new password hash
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(userRepository.count()).isEqualTo(1);
            User saved = userRepository.findByEmail(email).orElseThrow();
            assertThat(saved.isEmailVerified()).isFalse();
            assertThat(saved.getPasswordHash()).isNotEqualTo(oldHash);
            assertThat(passwordEncoder.matches(newPassword, saved.getPasswordHash())).isTrue();
            assertThat(passwordEncoder.matches(oldPassword, saved.getPasswordHash())).isFalse();

            // A fresh verification email is sent and the previous link is superseded
            assertThat(mailCount()).isEqualTo(mailsBefore + 1);
            String secondToken = latestTokenSentTo(email);
            assertThat(secondToken).isNotEqualTo(firstToken);
            assertThat(magicLinkRepository.findAllByEmailAndUsedFalse(email)).hasSize(1);
        }
    }

    @Nested
    class EmailVerification {

        @Test
        void verifyEmail_marksUserVerifiedAndSetsSession() {
            String email = uniqueEmail();
            registerUser(email, "Password123");
            String token = latestTokenSentTo(email);

            ResponseEntity<UserInfoResponse> response = postToken(VERIFY_EMAIL_PATH, token);

            User verified = userRepository.findByEmail(email).orElseThrow();
            assertThat(verified.isEmailVerified()).isTrue();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().email()).isEqualTo(email);
            
            // Assert session cookie is present
            assertThat(TestFixtures.setCookies(response)).anyMatch(c -> c.startsWith("JSESSIONID="));
        }

        @Test
        void verifyEmail_withInvalidToken_returns400() {
            assertTokenRejected(VERIFY_EMAIL_PATH, "invalid-token", TOKEN_INVALID);
        }

        @Test
        void verifyEmail_withExpiredToken_returns400AndLeavesUserUnverified() {
            String email = uniqueEmail();
            registerUser(email, "Password123");
            String token = latestTokenSentTo(email);
            expireMagicLinks(email);

            assertTokenRejected(VERIFY_EMAIL_PATH, token, TOKEN_EXPIRED);
            assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isFalse();
        }

        @Test
        void verifyEmail_withUsedToken_returns400() {
            String email = uniqueEmail();
            registerUser(email, "Password123");
            String token = latestTokenSentTo(email);
            assertThat(postToken(VERIFY_EMAIL_PATH, token).getStatusCode()).isEqualTo(HttpStatus.OK);

            assertTokenRejected(VERIFY_EMAIL_PATH, token, TOKEN_ALREADY_USED);
        }

        @Test
        void verifyEmail_withTwoFactorToken_returns400() {
            String email = uniqueEmail();
            String password = "Password123";
            registerVerifyAndGetSession(email, password);
            String twoFactorToken = requestTwoFactorToken(email, password);

            assertTokenRejected(VERIFY_EMAIL_PATH, twoFactorToken, TOKEN_INVALID);
        }

        @Test
        void verifyEmail_withSupersededToken_returns400AndLatestTokenStillWorks() {
            String email = uniqueEmail();
            registerUser(email, "Password123");
            String firstToken = latestTokenSentTo(email);
            expireResendCooldown(email);
            client().post().uri("/api/v1/auth/resend-verification")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ResendVerificationRequest(email))
                    .retrieve()
                    .toBodilessEntity();
            String secondToken = latestTokenSentTo(email);
            assertThat(secondToken).isNotEqualTo(firstToken);

            assertTokenRejected(VERIFY_EMAIL_PATH, firstToken, TOKEN_ALREADY_USED);
            assertThat(magicLinkRepository.findAllByEmailAndUsedFalse(email)).hasSize(1);

            assertThat(postToken(VERIFY_EMAIL_PATH, secondToken).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(userRepository.findByEmail(email).orElseThrow().isEmailVerified()).isTrue();
        }

        @Test
        void verifyEmail_whenUserNoLongerExists_returns400() {
            String email = uniqueEmail();
            registerUser(email, "Password123");
            String token = latestTokenSentTo(email);
            deleteAllUsers();

            assertTokenRejected(VERIFY_EMAIL_PATH, token, TOKEN_NO_ACCOUNT);
        }
    }

    @Nested
    class ResendVerification {

        @Test
        void resendVerification_withValidEmail_returns200AndSendsEmail(){
            String email = uniqueEmail();
            registerUser(email, "Password123");
            expireResendCooldown(email);
            long magicLinksBefore = magicLinkRepository.count();
            int mailsBefore = mailCount();

            ResponseEntity<Void> response = client().post().uri("/api/v1/auth/resend-verification")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .body(new ResendVerificationRequest(email))
                                                .retrieve()
                                                .toBodilessEntity();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(magicLinkRepository.count()).isEqualTo(magicLinksBefore + 1);
            assertThat(mailCount()).isEqualTo(mailsBefore + 1);
        }

        @Test
        void resendVerification_withVerifiedEmail_returns200AndSendsNoEmail(){
            String email = uniqueEmail();
            registerVerifyAndGetSession(email, "Password123");
            expireResendCooldown(email);
            long magicLinksBefore = magicLinkRepository.count();
            int mailsBefore = mailCount();

            ResponseEntity<Void> response = client().post().uri("/api/v1/auth/resend-verification")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .body(new ResendVerificationRequest(email))
                                                .retrieve()
                                                .toBodilessEntity();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(magicLinkRepository.count()).isEqualTo(magicLinksBefore);
            assertThat(mailCount()).isEqualTo(mailsBefore);
        }

        @Test
        void resendVerification_withUnregisteredEmail_returns200AndSendsNoEmail(){
            long magicLinksBefore = magicLinkRepository.count();
            int mailsBefore = mailCount();

            ResponseEntity<Void> response = client().post().uri("/api/v1/auth/resend-verification")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .body(new ResendVerificationRequest(uniqueEmail()))
                                                .retrieve()
                                                .toBodilessEntity();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(magicLinkRepository.count()).isEqualTo(magicLinksBefore);
            assertThat(mailCount()).isEqualTo(mailsBefore);
        }

        @ParameterizedTest()
        @NullAndEmptySource
        @ValueSource(strings = {
                "   ",                       // blank
                "not-an-email",              // no @ or domain
                "s1234567@",                 // no domain
                "s1234567@ed.ac.uk.evil.com",// domain with suffix appended
                "s1234567@gmail.com",        // wrong domain
                "s1234567@ac.uk",            // truncated domain
                "s1234567@ED.AC.UK",         // capitalised domain
                "s123456@ed.ac.uk",          // 6 digits (too few)
                "s12345678@ed.ac.uk",        // 8 digits (too many)
                "sabcdefg@ed.ac.uk",         // non-digit id
                "x1234567@ed.ac.uk",         // wrong prefix letter
                "1234567@ed.ac.uk",          // missing prefix letter
                " s1234567@ed.ac.uk",        // leading whitespace
                "s1234567@ed.ac.uk ",        // trailing whitespace
                "s1234567@ed.ac.uk\n"        // trailing newline
        })
        void resendVerification_withInvalidEmail_returns400AndSendsNoEmail(String invalidEmail) {
            registerUser(uniqueEmail(), "Password123");
            long magicLinksBefore = magicLinkRepository.count();
            int mailsBefore = mailCount();

            assertThatThrownBy(() ->
                    client().post().uri("/api/v1/auth/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new ResendVerificationRequest(invalidEmail))
                            .retrieve()
                            .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);

            assertThat(magicLinkRepository.count()).isEqualTo(magicLinksBefore);
            assertThat(mailCount()).isEqualTo(mailsBefore);
        }

        @Test
        void resendVerification_whenTooSoon_returns429AndSendsNoEmail() {
            String email = uniqueEmail();
            registerUser(email, "Password123");
            long magicLinksBefore = magicLinkRepository.count();
            int mailsBefore = mailCount();

            assertThatThrownBy(() ->
                    client().post().uri("/api/v1/auth/resend-verification")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new ResendVerificationRequest(email))
                            .retrieve()
                            .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.TooManyRequests.class);

            assertThat(magicLinkRepository.count()).isEqualTo(magicLinksBefore);
            assertThat(mailCount()).isEqualTo(mailsBefore);
        }
    }

    @Nested
    class Login {

        @Test
        void login_withVerifiedEmail_requires2fa() {
            String email = uniqueEmail();
            String password = "Password123";
            registerVerifyAndGetSession(email, password);

            ResponseEntity<LoginResponse> response = client().post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(email, password))
                    .retrieve()
                    .toEntity(LoginResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().status()).isEqualTo("2fa_required");
            assertThat(response.getBody().user()).isNull();
        }

        @ParameterizedTest()
        @NullAndEmptySource
        @ValueSource(strings = {
                "   ",                       // blank
                "not-an-email",              // no @ or domain
                "s1234567@",                 // no domain
                "s1234567@ed.ac.uk.evil.com",// domain with suffix appended
                "s1234567@gmail.com",        // wrong domain
                "s1234567@ac.uk",            // truncated domain
                "s1234567@ED.AC.UK",         // capitalised domain
                "s123456@ed.ac.uk",          // 6 digits (too few)
                "s12345678@ed.ac.uk",        // 8 digits (too many)
                "sabcdefg@ed.ac.uk",         // non-digit id
                "x1234567@ed.ac.uk",         // wrong prefix letter
                "1234567@ed.ac.uk",          // missing prefix letter
                " s1234567@ed.ac.uk",        // leading whitespace
                "s1234567@ed.ac.uk ",        // trailing whitespace
                "s1234567@ed.ac.uk\n"        // trailing newline
        })
        void login_withInvalidEmail_returns400AndSendsNoEmail(String invalidEmail) {
            registerVerifyAndGetSession(uniqueEmail(), "Password123");
            long magicLinksBefore = magicLinkRepository.count();
            int mailsBefore = mailCount();

            assertThatThrownBy(() ->
                    client().post().uri("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new LoginRequest(invalidEmail, "Password123"))
                            .retrieve()
                            .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);

            assertThat(magicLinkRepository.count()).isEqualTo(magicLinksBefore);
            assertThat(mailCount()).isEqualTo(mailsBefore);
        }

        @Test
        void login_withUnverifiedEmail_returns403() {
            String email = uniqueEmail();
            registerUser(email, "Password123");

            assertThatThrownBy(() ->
                    client().post().uri("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new LoginRequest(email, "Password123"))
                            .retrieve()
                            .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.Forbidden.class);
        }

        @Test
        void login_withWrongPassword_returns401() {
            String email = uniqueEmail();
            registerVerifyAndGetSession(email, "Password123");

            assertThatThrownBy(() ->
                    client().post().uri("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new LoginRequest(email, "wrongpassword"))
                            .retrieve()
                            .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        }
    }

    @Nested
    class TrustDevice {

        @Test
        void trustDevice_createsTrustedDeviceAndSetsCookie() {
            String email = uniqueEmail();
            registerUser(email, "Password123");
            String token = latestTokenSentTo(email);

            ResponseEntity<UserInfoResponse> verifyResponse = postToken(VERIFY_EMAIL_PATH, token);
            
            String sessionCookie = TestFixtures.extractCookie(verifyResponse, "JSESSIONID");

            ResponseEntity<Void> trustResponse = client().post().uri("/api/v1/auth/trust-device")
                    .header(HttpHeaders.COOKIE, sessionCookie)
                    .header(HttpHeaders.USER_AGENT, "TestBrowser/1.0")
                    .retrieve()
                    .toBodilessEntity();
                    
            assertThat(trustResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            List<String> setCookies = trustResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
            assertThat(setCookies).anyMatch(c -> c.contains("coursewise_td"));
            
            assertThat(trustedDeviceRepository.count()).isEqualTo(1);
        }

        @Test
        void trustDevice_whenSessionUserDeleted_returns401AndCreatesNoDevice() {
            String session = registerVerifyAndGetSession(uniqueEmail(), "Password123");
            deleteAllUsers();

            assertThatThrownBy(() -> client().post().uri("/api/v1/auth/trust-device")
                    .header(HttpHeaders.COOKIE, session)
                    .header(HttpHeaders.USER_AGENT, "TestBrowser/1.0")
                    .retrieve().toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class);
            assertThat(trustedDeviceRepository.count()).isZero();
        }
    }

    @Nested
    class TwoFactorAuthentication {

        @Test
        void verify2fa_withoutTrustedDevice_returns200AndSetsSessionButNoDeviceCookie() {
            String email = uniqueEmail();
            String password = "Password123";
            registerVerifyAndGetSession(email, password);
            
            String token = requestTwoFactorToken(email, password);

            ResponseEntity<UserInfoResponse> response = postToken(VERIFY_2FA_PATH, token);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().email()).isEqualTo(email);
            
            // Assert session cookie is present
            assertThat(TestFixtures.setCookies(response)).anyMatch(c -> c.startsWith("JSESSIONID="));
            assertThat(TestFixtures.setCookies(response)).noneMatch(c -> c.startsWith("coursewise_td="));
        }

        @Test
        void verify2fa_withInvalidToken_returns400() {
            assertTokenRejected(VERIFY_2FA_PATH, "invalid-token", TOKEN_INVALID);
        }

        @Test
        void verify2fa_withExpiredToken_returns400() {
            String email = uniqueEmail();
            String password = "Password123";
            registerVerifyAndGetSession(email, password);
            String token = requestTwoFactorToken(email, password);
            expireMagicLinks(email);

            assertTokenRejected(VERIFY_2FA_PATH, token, TOKEN_EXPIRED);
        }

        @Test
        void verify2fa_withUsedToken_returns400() {
            String email = uniqueEmail();
            String password = "Password123";
            registerVerifyAndGetSession(email, password);
            String token = requestTwoFactorToken(email, password);
            assertThat(postToken(VERIFY_2FA_PATH, token).getStatusCode()).isEqualTo(HttpStatus.OK);

            assertTokenRejected(VERIFY_2FA_PATH, token, TOKEN_ALREADY_USED);
        }

        @Test
        void verify2fa_withEmailVerificationToken_returns400() {
            String email = uniqueEmail();
            registerUser(email, "Password123");
            String verificationToken = latestTokenSentTo(email);

            assertTokenRejected(VERIFY_2FA_PATH, verificationToken, TOKEN_INVALID);
        }

        @Test
        void verify2fa_withSupersededToken_returns400AndLatestTokenStillWorks() {
            String email = uniqueEmail();
            String password = "Password123";
            registerVerifyAndGetSession(email, password);
            String firstToken = requestTwoFactorToken(email, password);
            String secondToken = requestTwoFactorToken(email, password);
            assertThat(secondToken).isNotEqualTo(firstToken);

            assertTokenRejected(VERIFY_2FA_PATH, firstToken, TOKEN_ALREADY_USED);
            assertThat(magicLinkRepository.findAllByEmailAndUsedFalse(email)).hasSize(1);

            ResponseEntity<UserInfoResponse> response = postToken(VERIFY_2FA_PATH, secondToken);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(TestFixtures.setCookies(response)).anyMatch(c -> c.startsWith("JSESSIONID="));
        }

        @Test
        void verify2fa_whenUserNoLongerExists_returns400() {
            String email = uniqueEmail();
            String password = "Password123";
            registerVerifyAndGetSession(email, password);
            String token = requestTwoFactorToken(email, password);
            deleteAllUsers();

            assertTokenRejected(VERIFY_2FA_PATH, token, TOKEN_NO_ACCOUNT);
        }

        @Test
        void login_withValidTrustedDevice_bypasses2faAndSetsSession() {
            String email = uniqueEmail();
            String password = "Password123";
            registerVerifyAndGetSession(email, password);
            
            String token = requestTwoFactorToken(email, password);

            ResponseEntity<UserInfoResponse> verifyResponse = postToken(VERIFY_2FA_PATH, token);
                    
            String jsessionId = TestFixtures.extractCookie(verifyResponse, "JSESSIONID");
                    
            ResponseEntity<Void> trustResponse = client().post().uri("/api/v1/auth/trust-device")
                    .header(HttpHeaders.USER_AGENT, "TestBrowser/1.0")
                    .header(HttpHeaders.COOKIE, jsessionId)
                    .retrieve()
                    .toBodilessEntity();
                    
            String tdCookie = TestFixtures.extractCookie(trustResponse, "coursewise_td");
                    
            ResponseEntity<LoginResponse> loginResponse = client().post().uri("/api/v1/auth/login")
                    .header(HttpHeaders.COOKIE, tdCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(email, password))
                    .retrieve()
                    .toEntity(LoginResponse.class);
                    
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(loginResponse.getBody().status()).isEqualTo("authenticated");
            assertThat(loginResponse.getBody().user().email()).isEqualTo(email);
            
            // Session cookie should be set again
            assertThat(TestFixtures.setCookies(loginResponse)).anyMatch(c -> c.startsWith("JSESSIONID="));
        }

        @Test
        void trustedDeviceCookie_ofAnotherUser_doesNotBypass2fa_butCanBeSharedOnceTrusted() {
            String emailA = uniqueEmail();
            String emailB = uniqueEmail();
            String password = "Password123";

            // User A registers and trusts the device
            registerVerifyAndGetSession(emailA, password);
            
            String tokenA = requestTwoFactorToken(emailA, password);
            ResponseEntity<UserInfoResponse> verifyA = postToken(VERIFY_2FA_PATH, tokenA);
            String sessionA = TestFixtures.extractCookie(verifyA, "JSESSIONID");
                    
            ResponseEntity<Void> trustA = client().post().uri("/api/v1/auth/trust-device")
                    .header(HttpHeaders.COOKIE, sessionA)
                    .header(HttpHeaders.USER_AGENT, "SharedBrowser")
                    .retrieve().toBodilessEntity();
            String tdCookie = TestFixtures.extractCookie(trustA, "coursewise_td");

            // User B registers
            registerVerifyAndGetSession(emailB, password);
            
            // User B logs in using the same device cookie
            ResponseEntity<LoginResponse> loginB = client().post().uri("/api/v1/auth/login")
                    .header(HttpHeaders.COOKIE, tdCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(emailB, password))
                    .retrieve().toEntity(LoginResponse.class);
                    
            // User B should still require 2FA, because they haven't explicitly trusted the device yet
            assertThat(loginB.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(loginB.getBody().status()).isEqualTo("2fa_required");
            
            // User B completes 2FA
            String tokenB = latestTokenSentTo(emailB);
            ResponseEntity<UserInfoResponse> verifyB = postToken(VERIFY_2FA_PATH, tokenB);
            String sessionB = TestFixtures.extractCookie(verifyB, "JSESSIONID");
                    
            // User B now trusts the device too (sending the existing tdCookie)
            ResponseEntity<Void> trustB = client().post().uri("/api/v1/auth/trust-device")
                    .header(HttpHeaders.COOKIE, sessionB + "; " + tdCookie) // Send both JSESSIONID and coursewise_td
                    .header(HttpHeaders.USER_AGENT, "SharedBrowser")
                    .retrieve().toBodilessEntity();
            String newTdCookie = TestFixtures.extractCookie(trustB, "coursewise_td");
                    
            // The returned cookie token should be exactly the same (shared token)
            assertThat(tdCookie).isEqualTo(newTdCookie);

            // Database should have exactly 2 records for the trusted devices
            assertThat(trustedDeviceRepository.count()).isEqualTo(2);

            // Now that B has trusted the device, the shared cookie lets B bypass 2FA
            ResponseEntity<LoginResponse> loginBTrusted = client().post().uri("/api/v1/auth/login")
                    .header(HttpHeaders.COOKIE, tdCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(emailB, password))
                    .retrieve().toEntity(LoginResponse.class);
            assertThat(loginBTrusted.getBody().status()).isEqualTo("authenticated");
            assertThat(loginBTrusted.getBody().user().email()).isEqualTo(emailB);
        }
    }

    @Nested
    class GetUser {

        @Test
        void me_withValidSession_returnsOwnUserInfo() {
            String email = uniqueEmail();
            String session = registerVerifyAndGetSession(email, "Password123");

            ResponseEntity<UserInfoResponse> response = getMe(session);

            User saved = userRepository.findByEmail(email).orElseThrow();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            UserInfoResponse body = response.getBody();
            assertThat(body.id()).isEqualTo(saved.getId());
            assertThat(body.email()).isEqualTo(email);
            assertThat(body.studentNumber()).isEqualTo(email.split("@")[0]);
            assertThat(body.createdAt()).isNotNull();
        }

        @Test
        void me_withoutSession_returns401() {
            assertThatThrownBy(() -> client().get().uri("/api/v1/auth/me")
                    .retrieve().toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        }

        @Test
        void me_whenSessionUserDeleted_returns401() {
            String session = registerVerifyAndGetSession(uniqueEmail(), "Password123");
            deleteAllUsers();

            assertThatThrownBy(() -> getMe(session))
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        }
    }

    @Nested
    class UpdateProfile {

        @Test
        void updateProfile_withValidNames_returnsUpdatedUserAndSavesToDatabase() {
            String email = uniqueEmail();
            String session = registerVerifyAndGetSession(email, "Password123");

            ResponseEntity<UserInfoResponse> response = patchProfile(session, new UpdateProfileRequest("John", "Doe"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().email()).isEqualTo(email);
            assertThat(response.getBody().firstName()).isEqualTo("John");
            assertThat(response.getBody().lastName()).isEqualTo("Doe");
            
            User saved = userRepository.findByEmail(email).orElseThrow();
            assertThat(saved.getFirstName()).isEqualTo("John");
            assertThat(saved.getLastName()).isEqualTo("Doe");
        }

        @Test
        void updateProfile_twice_overwritesPreviousNames() {
            String email = uniqueEmail();
            String session = registerVerifyAndGetSession(email, "Password123");
            patchProfile(session, new UpdateProfileRequest("John", "Doe"));

            patchProfile(session, new UpdateProfileRequest("Jane", "Smith"));

            User saved = userRepository.findByEmail(email).orElseThrow();
            assertThat(saved.getFirstName()).isEqualTo("Jane");
            assertThat(saved.getLastName()).isEqualTo("Smith");
        }

        @ParameterizedTest
        @EmptySource 
        @ValueSource(strings = {
                "thisnameiswaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaayyytoolong",
                "whereasthisnameisonlyjuuuuuuuuuuuuuuuuuuuusttoolong", // 51 chars - just over the max (50)
                "    ",
                "\t",
                "\n"
        })
        void updateProfile_withInvalidNames_returns400AndChangesNothing(String invalidName) {
            String email = uniqueEmail();
            String session = registerVerifyAndGetSession(email, "Password123");
            patchProfile(session, new UpdateProfileRequest("John", "Doe"));

            assertThatThrownBy(() -> patchProfile(session, new UpdateProfileRequest(invalidName, "White")))
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
            assertThatThrownBy(() -> patchProfile(session, new UpdateProfileRequest("Jane", invalidName)))
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        
            User saved = userRepository.findByEmail(email).orElseThrow();
            assertThat(saved.getFirstName()).isEqualTo("John");
            assertThat(saved.getLastName()).isEqualTo("Doe");
        }

        @Test
        void updateProfile_withOmittedFields_leavesThoseNamesUnchanged() {
            String email = uniqueEmail();
            String session = registerVerifyAndGetSession(email, "Password123");
            patchProfile(session, new UpdateProfileRequest("John", "Doe"));

            ResponseEntity<UserInfoResponse> response = patchProfile(session, new UpdateProfileRequest("Jane", null));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            User saved = userRepository.findByEmail(email).orElseThrow();
            assertThat(saved.getFirstName()).isEqualTo("Jane");
            assertThat(saved.getLastName()).isEqualTo("Doe");

            response = patchProfile(session, new UpdateProfileRequest(null, "White"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            saved = userRepository.findByEmail(email).orElseThrow();
            assertThat(saved.getFirstName()).isEqualTo("Jane");
            assertThat(saved.getLastName()).isEqualTo("White");
        }

        @Test
        void updateProfile_unauthenticated_returns401AndChangesNothing() {
            String email = uniqueEmail();
            registerVerifyAndGetSession(email, "Password123");

            assertThatThrownBy(() -> client().patch().uri("/api/v1/auth/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new UpdateProfileRequest("John", "Doe"))
                    .retrieve().toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class);
            assertThat(userRepository.findByEmail(email).orElseThrow().getFirstName()).isNull();
        }

        @Test
        void updateProfile_whenSessionUserDeleted_returns401() {
            String session = registerVerifyAndGetSession(uniqueEmail(), "Password123");
            deleteAllUsers();

            assertThatThrownBy(() -> patchProfile(session, new UpdateProfileRequest("John", "Doe")))
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        }
    }

    @Nested
    class Logout {

        @Test
        void logout_clearsSessionCookie() {
            String email = uniqueEmail();
            registerVerifyAndGetSession(email, "Password123");
            
            // Login to get session
            String token = requestTwoFactorToken(email, "Password123");
            
            ResponseEntity<UserInfoResponse> verifyResponse = postToken(VERIFY_2FA_PATH, token);
            String jsessionId = TestFixtures.extractCookie(verifyResponse, "JSESSIONID");

            // Verify login works
            ResponseEntity<Void> loggedInResponse = client().get().uri("/api/v1/auth/me")
                    .header(HttpHeaders.COOKIE, jsessionId).retrieve().toBodilessEntity();
            assertThat(loggedInResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Logout
            ResponseEntity<Void> logoutResponse = client().post().uri("/api/v1/auth/logout")
                    .header(HttpHeaders.COOKIE, jsessionId)
                    .retrieve().toBodilessEntity();

            // Assert JSESSIONID is cleared
            List<String> setCookies = logoutResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
            assertThat(setCookies).isNotNull();
            assertThat(setCookies).anyMatch(c -> c.startsWith("JSESSIONID=") && c.contains("Max-Age=0"));

            // Assert user is unauthenticated servier-side
            assertThatThrownBy(() -> client().get().uri("/api/v1/auth/me")
                .header(HttpHeaders.COOKIE, jsessionId).retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        }

        @Test
        void logout_doesNotClearTrustedDeviceCookie() {
            String email = uniqueEmail();
            registerVerifyAndGetSession(email, "Password123");
            
            // Login and trust device to get session
            String token = requestTwoFactorToken(email, "Password123");
            
            ResponseEntity<UserInfoResponse> verifyResponse = postToken(VERIFY_2FA_PATH, token);
            String jsessionId = TestFixtures.extractCookie(verifyResponse, "JSESSIONID");

            ResponseEntity<Void> trustResponse = client().post().uri("/api/v1/auth/trust-device")
                    .header(HttpHeaders.COOKIE, jsessionId)
                    .header(HttpHeaders.USER_AGENT, "TestBrowser/1.0")
                    .retrieve().toBodilessEntity();
            String tdCookie = TestFixtures.extractCookie(trustResponse, "coursewise_td");

            // Logout
            ResponseEntity<Void> logoutResponse = client().post().uri("/api/v1/auth/logout")
                    .header(HttpHeaders.COOKIE, jsessionId + "; " + tdCookie)
                    .retrieve().toBodilessEntity();
                    
            // Assert coursewise_td is NOT cleared
            List<String> setCookies = logoutResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
            assertThat(setCookies).isNotNull();
            assertThat(setCookies).noneMatch(c -> c.startsWith("coursewise_td=")); // ensure none of the headers touch the coursewise_td cookie
            assertThat(trustedDeviceRepository.count()).isEqualTo(1);
        }
    }

    // --- Helpers ---

    private String registerVerifyAndGetSession(String email, String password) {
        registerUser(email, password);
        ResponseEntity<UserInfoResponse> response = postToken(VERIFY_EMAIL_PATH, latestTokenSentTo(email));
        return TestFixtures.extractCookie(response, "JSESSIONID");
    }

    private ResponseEntity<Void> registerUser(String email, String password) {
        return client().post().uri("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new RegisterRequest(email, password))
                        .retrieve()
                        .toBodilessEntity();
    }

    private void deleteAllUsers() {
        jdbcTemplate.execute("DELETE FROM users");
    }

    private ResponseEntity<UserInfoResponse> getMe(String session) {
        return client().get().uri("/api/v1/auth/me")
                .header(HttpHeaders.COOKIE, session)
                .retrieve()
                .toEntity(UserInfoResponse.class);
    }

    private ResponseEntity<UserInfoResponse> patchProfile(String session, UpdateProfileRequest request) {
        return client().patch().uri("/api/v1/auth/profile")
                .header(HttpHeaders.COOKIE, session)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(UserInfoResponse.class);
    }

    private RestClient client() {
        return restClientBuilder.baseUrl("http://localhost:" + port).build();
    }

    private int mailCount() {
        return Mockito.mockingDetails(mailSender).getInvocations().size();
    }

    private void expireResendCooldown(String email) {
        jdbcTemplate.update("UPDATE magic_links SET created_at = created_at - INTERVAL '1 hour' WHERE email = ?", email);
    }

    private void expireMagicLinks(String email) {
        jdbcTemplate.update("UPDATE magic_links SET expires_at = expires_at - INTERVAL '1 hour' WHERE email = ?", email);
    }

    private ResponseEntity<UserInfoResponse> postToken(String path, String token) {
        return client().post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VerifyEmailRequest(token))
                .retrieve()
                .toEntity(UserInfoResponse.class);
    }

    /** Logs in with valid credentials (which triggers the 2FA email) and returns the emailed 2FA token. */
    private String requestTwoFactorToken(String email, String password) {
        client().post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password))
                .retrieve()
                .toBodilessEntity();
        return latestTokenSentTo(email);
    }

    /**
     * Asserts the token is rejected with a 400 whose body carries the expected reason, and that no
     * session cookie is issued.
     */
    private void assertTokenRejected(String path, String token, String expectedDetail) {
        assertThatThrownBy(() -> postToken(path, token))
                .isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class, e -> {
                    assertThat(e.getResponseBodyAsString()).contains(expectedDetail);
                    HttpHeaders headers = e.getResponseHeaders();
                    List<String> setCookies = headers == null ? null : headers.get(HttpHeaders.SET_COOKIE);
                    assertThat(setCookies == null ? List.<String>of() : setCookies)
                            .noneMatch(c -> c.startsWith("JSESSIONID="));
                });
    }

    private String latestTokenSentTo(String email) {
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
}
