package com.coursewise.auth.dto;

/**
 * Response for the login endpoint. Contains the authentication status
 * and optionally the user info (only present when fully authenticated).
 *
 * @param status "authenticated" when login is complete (trusted device bypass),
 *               or "2fa_required" when a magic-link verification is needed
 * @param user   The authenticated user's info, or null when 2FA is still pending
 */
public record LoginResponse(
        String status,
        UserInfoResponse user
) {
    public static final String STATUS_AUTHENTICATED = "authenticated";
    public static final String STATUS_2FA_REQUIRED = "2fa_required";

    public static LoginResponse authenticated(UserInfoResponse user) {
        return new LoginResponse(STATUS_AUTHENTICATED, user);
    }

    public static LoginResponse twoFactorRequired() {
        return new LoginResponse(STATUS_2FA_REQUIRED, null);
    }
}
