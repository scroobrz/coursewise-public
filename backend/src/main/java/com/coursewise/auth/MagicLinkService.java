package com.coursewise.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.util.List;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coursewise.auth.exception.InvalidTokenException;
import com.coursewise.auth.exception.ResendTooSoonException;
import com.coursewise.config.AppConfigProperties;

/**
 * Service class responsible for managing and distributing magic links.
 * <p>
 * This service handles the generation of secure tokens, their persistence as 
 * hashed {@link MagicLink} entities, and sending the corresponding raw tokens 
 * to users via email for authentication purposes (e.g., email verification and 2FA).
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MagicLinkService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;
    private final AppConfigProperties appConfig;
    private final MagicLinkRepository magicLinkRepository;
    private final UserRepository userRepository;

    // ── Email Sending ─────────────────────────────────────────────────────────

    /**
     * Generates a new magic link token for email verification and sends it to the user.
     *
     * @param toEmail The recipient's email address
     */
    public void sendVerificationEmail(String toEmail) {
        String rawToken = generateSecureToken();
        generateMagicLink(toEmail, rawToken, MagicLinkPurpose.EMAIL_VERIFICATION);
        String link = appConfig.frontendUrl() + "/auth/verify-email?token=" + rawToken;
        sendMagicLinkEmail(toEmail, link);
        log.info("Verification email sent");
    }

    /**
     * Resends the verification email, enforcing a cooldown period to prevent spam.
     *
     * @param toEmail The recipient's email address
     * @throws ResendTooSoonException if the cooldown period has not elapsed
     */
    public void resendVerificationEmail(String toEmail) {
        // Rate-limit check: enforce cooldown
        magicLinkRepository.findFirstByEmailOrderByCreatedAtDesc(toEmail)
                .ifPresent(recentLink -> {
                    if (recentLink.getCreatedAt().plusSeconds(appConfig.shared().resendCooldownSeconds())
                            .isAfter(OffsetDateTime.now())) {
                        throw new ResendTooSoonException();
                    }
                });
        sendVerificationEmail(toEmail);
    }

    /**
     * Generates a new magic link token for 2FA login and sends it to the user.
     *
     * @param toEmail The recipient's email address
     */
    public void send2faEmail(String toEmail) {
        String rawToken = generateSecureToken();
        generateMagicLink(toEmail, rawToken, MagicLinkPurpose.LOGIN_2FA);
        String link = appConfig.frontendUrl() + "/auth/verify-2fa?token=" + rawToken;
        sendMagicLinkEmail(toEmail, link);
        log.info("2FA email sent");
    }

    /**
     * Internal helper to dispatch the actual email containing the magic link.
     *
     * @param toEmail The recipient's email address
     * @param link    The full URL including the raw token
     */
    private void sendMagicLinkEmail(String toEmail, String link) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Verify your email to log in to your CoursED account");
        message.setText(
                "Click the following link to verify your email address:\n\n"
                + link + "\n\n"
                + "This link will expire in " + appConfig.shared().magicLinkExpiryMinutes() + " minutes.\n\n"
        );
        message.setFrom(appConfig.fromAddress());

        mailSender.send(message);
    }

    // ── Token Validation ──────────────────────────────────────────────────────

    /**
     * Validates a magic link token: checks existence, purpose match, expiry, single-use, 
     * and returns the associated user. Marks the token as used upon successful validation.
     *
     * @param token           The raw token string to validate
     * @param expectedPurpose The expected {@link MagicLinkPurpose} of the token
     * @return The validated {@link User} entity
     * @throws InvalidTokenException if the token is invalid, expired, used, or for the wrong purpose
     */
    public User validateAndConsumeToken(String token, MagicLinkPurpose expectedPurpose) {
        MagicLink magicLink = magicLinkRepository.findByToken(TokenHasher.hash(token))
                .orElseThrow(() -> new InvalidTokenException("Invalid token"));

        if (magicLink.getPurpose() != expectedPurpose) {
            throw new InvalidTokenException("Invalid token");
        }

        if (magicLink.isUsed()) {
            throw new InvalidTokenException("This link has already been used");
        }
        if (magicLink.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException("This link has expired");
        }

        magicLink.markAsUsed();
        magicLinkRepository.save(magicLink);

        User user = userRepository.findByEmail(magicLink.getEmail())
                .orElseThrow(() -> new InvalidTokenException("No account found for this email"));

        return user;
    }

    // ── Internal Helpers ──────────────────────────────────────────────────────

    /**
     * Generates a new magic link for the given purpose, persisting only its
     * hash to the database, and returns the entity.
     *
     * @param email    The email associated with the link
     * @param rawToken The raw token before hashing
     * @param purpose  The purpose of this magic link
     * @return The saved {@link MagicLink} entity
     */
    private MagicLink generateMagicLink(String email, String rawToken, MagicLinkPurpose purpose) {
        // Invalidate all previous unused links for this email
        invalidateExistingLinks(email);

        MagicLink magicLink = new MagicLink(
                email,
                TokenHasher.hash(rawToken),
                purpose,
                OffsetDateTime.now().plusMinutes(appConfig.shared().magicLinkExpiryMinutes())
        );

        return magicLinkRepository.save(magicLink);
    }

    /**
     * Marks all existing unused magic links for the given email as used.
     * Ensures only the most recently generated link is valid at any time.
     *
     * @param email The email address to invalidate links for
     */
    private void invalidateExistingLinks(String email) {
        List<MagicLink> activeLinks = magicLinkRepository.findAllByEmailAndUsedFalse(email);
        if (!activeLinks.isEmpty()) {
            activeLinks.forEach(MagicLink::markAsUsed);
            magicLinkRepository.saveAll(activeLinks);
            log.debug("Invalidated {} existing magic link(s) for email", activeLinks.size());
        }
    }

    /**
     * Generates a secure, URL-safe base64-encoded 32-byte token.
     *
     * @return The generated secure token
     */
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
