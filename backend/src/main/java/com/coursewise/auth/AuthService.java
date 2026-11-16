package com.coursewise.auth;

import com.coursewise.auth.dto.UserInfoResponse;
import com.coursewise.auth.exception.EmailAlreadyRegisteredException;
import com.coursewise.auth.exception.EmailNotVerifiedException;
import com.coursewise.auth.exception.InvalidCredentialsException;
import com.coursewise.auth.exception.InvalidTokenException;
import com.coursewise.auth.exception.ResendTooSoonException;
import com.coursewise.config.AppConfigProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class handling password + magic-link 2FA authentication.
 * <p>
 * Auth flow:
 * <ol>
 *   <li>User registers with email + password → verification magic link sent</li>
 *   <li>User clicks verification link → email marked as verified</li>
 *   <li>User logs in with email + password:
 *       <ul>
 *         <li>If a valid trusted-device token is presented → session created immediately</li>
 *         <li>Otherwise → 2FA magic link sent, user must click to complete login</li>
 *       </ul>
 *   </li>
 * </ol>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final MagicLinkService magicLinkService;
    private final PasswordEncoder passwordEncoder;
    private final AppConfigProperties appConfig;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers a new user with email and password.
     * Hashes the password, creates the user with {@code emailVerified=false},
     * and sends a verification magic link to their email.
     *
     * @param email    The university email address
     * @param password The raw (plaintext) password
     * @throws EmailAlreadyRegisteredException if the email is already registered
     */
    public void register(String email, String password) {

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.isEmailVerified()) {
                // Truly taken — can't re-register
                throw new EmailAlreadyRegisteredException();
            }
            // Unverified account: update password hash and resend verification
            user.setPasswordHash(passwordEncoder.encode(password));
            userRepository.save(user);
            magicLinkService.sendVerificationEmail(email);
            log.info("Idempotent re-registration: password updated, verification email resent to email of user {}", user.getId());
            return;
        }

        String hashedPassword = passwordEncoder.encode(password);
        User user = new User(email, hashedPassword);
        userRepository.save(user);

        magicLinkService.sendVerificationEmail(email);
        log.info("Registration successful, verification email sent to email of user {}", user.getId());
    }

    // ── Email Verification ────────────────────────────────────────────────────

    /**
     * Verifies a user's email address using a magic link token sent during
     * registration. Marks the user as {@code emailVerified=true} and creates 
     * a session.
     * <p>
     * Returns the user info for the newly verified user.
     *
     * @param token The magic link token from the verification email
     * @param httpServletRequest The HTTP request to create a session for
     * @return A {@link UserInfoResponse} for the verified user
     * @throws InvalidTokenException if the token is invalid, expired, or used
     */
    public UserInfoResponse verifyRegistrationEmail(String token, HttpServletRequest httpServletRequest) {
        log.info("Email verification attempt");
        User user = magicLinkService.validateAndConsumeToken(token, MagicLinkPurpose.EMAIL_VERIFICATION);

        user.markEmailVerified();
        userRepository.save(user);
        createSession(user, httpServletRequest);

        log.info("Email verified successfully, session created for user: {}", user.getId());
        return UserInfoResponse.from(user);
    }

    // ── Login (Password Step) ─────────────────────────────────────────────────

    /**
     * Authenticates a user by email and password (step 1 of 2FA).
     * <p>
     * If a valid trusted-device token is provided, the user is fully
     * authenticated immediately (2FA bypass) and a session is created. 
     * Otherwise, a 2FA magic link is sent to their email.
     *
     * @param email       The user's email
     * @param password    The user's password
     * @param deviceToken An optional trusted device token from a cookie
     * @param httpServletRequest The HTTP request to create a session for
     * @return A {@link UserInfoResponse} for the verified user if trusted 
     * device is valid, or {@link Optional#empty()} if 2FA is required
     * @throws InvalidCredentialsException if email/password is wrong
     * @throws EmailNotVerifiedException   if the user hasn't verified their email
     */
    public Optional<UserInfoResponse> login(String email, String password, String deviceToken, HttpServletRequest httpServletRequest) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        // Check for trusted device bypass
        if (deviceToken != null && !deviceToken.isBlank()) {
            String hashedToken = TokenHasher.hash(deviceToken);
            Optional<TrustedDevice> trustedDevice = trustedDeviceRepository.findByDeviceTokenAndUserId(hashedToken, user.getId());

            if (trustedDevice.isPresent() && !trustedDevice.get().isExpired()) {
                log.info("Trusted device {} found, skipping 2FA - login successful and session created for user {}", trustedDevice.get().getId(), user.getId());
                createSession(user, httpServletRequest);
                return Optional.of(UserInfoResponse.from(user));
            }
        }

        magicLinkService.send2faEmail(email);
        log.info("2FA required, magic link sent to email of user {}", user.getId());

        return Optional.empty();
    }

    // ── 2FA Verification ──────────────────────────────────────────────────────

    /**
     * Completes the 2FA login step by verifying the magic link token.
     * Creates a session on success.
     * <p>
     *
     * @param token The magic link token from the 2FA email
     * @param httpServletRequest The HTTP request to create a session for
     * @return A {@link UserInfoResponse} for the verified user
     * @throws InvalidTokenException if the token is invalid, expired, or used
     */
    public UserInfoResponse verify2fa(String token, HttpServletRequest httpServletRequest) {
        User user = magicLinkService.validateAndConsumeToken(token, MagicLinkPurpose.LOGIN_2FA);
        createSession(user, httpServletRequest);
        log.info("2FA verification successful, session created for user: {}", user.getId());
        return UserInfoResponse.from(user);
    }

    // ── Resend Verification ───────────────────────────────────────────────────

    /**
     * Resends a verification email to an unverified user.
     * <p>
     * Deliberately does nothing (and logs a warning) if the email is not
     * registered or is already verified, to prevent email enumeration.
     * <p>
     * Enforces the configured cooldown ({@code coursewise.config.resend-cooldown-seconds})
     * between resend requests.
     *
     * @param email The email address to resend verification to
     * @throws ResendTooSoonException if the cooldown has not elapsed
     */
    public void resendVerification(String email) {
        log.info("Resend verification request");

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.warn("Resend verification requested for non-existent email");
            return;
        }

        User user = userOpt.get();
        if (user.isEmailVerified()) {
            log.warn("Resend verification requested for already-verified email");
            return;
        }
        
        magicLinkService.resendVerificationEmail(email);
        log.info("Verification email resent to email of user {}", user.getId());
    }

    /**
     * Creates a new trusted device token for the user.
     *
     * @param user      The authenticated user
     * @param userAgent The User-Agent string from the HTTP request
     * @param existingDeviceToken An optional existing trusted device token from a cookie
     * @return The raw trusted device token to be returned to the client
     */
    public String createTrustedDevice(User user, String userAgent, String existingDeviceToken) {
        String deviceToken;
        String hashedToken;

        if (existingDeviceToken != null && !existingDeviceToken.isBlank()) {
            hashedToken = TokenHasher.hash(existingDeviceToken);
            // Check if this token is actually a valid, known token in our system
            boolean isValidToken = trustedDeviceRepository.existsByDeviceToken(hashedToken);
            
            if (isValidToken) {
                deviceToken = existingDeviceToken;
            } else {
                // Client provided an unknown token (could be fixation or just expired/deleted)
                // Generate a fresh one.
                deviceToken = generateSecureToken();
                hashedToken = TokenHasher.hash(deviceToken);
            }
        } else {
            deviceToken = generateSecureToken();
            hashedToken = TokenHasher.hash(deviceToken);
        }
        
        Optional<TrustedDevice> existing = trustedDeviceRepository.findByDeviceTokenAndUserId(hashedToken, user.getId());
        if (existing.isPresent()) {
            existing.get().renew(OffsetDateTime.now().plusDays(appConfig.shared().trustedDeviceDays()));
            TrustedDevice trustedDevice = trustedDeviceRepository.save(existing.get());
            log.info("Trusted device {} expiry refreshed for user {}", trustedDevice.getId(), user.getId());
            return deviceToken;
        }

        String deviceName = parseDeviceName(userAgent);
        TrustedDevice device = new TrustedDevice(
                user,
                hashedToken,
                deviceName,
                OffsetDateTime.now().plusDays(appConfig.shared().trustedDeviceDays())
        );
        TrustedDevice trustedDevice = trustedDeviceRepository.save(device);
        log.info("Trusted device {} created for user {}", trustedDevice.getId(), user.getId());
        return deviceToken;
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    /**
     * Logs out the user by invalidating their session and clearing the security context.
     *
     * @param httpServletRequest The HTTP request containing the session
     */
    public void logout(HttpServletRequest httpServletRequest) {
        HttpSession session = httpServletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        log.info("User logged out, session invalidated and security context cleared");
    }

    // ── User Management ───────────────────────────────────────────────────────

    /**
     * Retrieves a user by their unique identifier.
     *
     * @param id The UUID of the user
     * @return The requested {@link User}
     * @throws IllegalArgumentException if the user is not found
     */
    @Transactional(readOnly = true)
    public User getUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    /**
     * Updates a user's profile names.
     * 
     * @param id The UUID of the user
     * @param firstName The new first name
     * @param lastName The new last name
     * @return A {@link UserInfoResponse} with the updated user info
     * @throws IllegalArgumentException if the user is not found
     */
    public UserInfoResponse updateProfile(UUID id, String firstName, String lastName) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        user.setProfileNames(firstName, lastName);
        log.info("Profile names updated for user {}", user.getId());
        userRepository.save(user);
        return UserInfoResponse.from(user);
    }

    // ── Internal Helpers ──────────────────────────────────────────────────────

    private void createSession(User user, HttpServletRequest httpServletRequest) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getId().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = httpServletRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
    
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Extracts a human-readable device name from the User-Agent header.
     * Falls back to "Unknown device" if parsing fails.
     */
    private String parseDeviceName(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        // Simple heuristic: extract browser and OS
        if (userAgent.contains("Chrome") && !userAgent.contains("Edg")) {
            return "Chrome" + extractOs(userAgent);
        } else if (userAgent.contains("Firefox")) {
            return "Firefox" + extractOs(userAgent);
        } else if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) {
            return "Safari" + extractOs(userAgent);
        } else if (userAgent.contains("Edg")) {
            return "Edge" + extractOs(userAgent);
        }
        return "Unknown browser" + extractOs(userAgent);
    }

    private String extractOs(String userAgent) {
        if (userAgent.contains("Mac OS")) {
            return " on macOS";
        } else if (userAgent.contains("Windows")) {
            return " on Windows";
        } else if (userAgent.contains("Linux")) {
            return " on Linux";
        } else if (userAgent.contains("Android")) {
            return " on Android";
        } else if (userAgent.contains("iPhone") || userAgent.contains("iPad")) {
            return " on iOS";
        }
        return "";
    }
}