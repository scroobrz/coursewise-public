package com.coursewise;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared test-data generators and response helpers used across integration tests.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    // Sequential rather than random: guarantees no two tests in a JVM share an email
    // (stays 7 digits up to 9,999,999, so it always matches the email regex).
    private static final AtomicInteger EMAIL_SEQ = new AtomicInteger(1_000_000);

    public static String uniqueEdinburghEmail() {
        return "s" + EMAIL_SEQ.getAndIncrement() + "@ed.ac.uk";
    }

    /** All Set-Cookie header values of a response (empty list if none). */
    public static List<String> setCookies(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        return cookies == null ? List.of() : cookies;
    }

    public static String uniqueCourseCode(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String extractCookie(ResponseEntity<?> response, String cookieName) {
        return extractCookie(response.getHeaders(), cookieName);
    }

    public static String extractCookie(EntityExchangeResult<?> result, String cookieName) {
        return extractCookie(result.getResponseHeaders(), cookieName);
    }

    private static String extractCookie(HttpHeaders headers, String cookieName) {
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            throw new IllegalStateException("No SET_COOKIE header found");
        }
        return setCookies.stream()
                .filter(c -> c.startsWith(cookieName + "="))
                .findFirst()
                .map(c -> c.split(";")[0])
                .orElseThrow(() -> new IllegalStateException("Cookie " + cookieName + " not found"));
    }
}
