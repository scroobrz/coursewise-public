package com.coursewise.auth;

import com.coursewise.auth.dto.UserInfoResponse;
import com.coursewise.auth.exception.EmailAlreadyRegisteredException;
import com.coursewise.auth.exception.EmailNotVerifiedException;
import com.coursewise.auth.exception.InvalidCredentialsException;
import com.coursewise.auth.exception.InvalidTokenException;
import com.coursewise.auth.exception.ResendTooSoonException;
import com.coursewise.config.AppConfigProperties;
import com.coursewise.config.AppConfigResponse;
import com.coursewise.config.AppConfigResponse.ValidationProperties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TrustedDeviceRepository trustedDeviceRepository;
    @Mock private MagicLinkService magicLinkService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;
    private MockHttpServletRequest request;

    // ── Shared test constants ─────────────────────────────────────────────────────────
    private static final int    TEST_TRUSTED_DEVICE_DAYS   = 30;
    private static final int    TEST_RESEND_COOLDOWN_SECS   = 60;
    private static final int    TEST_MAGIC_LINK_EXPIRY_MINS = 15;
    private static final String TEST_FRONTEND_URL           = "http://localhost:4200";
    private static final String TEST_FROM_ADDRESS           = "noreply@coursewise.com";

    private static final AppConfigProperties TEST_CONFIG = new AppConfigProperties(
            true, TEST_FRONTEND_URL, TEST_FROM_ADDRESS,
            new AppConfigResponse(
                    TEST_TRUSTED_DEVICE_DAYS, TEST_RESEND_COOLDOWN_SECS, TEST_MAGIC_LINK_EXPIRY_MINS,
                    new ValidationProperties(
                            "^[sS]\\d{7}@ed\\.ac\\.uk$", 8, 128,
                            "^(?=.*[A-Z])(?=.*[^a-zA-Z]).+$",
                            "Must contain at least one uppercase letter and one non-alphabetic character"
                    )
            )
    );

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, trustedDeviceRepository,
                magicLinkService, passwordEncoder, TEST_CONFIG
        );
        request = new MockHttpServletRequest();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Nested
    class Register {

        @Test
        void register_hashesPasswordAndSendsVerificationEmail() {
            String email = "s1234567@ed.ac.uk";
            String password = "securePassword123";

            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.register(email, password);

            // Verify user saved with hashed password (not plaintext)
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getEmail()).isEqualTo(email);
            assertThat(savedUser.getPasswordHash()).isNotEqualTo(password);
            assertThat(passwordEncoder.matches(password, savedUser.getPasswordHash())).isTrue();
            assertThat(savedUser.isEmailVerified()).isFalse();

            // Verify magic link generation triggered via MagicLinkService
            verify(magicLinkService).sendVerificationEmail(eq(email));
        }

        @Test
        void register_withVerifiedExistingUser_throwsEmailAlreadyRegistered() {
            String email = "s1234567@ed.ac.uk";
            User user = new User(email);
            user.markEmailVerified();
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.register(email, "Password123"))
                    .isInstanceOf(EmailAlreadyRegisteredException.class);

            verify(userRepository, never()).save(any());
        }

        @Test
        void register_withUnverifiedExistingUser_resendsVerificationAndUpdatesPassword() {
            String email = "s1234567@ed.ac.uk";
            String newPassword = "newPassword123";
            User existingUser = new User(email, "oldHashedPassword");
            // emailVerified is false by default

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(existingUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.register(email, newPassword);

            // Verify password updated
            assertThat(existingUser.getPasswordHash()).isNotEqualTo("oldHashedPassword");
            assertThat(passwordEncoder.matches(newPassword, existingUser.getPasswordHash())).isTrue();

            verify(userRepository).save(existingUser);
            
            // Verify new magic link generated and sent
            verify(magicLinkService).sendVerificationEmail(eq(email));
        }
    }

    // ── Email Verification ────────────────────────────────────────────────────

    @Nested
    class VerifyEmail {

        @Test
        void verifyEmail_withValidToken_returnsUserInfoAndMarksUserAsVerifiedAndCreatesSession() {
            String token = "valid-token";
            String email = "s1234567@ed.ac.uk";
            User user = new User(email, "hashed");
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

            when(magicLinkService.validateAndConsumeToken(token, MagicLinkPurpose.EMAIL_VERIFICATION))
                    .thenReturn(user);

            UserInfoResponse result = authService.verifyRegistrationEmail(token, request);

            assertThat(user.isEmailVerified()).isTrue();
            assertThat(result.id()).isEqualTo(user.getId());
            assertThat(result.email()).isEqualTo(email);
            verify(userRepository).save(user);
            assertSessionCreatedFor(user);
        }

        // Invalid token handling is delegated to MagicLinkService.
        @Test
        void verifyEmail_withInvalidToken_delegatesExceptionsAndDoesNotCreateSession() {
            String token = "expired-token";
            when(magicLinkService.validateAndConsumeToken(token, MagicLinkPurpose.EMAIL_VERIFICATION))
                    .thenThrow(new InvalidTokenException("Invalid token"));

            assertThatThrownBy(() -> authService.verifyRegistrationEmail(token, request))
                    .isInstanceOf(InvalidTokenException.class);

            assertNoSessionCreated();
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Nested
    class Login {

        @Test
        void login_withCorrectCredentialsAndNoTrustedDevice_sends2faAndReturnsEmptyAndDoesNotCreateSession() {
            String email = "s1234567@ed.ac.uk";
            String password = "Password123";
            User user = createVerifiedUser(email, password);

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

            Optional<UserInfoResponse> result = authService.login(email, password, null, request);

            assertThat(result).isEmpty();
            verify(magicLinkService).send2faEmail(eq(email));
            assertNoSessionCreated();
        }

        @Test
        void login_withCorrectCredentialsAndValidTrustedDevice_returnsUserInfoAndCreatesSession() {
            String email = "s1234567@ed.ac.uk";
            String password = "Password123";
            User user = createVerifiedUser(email, password);
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

            TrustedDevice device = new TrustedDevice(user, TokenHasher.hash("device-token"), "Test", OffsetDateTime.now().plusDays(10));

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(trustedDeviceRepository.findByDeviceTokenAndUserId(TokenHasher.hash("device-token"), user.getId())).thenReturn(Optional.of(device));

            Optional<UserInfoResponse> result = authService.login(email, password, "device-token", request);

            assertThat(result).isPresent();
            assertThat(result.get().email()).isEqualTo(email);
            verify(magicLinkService, never()).send2faEmail(anyString()); // No 2FA email
            assertSessionCreatedFor(user);
        }

        @Test
        void login_withWrongPassword_throwsInvalidCredentialsAndDoesNotCreateSession() {
            String email = "s1234567@ed.ac.uk";
            User user = createVerifiedUser(email, "correctPassword");

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(email, "wrongPassword", null, request))
                    .isInstanceOf(InvalidCredentialsException.class);

            assertNoSessionCreated();
        }

        @Test
        void login_withNonExistentEmail_throwsInvalidCredentialsAndDoesNotCreateSession() {
            when(userRepository.findByEmail("s0000000@ed.ac.uk")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login("s0000000@ed.ac.uk", "any", null, request))
                    .isInstanceOf(InvalidCredentialsException.class);
            assertNoSessionCreated();
        }

        @Test
        void login_withUnverifiedEmail_throwsEmailNotVerifiedAndDoesNotCreateSession() {
            String email = "s1234567@ed.ac.uk";
            String password = "Password123";
            User user = new User(email, passwordEncoder.encode(password));
            // emailVerified is false by default

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(email, password, null, request))
                    .isInstanceOf(EmailNotVerifiedException.class);

            assertNoSessionCreated();
        }

        @Test
        void login_withExpiredTrustedDevice_sends2faAndDoesNotCreateSession() {
            String email = "s1234567@ed.ac.uk";
            String password = "Password123";
            User user = createVerifiedUser(email, password);
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

            TrustedDevice expired = new TrustedDevice(user, TokenHasher.hash("expired-device"), "Test", OffsetDateTime.now().minusDays(1));

            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            when(trustedDeviceRepository.findByDeviceTokenAndUserId(TokenHasher.hash("expired-device"), user.getId())).thenReturn(Optional.of(expired));

            Optional<UserInfoResponse> result = authService.login(email, password, "expired-device", request);

            assertThat(result).isEmpty();
            verify(magicLinkService).send2faEmail(eq(email));
            assertNoSessionCreated();
        }
    }

    // ── 2FA Verification ──────────────────────────────────────────────────────

    @Nested
    class Verify2fa {

        @Test
        void verify2fa_withValidToken_returnsUserInfoAndCreatesSession() {
            String token = "valid-token";
            String email = "s1234567@ed.ac.uk";
            User user = createVerifiedUser(email, "Password123");
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

            when(magicLinkService.validateAndConsumeToken(token, MagicLinkPurpose.LOGIN_2FA))
                    .thenReturn(user);

            UserInfoResponse result = authService.verify2fa(token, request);

            assertThat(result.email()).isEqualTo(email);
            assertSessionCreatedFor(user);
        }

        @Test
        void verify2fa_withInvalidToken_throwsAndDoesNotCreateSession() {
            String token = "bad-token";
            when(magicLinkService.validateAndConsumeToken(token, MagicLinkPurpose.LOGIN_2FA))
                    .thenThrow(new InvalidTokenException("Invalid token"));

            assertThatThrownBy(() -> authService.verify2fa(token, request))
                    .isInstanceOf(InvalidTokenException.class);

            assertNoSessionCreated();
        }
    }

    // ── Resend Verification ───────────────────────────────────────────────────

    @Nested
    class ResendVerification {

        @Test
        void resendVerification_forUnverifiedUser_sendsNewEmail() {
            String email = "s1234567@ed.ac.uk";
            User user = new User(email); // emailVerified is false
            
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            
            authService.resendVerification(email);
            
            verify(magicLinkService).resendVerificationEmail(eq(email));
        }

        @Test
        void resendVerification_forVerifiedUser_doesNothing() {
            String email = "s1234567@ed.ac.uk";
            User user = new User(email);
            user.markEmailVerified();
            
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            
            authService.resendVerification(email);
            
            verify(magicLinkService, never()).resendVerificationEmail(anyString());
        }

        @Test
        void resendVerification_forNonExistentEmail_doesNothing() {
            String email = "s0000000@ed.ac.uk";
            when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
            
            authService.resendVerification(email);
            
            verify(magicLinkService, never()).resendVerificationEmail(anyString());
        }

        // Cooldown checks are delegated to MagicLinkService
        @Test
        void resendVerification_bubblesExceptionsFromMagicLinkService() {
            String email = "s1234567@ed.ac.uk";
            User user = new User(email);
            
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
            doThrow(new ResendTooSoonException()).when(magicLinkService).resendVerificationEmail(email);
            
            assertThatThrownBy(() -> authService.resendVerification(email))
                    .isInstanceOf(ResendTooSoonException.class);
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Nested
    class Logout {

        @Test
        void logout_withExistingSession_invalidatesSessionAndClearsContext() {
            MockHttpSession session = new MockHttpSession();
            request.setSession(session);
            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());

            authService.logout(request);

            assertThat(session.isInvalid()).isTrue();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void logout_withoutSession_doesNotThrowOrCreateSession() {
            authService.logout(request);

            assertThat(request.getSession(false)).isNull();
        }
    }

    // ── User Management ──────────────────────────────────────────────────────

    @Nested
    class UpdateProfile {

        @Test
        void updateProfile_withExistingUser_savesNamesAndReturnsUserInfo() {
            UUID id = UUID.randomUUID();
            User user = createVerifiedUser("s1234567@ed.ac.uk", "Password123");
            ReflectionTestUtils.setField(user, "id", id);
            when(userRepository.findById(id)).thenReturn(Optional.of(user));

            UserInfoResponse result = authService.updateProfile(id, "John", "Doe");

            assertThat(result.id()).isEqualTo(id);
            assertThat(result.firstName()).isEqualTo("John");
            assertThat(result.lastName()).isEqualTo("Doe");
            verify(userRepository).save(user);
        }

        @Test
        void updateProfile_withUnknownUser_throwsIllegalArgumentAndSavesNothing() {
            UUID id = UUID.randomUUID();
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.updateProfile(id, "John", "Doe"))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(userRepository, never()).save(any());
        }
    }

    // ── Trusted Devices ──────────────────────────────────────────────────────

    @Nested
    class CreateTrustedDevice {

        private User user;

        @BeforeEach
        void setUpUser() {
            user = createVerifiedUser("s1234567@ed.ac.uk", "Password123");
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
            when(trustedDeviceRepository.save(any(TrustedDevice.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        void createTrustedDevice_withNoExistingToken_generatesFreshTokenAndCreatesDevice() {
            when(trustedDeviceRepository.findByDeviceTokenAndUserId(any(), any())).thenReturn(Optional.empty());

            String token = authService.createTrustedDevice(user, "Mozilla/5.0 (Macintosh; Intel Mac OS X) Chrome/120.0", null);

            assertThat(token).isNotBlank();
            verify(trustedDeviceRepository, never()).existsByDeviceToken(any());

            ArgumentCaptor<TrustedDevice> captor = ArgumentCaptor.forClass(TrustedDevice.class);
            verify(trustedDeviceRepository).save(captor.capture());
            assertThat(captor.getValue().getUser()).isEqualTo(user);
            assertThat(captor.getValue().getDeviceToken()).isEqualTo(TokenHasher.hash(token));
        }

        @Test
        void createTrustedDevice_withUnknownExistingToken_generatesFreshTokenAndCreatesDevice() {
            String unknownToken = "unknown-device-token";
            when(trustedDeviceRepository.existsByDeviceToken(TokenHasher.hash(unknownToken))).thenReturn(false);
            when(trustedDeviceRepository.findByDeviceTokenAndUserId(any(), any())).thenReturn(Optional.empty());

            String token = authService.createTrustedDevice(user, "Mozilla/5.0 Chrome/120.0", unknownToken);

            assertThat(token).isNotEqualTo(unknownToken);

            ArgumentCaptor<TrustedDevice> captor = ArgumentCaptor.forClass(TrustedDevice.class);
            verify(trustedDeviceRepository).save(captor.capture());
            assertThat(captor.getValue().getDeviceToken()).isEqualTo(TokenHasher.hash(token));
        }

        @Test
        void createTrustedDevice_withKnownTokenForSameUser_refreshesExpiryWithoutNewRow() {
            String existingToken = "known-device-token";
            TrustedDevice existingDevice = new TrustedDevice(
                    user, TokenHasher.hash(existingToken), "Old device", OffsetDateTime.now().minusDays(1));

            when(trustedDeviceRepository.existsByDeviceToken(TokenHasher.hash(existingToken))).thenReturn(true);
            when(trustedDeviceRepository.findByDeviceTokenAndUserId(TokenHasher.hash(existingToken), user.getId()))
                    .thenReturn(Optional.of(existingDevice));

            String token = authService.createTrustedDevice(user, "Mozilla/5.0 Chrome/120.0", existingToken);

            assertThat(token).isEqualTo(existingToken);
            assertThat(existingDevice.getExpiresAt()).isAfter(OffsetDateTime.now());
            verify(trustedDeviceRepository, times(1)).save(any(TrustedDevice.class));
            verify(trustedDeviceRepository).save(existingDevice);
        }

        @Test
        void createTrustedDevice_withTokenKnownToAnotherUser_reusesRawTokenAcrossUsers() {
            // A raw device-token cookie that is already known in the system (because 
            // it belongs to a different user's trusted device) is reused as-is and 
            // attached to this user too - the same raw cookie value then bypasses 2FA 
            // for both accounts.
            String sharedRawToken = "another-users-cookie-value";

            when(trustedDeviceRepository.existsByDeviceToken(TokenHasher.hash(sharedRawToken))).thenReturn(true);
            when(trustedDeviceRepository.findByDeviceTokenAndUserId(TokenHasher.hash(sharedRawToken), user.getId()))
                    .thenReturn(Optional.empty());

            String token = authService.createTrustedDevice(user, "Mozilla/5.0 Chrome/120.0", sharedRawToken);

            assertThat(token).isEqualTo(sharedRawToken);

            ArgumentCaptor<TrustedDevice> captor = ArgumentCaptor.forClass(TrustedDevice.class);
            verify(trustedDeviceRepository).save(captor.capture());
            assertThat(captor.getValue().getUser()).isEqualTo(user);
            assertThat(captor.getValue().getDeviceToken()).isEqualTo(TokenHasher.hash(sharedRawToken));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertSessionCreatedFor(User user) {
        MockHttpSession session = (MockHttpSession) request.getSession(false);
        assertThat(session).isNotNull();
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context).isNotNull();
        assertThat(context.getAuthentication().getName()).isEqualTo(user.getId().toString());
        assertThat(context.getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_USER");
    }

    private void assertNoSessionCreated() {
        assertThat(request.getSession(false)).isNull();
    }

    private User createVerifiedUser(String email, String rawPassword) {
        User user = new User(email, passwordEncoder.encode(rawPassword));
        user.markEmailVerified();
        return user;
    }
}
