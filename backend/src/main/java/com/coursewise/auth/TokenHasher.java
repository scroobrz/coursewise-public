package com.coursewise.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Hashes high-entropy bearer tokens (magic links, trusted-device tokens)
 * before they are persisted, so that a database breach alone doesn't yield
 * tokens usable to impersonate a user. SHA-256 is appropriate here — unlike
 * passwords, these tokens are already uniformly random and not guessable.
 */
public final class TokenHasher {

    private TokenHasher() {}

    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
