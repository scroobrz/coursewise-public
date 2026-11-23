package com.coursewise;

import com.coursewise.review.dto.ReviewRequest;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

/**
 * Shared parameter sources for parameterised tests, so the web-slice and integration
 * layers exercise exactly the same boundary values.
 * <p>
 * Reference from a test with a fully-qualified {@code @MethodSource}, e.g.
 * {@code @MethodSource("com.coursewise.TestParameters#invalidEmails")} (a bare name is not
 * resolved against another class, and is not found from a {@code @Nested} class either).
 * Sources deliberately exclude {@code null} and {@code ""}: combine with
 * {@code @NullAndEmptySource} (or {@code @EmptySource}) where those apply.
 */
public final class TestParameters {

    private TestParameters() {
    }

    /** Emails that must fail {@code ^[sS]\d{7}@ed\.ac\.uk$} (excluding null and empty). */
    public static Stream<String> invalidEmails() {
        return Stream.of(
                "   ",                        // blank
                "not-an-email",               // no @ or domain
                "s1234567@",                  // no domain
                "s1234567@ed.ac.uk.evil.com", // domain with suffix appended
                "s1234567@gmail.com",         // wrong domain
                "s1234567@ac.uk",             // truncated domain
                "s1234567@ED.AC.UK",          // capitalised domain
                "s123456@ed.ac.uk",           // 6 digits (too few)
                "s12345678@ed.ac.uk",         // 8 digits (too many)
                "sabcdefg@ed.ac.uk",          // non-digit id
                "x1234567@ed.ac.uk",          // wrong prefix letter
                "1234567@ed.ac.uk",           // missing prefix letter
                " s1234567@ed.ac.uk",         // leading whitespace
                "s1234567@ed.ac.uk ",         // trailing whitespace
                "s1234567@ed.ac.uk\n"         // trailing newline
        );
    }

    /** Passwords that violate the length (8-128) or complexity rules (excluding null and empty). */
    public static Stream<String> invalidPasswords() {
        return Stream.of(
                "        ",     // blank
                "Pass1",        // too short
                "Passwo1",      // 7 chars: just below min (8)
                "ThisIsMyPasswordAndItIsWaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaayyyyyyyyyyyyyyyyyyTooLong123",
                "WhereasThisPasswordIsOnlyJuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuustTooLong123", // 129 chars - just over the max (128)
                "password123",  // no uppercase
                "PasswordOnly", // no non-alphabetic character
                "PASSWORDONLY", // uppercase only, no non-alphabetic
                "12345678",     // no uppercase
                "!!!!!!!!"      // no uppercase
        );
    }

    /** Whitespace-only tokens (excluding null and empty). */
    public static Stream<String> blankTokens() {
        return Stream.of(" ", "   ", "\t", "\n");
    }

    /** First/last names that must be rejected (max length 50, no blanks); excludes null and empty. */
    public static Stream<String> invalidNames() {
        return Stream.of(
                "thisnameiswaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaayyytoolong",
                "whereasthisnameisonlyjuuuuuuuuuuuuuuuuuuuusttoolong", // 51 chars - just over the max (50)
                "    ",
                "\t",
                "\n"
        );
    }

    /** Service-level token rejection scenarios as (scenario, serviceMessage). */
    public static Stream<Arguments> rejectedTokenScenarios() {
        return Stream.of(
                Arguments.of("expired token", "This link has expired"),
                Arguments.of("used or superseded token", "This link has already been used"),
                Arguments.of("wrong-purpose token", "Invalid token"),
                Arguments.of("token with no corresponding user", "No account found for this email")
        );
    }

    /** Review payloads with a rating outside 1-5 or a missing recommend flag. */
    public static Stream<Arguments> invalidReviewRequests() {
        return Stream.of(
                Arguments.of(new ReviewRequest(6, 3, 3, 3, true, "body")),
                Arguments.of(new ReviewRequest(3, 6, 3, 3, true, "body")),
                Arguments.of(new ReviewRequest(3, 3, 6, 3, true, "body")),
                Arguments.of(new ReviewRequest(3, 0, 3, 6, true, "body")),
                Arguments.of(new ReviewRequest(-1, 3, 3, 3, true, "body")),
                Arguments.of(new ReviewRequest(3, -1, 3, 3, true, "body")),
                Arguments.of(new ReviewRequest(3, 3, -1, 3, true, "body")),
                Arguments.of(new ReviewRequest(3, 3, 3, -1, true, "body")),
                Arguments.of(new ReviewRequest(3, 3, 3, 3, null, "body"))
        );
    }
}
