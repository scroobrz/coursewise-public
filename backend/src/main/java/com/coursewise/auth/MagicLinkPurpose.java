package com.coursewise.auth;

/**
 * The action a {@link MagicLink} token authorizes. Prevents a token issued
 * for one purpose (e.g. email verification) from being redeemed for another
 * (e.g. completing 2FA login).
 */
public enum MagicLinkPurpose {
    EMAIL_VERIFICATION,
    LOGIN_2FA
}
