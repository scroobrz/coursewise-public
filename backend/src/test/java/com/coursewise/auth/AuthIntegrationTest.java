package com.coursewise.auth;

import com.coursewise.TestFixtures;
import com.coursewise.TestcontainersConfiguration;
import com.coursewise.auth.dto.LoginRequest;
import com.coursewise.auth.dto.LoginResponse;
import com.coursewise.auth.dto.RegisterRequest;
import com.coursewise.auth.dto.UserInfoResponse;
import com.coursewise.auth.dto.VerifyEmailRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
@TestPropertySource(properties = "management.health.mail.enabled=false")
class AuthIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private MagicLinkRepository magicLinkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrustedDeviceRepository trustedDeviceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("TRUNCATE TABLE users, magic_links, trusted_devices CASCADE");
    }

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

        @Test
        void register_withVerifiedDuplicateEmail_returns409() {
            String email = uniqueEmail();
            registerAndVerify(email, "Password123");

            assertThatThrownBy(() -> registerUser(email, "Password123"))
                    .isInstanceOf(HttpClientErrorException.Conflict.class);
        }
    }

    @Nested
    class EmailVerification {

        @Test
        void verifyEmail_marksUserVerifiedAndSetsSession() {
            String email = uniqueEmail();
            registerUser(email, "Password123");
            String token = latestUnusedTokenFor(email);

            ResponseEntity<UserInfoResponse> response = client().post().uri("/api/v1/auth/verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new VerifyEmailRequest(token))
                    .retrieve()
                    .toEntity(UserInfoResponse.class);

            User verified = userRepository.findByEmail(email).orElseThrow();
            assertThat(verified.isEmailVerified()).isTrue();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().email()).isEqualTo(email);
            
            // Assert session cookie is present
            String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).contains("JSESSIONID");
        }

        @Test
        void verifyEmail_withInvalidToken_returns400() {
            assertThatThrownBy(() ->
                    client().post().uri("/api/v1/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new VerifyEmailRequest("invalid-token"))
                            .retrieve()
                            .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);
        }
    }

    @Nested
    class Login {

        @Test
        void login_withVerifiedEmail_requires2fa() {
            String email = uniqueEmail();
            registerAndVerify(email, "Password123");

            ResponseEntity<LoginResponse> response = client().post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(email, "Password123"))
                    .retrieve()
                    .toEntity(LoginResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().status()).isEqualTo("2fa_required");
            assertThat(response.getBody().user()).isNull();
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
            registerAndVerify(email, "Password123");

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
            String token = latestUnusedTokenFor(email);

            ResponseEntity<UserInfoResponse> verifyResponse = client().post().uri("/api/v1/auth/verify-email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new VerifyEmailRequest(token))
                    .retrieve()
                    .toEntity(UserInfoResponse.class);
            
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
    }

    @Nested
    class TwoFactorAuthentication {

        @Test
        void verify2fa_withoutTrustedDevice_returns200AndSetsSessionButNoDeviceCookie() {
            String email = uniqueEmail();
            registerAndVerify(email, "Password123");
            
            client().post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(email, "Password123"))
                    .retrieve()
                    .toBodilessEntity();
                    
            String token = latestUnusedTokenFor(email);

            ResponseEntity<UserInfoResponse> response = client().post().uri("/api/v1/auth/verify-2fa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new VerifyEmailRequest(token))
                    .retrieve()
                    .toEntity(UserInfoResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().email()).isEqualTo(email);
            
            // Assert session cookie is present
            String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).contains("JSESSIONID");
            assertThat(setCookie).doesNotContain("coursewise_td");
        }
        
        @Test
        void login_withValidTrustedDevice_bypasses2faAndSetsSession() {
            String email = uniqueEmail();
            registerAndVerify(email, "Password123");
            
            client().post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(email, "Password123"))
                    .retrieve()
                    .toBodilessEntity();
                    
            String token = latestUnusedTokenFor(email);

            ResponseEntity<UserInfoResponse> verifyResponse = client().post().uri("/api/v1/auth/verify-2fa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new VerifyEmailRequest(token))
                    .retrieve()
                    .toEntity(UserInfoResponse.class);
                    
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
                    .body(new LoginRequest(email, "Password123"))
                    .retrieve()
                    .toEntity(LoginResponse.class);
                    
            assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(loginResponse.getBody().status()).isEqualTo("authenticated");
            assertThat(loginResponse.getBody().user().email()).isEqualTo(email);
            
            // Session cookie should be set again
            assertThat(loginResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("JSESSIONID");
        }

        @Test
        void sharedTrustedDevice_keepsTrustIsolatedPerUser() {
            String emailA = uniqueEmail();
            String emailB = uniqueEmail();

            // User A registers and trusts the device
            registerAndVerify(emailA, "Password123");
            
            client().post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(emailA, "Password123"))
                    .retrieve().toBodilessEntity();
            String tokenA = latestUnusedTokenFor(emailA);
            ResponseEntity<Void> verifyA = client().post().uri("/api/v1/auth/verify-2fa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new VerifyEmailRequest(tokenA))
                    .retrieve().toBodilessEntity();
            String sessionA = TestFixtures.extractCookie(verifyA, "JSESSIONID");
                    
            ResponseEntity<Void> trustA = client().post().uri("/api/v1/auth/trust-device")
                    .header(HttpHeaders.COOKIE, sessionA)
                    .header(HttpHeaders.USER_AGENT, "SharedBrowser")
                    .retrieve().toBodilessEntity();
            String tdCookie = TestFixtures.extractCookie(trustA, "coursewise_td");

            // User B registers
            registerAndVerify(emailB, "Password123");
            
            // User B logs in using the same device cookie
            ResponseEntity<LoginResponse> loginB = client().post().uri("/api/v1/auth/login")
                    .header(HttpHeaders.COOKIE, tdCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(emailB, "Password123"))
                    .retrieve().toEntity(LoginResponse.class);
                    
            // User B should still require 2FA, because they haven't explicitly trusted the device yet
            assertThat(loginB.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(loginB.getBody().status()).isEqualTo("2fa_required");
            
            // User B completes 2FA
            String tokenB = latestUnusedTokenFor(emailB);
            ResponseEntity<Void> verifyB = client().post().uri("/api/v1/auth/verify-2fa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new VerifyEmailRequest(tokenB))
                    .retrieve().toBodilessEntity();
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
        }
    }

    @Nested
    class Logout {

        @Test
        void logout_clearsSessionCookie() {
            String email = uniqueEmail();
            registerAndVerify(email, "Password123");
            
            // Login to get session
            client().post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(email, "Password123"))
                    .retrieve().toBodilessEntity();
            String token = latestUnusedTokenFor(email);
            
            ResponseEntity<Void> verifyResponse = client().post().uri("/api/v1/auth/verify-2fa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new VerifyEmailRequest(token))
                    .retrieve().toBodilessEntity();
            String jsessionId = TestFixtures.extractCookie(verifyResponse, "JSESSIONID");

            // Logout
            ResponseEntity<Void> logoutResponse = client().post().uri("/api/v1/auth/logout")
                    .header(HttpHeaders.COOKIE, jsessionId)
                    .retrieve().toBodilessEntity();
                    
            // Assert JSESSIONID is cleared
            List<String> setCookies = logoutResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
            assertThat(setCookies).isNotNull();
            assertThat(setCookies).anyMatch(c -> c.startsWith("JSESSIONID=") && c.contains("Max-Age=0"));
        }

        @Test
        void logout_doesNotClearTrustedDeviceCookie() {
            String email = uniqueEmail();
            registerAndVerify(email, "Password123");
            
            // Login and trust device to get session
            client().post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(email, "Password123"))
                    .retrieve().toBodilessEntity();
            String token = latestUnusedTokenFor(email);
            
            ResponseEntity<Void> verifyResponse = client().post().uri("/api/v1/auth/verify-2fa")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new VerifyEmailRequest(token))
                    .retrieve().toBodilessEntity();
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
            if (setCookies != null) {
                assertThat(setCookies).noneMatch(c -> c.startsWith("coursewise_td=") && c.contains("Max-Age=0"));
            }
        }
    }

    // --- Helpers ---

    private RestClient client() {
        return restClientBuilder.baseUrl("http://localhost:" + port).build();
    }

    private void registerUser(String email, String password) {
        client().post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(email, password))
                .retrieve()
                .toBodilessEntity();
    }
    
    private void registerAndVerify(String email, String password) {
        registerUser(email, password);
        String token = latestUnusedTokenFor(email);
        client().post().uri("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VerifyEmailRequest(token))
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
}
