package com.coursewise.auth;

import com.coursewise.auth.exception.InvalidTokenException;
import com.coursewise.auth.exception.ResendTooSoonException;
import com.coursewise.config.AppConfigProperties;
import com.coursewise.config.AppConfigResponse;
import com.coursewise.config.AppConfigResponse.ValidationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MagicLinkServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private MagicLinkRepository magicLinkRepository;
    @Mock private UserRepository userRepository;

    private MagicLinkService magicLinkService;
    private AppConfigProperties appConfig;

    // ── Shared test constants ─────────────────────────────────────────────────────────
    private static final int    TEST_TRUSTED_DEVICE_DAYS   = 30;
    private static final int    TEST_RESEND_COOLDOWN_SECS   = 60;
    private static final int    TEST_MAGIC_LINK_EXPIRY_MINS = 15;
    private static final String TEST_FRONTEND_URL           = "http://localhost:4200";
    private static final String TEST_FROM_ADDRESS           = "noreply@coursewise.com";

    @BeforeEach
    void setUp() {
        appConfig = new AppConfigProperties(
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
        magicLinkService = new MagicLinkService(mailSender, appConfig, magicLinkRepository, userRepository);
    }

    @Nested
    class SendVerificationEmail {

        @Test
        void sendVerificationEmail_generatesLinkInvalidatesOldAndSendsEmail() {
            String email = "s1234567@ed.ac.uk";

            MagicLink oldLink = new MagicLink(email, "old-hash", MagicLinkPurpose.EMAIL_VERIFICATION, OffsetDateTime.now().plusMinutes(5));
            when(magicLinkRepository.findAllByEmailAndUsedFalse(email)).thenReturn(List.of(oldLink));
            when(magicLinkRepository.save(any(MagicLink.class))).thenAnswer(inv -> inv.getArgument(0));

            magicLinkService.sendVerificationEmail(email);

            // Verify old links are invalidated
            assertThat(oldLink.isUsed()).isTrue();
            verify(magicLinkRepository).saveAll(List.of(oldLink));

            // Verify new link is created and saved
            ArgumentCaptor<MagicLink> linkCaptor = ArgumentCaptor.forClass(MagicLink.class);
            verify(magicLinkRepository).save(linkCaptor.capture());
            MagicLink savedLink = linkCaptor.getValue();
            assertThat(savedLink.getEmail()).isEqualTo(email);
            assertThat(savedLink.getPurpose()).isEqualTo(MagicLinkPurpose.EMAIL_VERIFICATION);

            // Verify email was sent containing the unhashed token, while the DB stored the hashed token
            ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(mailCaptor.capture());
            SimpleMailMessage sentEmail = mailCaptor.getValue();
            assertThat(sentEmail.getTo()).containsExactly(email);
            assertThat(sentEmail.getFrom()).isEqualTo(appConfig.fromAddress());
            assertThat(sentEmail.getText()).contains("/auth/verify-email?token=");

            // Extract raw token from email and ensure its hash matches the one in DB
            String emailText = sentEmail.getText();
            String tokenParam = emailText.substring(emailText.indexOf("?token=") + 7).split("\n")[0].trim();
            assertThat(savedLink.getToken()).isEqualTo(TokenHasher.hash(tokenParam));
        }
    }

    @Nested
    class ResendVerificationEmail {

        @Test
        void resendVerificationEmail_withinCooldown_throwsResendTooSoon() {
            String email = "s1234567@ed.ac.uk";

            MagicLink recentLink = new MagicLink(email, "token", MagicLinkPurpose.EMAIL_VERIFICATION, OffsetDateTime.now().plusMinutes(15));
            // Ensure createdAt is just a few seconds ago
            ReflectionTestUtils.setField(recentLink, "createdAt", OffsetDateTime.now().minusSeconds(10));
            when(magicLinkRepository.findFirstByEmailOrderByCreatedAtDesc(email)).thenReturn(Optional.of(recentLink));

            assertThatThrownBy(() -> magicLinkService.resendVerificationEmail(email))
                    .isInstanceOf(ResendTooSoonException.class);

            verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }

        @Test
        void resendVerificationEmail_afterCooldownExpired_sendsNewEmail() {
            String email = "s1234567@ed.ac.uk";

            MagicLink olderLink = new MagicLink(email, "token", MagicLinkPurpose.EMAIL_VERIFICATION, OffsetDateTime.now().plusMinutes(15));
            // Ensure createdAt is older than 60 seconds
            ReflectionTestUtils.setField(olderLink, "createdAt", OffsetDateTime.now().minusSeconds(65));
            when(magicLinkRepository.findFirstByEmailOrderByCreatedAtDesc(email)).thenReturn(Optional.of(olderLink));
            
            when(magicLinkRepository.save(any(MagicLink.class))).thenAnswer(inv -> inv.getArgument(0));

            magicLinkService.resendVerificationEmail(email);

            verify(magicLinkRepository).save(any(MagicLink.class));
            verify(mailSender).send(any(SimpleMailMessage.class));
        }
    }

    @Nested
    class Send2faEmail {

        @Test
        void send2faEmail_generatesLinkAndSendsEmail() {
            String email = "s1234567@ed.ac.uk";

            when(magicLinkRepository.save(any(MagicLink.class))).thenAnswer(inv -> inv.getArgument(0));

            magicLinkService.send2faEmail(email);

            verify(magicLinkRepository).save(any(MagicLink.class));

            ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(mailCaptor.capture());
            SimpleMailMessage sentEmail = mailCaptor.getValue();
            assertThat(sentEmail.getTo()).containsExactly(email);
            assertThat(sentEmail.getText()).contains("/auth/verify-2fa?token=");
        }
    }

    @Nested
    class ValidateAndConsumeToken {

        @Test
        void validate_withValidToken_consumesLinkAndReturnsUser() {
            String rawToken = "raw-token";
            String email = "s1234567@ed.ac.uk";
            MagicLink link = new MagicLink(email, TokenHasher.hash(rawToken), MagicLinkPurpose.EMAIL_VERIFICATION, OffsetDateTime.now().plusMinutes(5));
            User user = new User(email, "hashed");

            when(magicLinkRepository.findByToken(TokenHasher.hash(rawToken))).thenReturn(Optional.of(link));
            when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

            User result = magicLinkService.validateAndConsumeToken(rawToken, MagicLinkPurpose.EMAIL_VERIFICATION);

            assertThat(link.isUsed()).isTrue();
            assertThat(result).isEqualTo(user);
            verify(magicLinkRepository).save(link);
        }

        @Test
        void validate_withExpiredToken_throwsInvalidToken() {
            String rawToken = "expired-token";
            MagicLink link = new MagicLink("s1234567@ed.ac.uk", TokenHasher.hash(rawToken), MagicLinkPurpose.EMAIL_VERIFICATION, OffsetDateTime.now().minusMinutes(5));

            when(magicLinkRepository.findByToken(TokenHasher.hash(rawToken))).thenReturn(Optional.of(link));

            assertThatThrownBy(() -> magicLinkService.validateAndConsumeToken(rawToken, MagicLinkPurpose.EMAIL_VERIFICATION))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void validate_withUsedToken_throwsInvalidToken() {
            String rawToken = "used-token";
            MagicLink link = new MagicLink("s1234567@ed.ac.uk", TokenHasher.hash(rawToken), MagicLinkPurpose.EMAIL_VERIFICATION, OffsetDateTime.now().plusMinutes(5));
            link.markAsUsed();

            when(magicLinkRepository.findByToken(TokenHasher.hash(rawToken))).thenReturn(Optional.of(link));

            assertThatThrownBy(() -> magicLinkService.validateAndConsumeToken(rawToken, MagicLinkPurpose.EMAIL_VERIFICATION))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void validate_withWrongPurpose_throwsInvalidToken() {
            String rawToken = "2fa-token";
            MagicLink link = new MagicLink("s1234567@ed.ac.uk", TokenHasher.hash(rawToken), MagicLinkPurpose.LOGIN_2FA, OffsetDateTime.now().plusMinutes(5));

            when(magicLinkRepository.findByToken(TokenHasher.hash(rawToken))).thenReturn(Optional.of(link));

            // Passing EMAIL_VERIFICATION instead of LOGIN_2FA
            assertThatThrownBy(() -> magicLinkService.validateAndConsumeToken(rawToken, MagicLinkPurpose.EMAIL_VERIFICATION))
                    .isInstanceOf(InvalidTokenException.class);
        }
        
        @Test
        void validate_withNonExistentToken_throwsInvalidToken() {
            when(magicLinkRepository.findByToken(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> magicLinkService.validateAndConsumeToken("bad-token", MagicLinkPurpose.EMAIL_VERIFICATION))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }
}
