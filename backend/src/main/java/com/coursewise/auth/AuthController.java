package com.coursewise.auth;

import com.coursewise.auth.dto.LoginRequest;
import com.coursewise.auth.dto.LoginResponse;
import com.coursewise.auth.dto.RegisterRequest;
import com.coursewise.auth.dto.ResendVerificationRequest;
import com.coursewise.auth.dto.UserInfoResponse;
import com.coursewise.auth.dto.VerifyEmailRequest;
import com.coursewise.auth.dto.UpdateProfileRequest;
import com.coursewise.config.AppConfigProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for password + magic-link 2FA authentication.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code POST /register} — create a new account</li>
 *   <li>{@code POST /verify-email} — verify email after registration</li>
 *   <li>{@code POST /login} — password login (step 1 of 2FA)</li>
 *   <li>{@code POST /verify-2fa} — magic link verification (step 2 of 2FA)</li>
 *   <li>{@code GET /me} — get current user info</li>
 *   <li>{@code PATCH /profile} — update profile</li>
 *   <li>{@code POST /logout} — end session</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String TRUSTED_DEVICE_COOKIE = "coursewise_td";
    private static final String SESSION_COOKIE = "JSESSIONID";

    private final AuthService authService;
    private final AppConfigProperties appConfig;

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Creates a new user account with email and password.
     * Sends a verification magic link to the provided email.
     *
     * @param request The registration request with email and password
     * @return 201 Created on success
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Received registration request");
        authService.register(request.email().toLowerCase(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // ── Configuration ─────────────────────────────────────────────────────────
    // Note: Public app config is now served by GET /api/v1/config (AppConfigController).

    // ── Email Verification (During registration) ────────────────────────────────────────────────────

    /**
     * Verifies a user's email address using the token from their verification email.
     * Creates a session automatically upon success.
     *
     * @param request The verification request containing the token
     * @param httpServletRequest The HTTP request (for session creation)
     * @return The authenticated user's info
     */
    @PostMapping("/verify-email")
    public ResponseEntity<UserInfoResponse> verifyRegistrationEmail(
            @Valid @RequestBody VerifyEmailRequest request,
            HttpServletRequest httpServletRequest) {
        log.info("Received email verification request");
        User user = authService.verifyRegistrationEmail(request.token());
        createSession(user, httpServletRequest);
        return ResponseEntity.ok(UserInfoResponse.from(user));
    }

    // ── Resend Verification ───────────────────────────────────────────────────

    /**
     * Resends a verification email for an unverified account.
     * <p>
     * Always returns 200 OK regardless of whether the email exists or was
     * already verified, to prevent email enumeration attacks.
     *
     * @param request The request containing the email address
     * @return 200 OK always
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        log.info("Received resend verification request");
        authService.resendVerification(request.email().toLowerCase());
        return ResponseEntity.ok().build();
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Authenticates a user by email and password (step 1 of 2FA).
     * <p>
     * If the browser has a valid trusted-device cookie, the user is fully
     * authenticated and a session is created. Otherwise, a 2FA magic link
     * is sent to their email.
     *
     * @param request            The login request with email and password
     * @param httpServletRequest The HTTP request (for session and cookie access)
     * @return A {@link LoginResponse} with status "authenticated" or "2fa_required"
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest) {
        log.info("Received login request");

        String deviceToken = extractTrustedDeviceCookie(httpServletRequest);

        Optional<User> authenticatedUser = authService.login(
                request.email().toLowerCase(),
                request.password(),
                deviceToken
        );

        if (authenticatedUser.isPresent()) {
            // Trusted device bypass — create session immediately
            User user = authenticatedUser.get();
            createSession(user, httpServletRequest);

            UserInfoResponse userInfo = UserInfoResponse.from(user);
            log.info("Login successful via trusted device");
            return ResponseEntity.ok(LoginResponse.authenticated(userInfo));
        }

        // 2FA required — email has been sent by the service
        log.info("2FA required, magic link sent");
        return ResponseEntity.ok(LoginResponse.twoFactorRequired());
    }

    // ── 2FA Verification ──────────────────────────────────────────────────────

    /**
     * Completes the 2FA login by verifying the magic link token.
     * Creates a session upon success. Trusting the device is a separate,
     * optional step handled by {@code POST /trust-device}.
     *
     * @param request            The 2FA verification request
     * @param httpServletRequest The HTTP request (for session creation)
     * @return The authenticated user's info
     */
    @PostMapping("/verify-2fa")
    public ResponseEntity<UserInfoResponse> verify2fa(
            @Valid @RequestBody VerifyEmailRequest request,
            HttpServletRequest httpServletRequest) {
        log.info("Received 2FA verification request");

        User user = authService.verify2fa(request.token());

        // Create session
        createSession(user, httpServletRequest);

        UserInfoResponse userInfo = UserInfoResponse.from(user);
        log.info("2FA verification successful, session created");
        return ResponseEntity.ok(userInfo);
    }

    // ── User Info ─────────────────────────────────────────────────────────────

    /**
     * Retrieves the currently authenticated user's information.
     * Requires an active session.
     *
     * @return The {@link UserInfoResponse} for the current user
     */
    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getCurrentUser(Authentication authentication) {
        String userId = authentication.getName();
        User user = authService.getUser(UUID.fromString(userId));
        return ResponseEntity.ok(UserInfoResponse.from(user));
    }

    /**
     * Updates the currently authenticated user's profile names.
     */
    @PatchMapping("/profile")
    public ResponseEntity<UserInfoResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request, Authentication authentication) {
        String userId = authentication.getName();
        User user = authService.updateProfile(UUID.fromString(userId), request.firstName(), request.lastName());
        return ResponseEntity.ok(UserInfoResponse.from(user));
    }

    /**
     * Trusts the current device for future logins.
     * Requires an active session.
     */
    @PostMapping("/trust-device")
    public ResponseEntity<Void> trustDevice(
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse,
            Authentication authentication) {
        log.info("Received trust device request");
        String userId = authentication.getName();
        User user = authService.getUser(UUID.fromString(userId));
        String userAgent = httpServletRequest.getHeader("User-Agent");

        String existingDeviceToken = extractTrustedDeviceCookie(httpServletRequest);
        String newDeviceToken = authService.createTrustedDevice(user, userAgent, existingDeviceToken);

        ResponseCookie cookie = ResponseCookie.from(TRUSTED_DEVICE_COOKIE, newDeviceToken)
                .httpOnly(true)
                .secure(appConfig.cookieSecure())
                .path("/api/v1/auth")
                .maxAge(appConfig.shared().trustedDeviceDays() * 24 * 60 * 60)
                .sameSite("Strict")
                .build();
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.info("Trusted device cookie set via /trust-device endpoint");

        return ResponseEntity.ok().build();
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    /**
     * Logs out the current user by invalidating their session, clearing the
     * security context, and clearing the session cookie.
     *
     * @param request  The HTTP request containing the session
     * @param response The HTTP response (for clearing the cookie)
     * @return An empty successful response
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, "")
                .httpOnly(true)
                .secure(appConfig.cookieSecure())
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private String extractTrustedDeviceCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> TRUSTED_DEVICE_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

}
