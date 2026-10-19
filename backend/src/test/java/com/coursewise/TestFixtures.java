package com.coursewise;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import java.util.List;
import java.util.Random;

/**
 * Shared test-data generators and response helpers used across integration tests.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static String uniqueEdinburghEmail() {
        return "s" + (1000000 + new Random().nextInt(9000000)) + "@ed.ac.uk";
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
