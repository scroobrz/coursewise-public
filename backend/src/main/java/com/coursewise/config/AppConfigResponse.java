package com.coursewise.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The frontend-shared subset of application config: bound directly from
 * {@code coursewise.config.shared.*} in {@code application.yml} (as
 * {@link AppConfigProperties#shared()}), and served verbatim as the response
 * body for {@code GET /api/v1/config}.
 *
 * <p>Because this record is never nested under backend-only fields, there's no
 * projection step needed between "what's configured" and "what's exposed" —
 * this type is both. Backend-only fields ({@code cookieSecure}, {@code frontendUrl},
 * {@code fromAddress}) live as siblings on {@link AppConfigProperties} instead and
 * are structurally unreachable from here.
 *
 * @param trustedDeviceDays      How long a trusted-device bypass remains valid (in days).
 * @param resendCooldownSeconds  Minimum seconds between verification email resends.
 * @param magicLinkExpiryMinutes How long a magic-link token is valid before it expires.
 * @param validation             Input-validation rules for registration forms.
 */
public record AppConfigResponse(
        @DefaultValue("30") int trustedDeviceDays,
        @DefaultValue("60") int resendCooldownSeconds,
        @DefaultValue("15") int magicLinkExpiryMinutes,
        ValidationProperties validation
) {

    /**
     * Nested record holding all input-validation rules for the registration flow.
     *
     * @param emailRegex                Pattern a university student email must match.
     * @param passwordMinLength         Minimum password length in characters.
     * @param passwordMaxLength         Maximum password length in characters.
     * @param passwordRegex             Complexity regex the password must satisfy.
     * @param passwordRegexDescription  Human-readable description of the complexity rule,
     *                                  rendered verbatim as the Angular form error message.
     */
    public record ValidationProperties(
            @DefaultValue("^[sS]\\d{7}@ed\\.ac\\.uk$") String emailRegex,
            @DefaultValue("8")   int passwordMinLength,
            @DefaultValue("128") int passwordMaxLength,
            @DefaultValue("^(?=.*[A-Z])(?=.*[^a-zA-Z]).+$") String passwordRegex,
            @DefaultValue("Must contain at least one uppercase letter and one non-alphabetic character (number or symbol)")
            String passwordRegexDescription
    ) {}
}
