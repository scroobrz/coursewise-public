package com.coursewise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Single configuration properties record for the Coursewise application, bound
 * from the {@code coursewise.config.*} keys in {@code application.yml}.
 *
 * <p>Fields are split into two logical groups:
 * <ul>
 *   <li><strong>Backend-only</strong> — used internally (e.g. cookie flags,
 *       magic-link construction) and never serialised in {@code AppConfigResponse}.</li>
 *   <li><strong>Frontend-shared</strong> ({@link #shared()}) — bound as an
 *       {@link AppConfigResponse}, which is also served verbatim via
 *       {@code GET /api/v1/config}. There's no separate projection step: the
 *       config-binding type and the wire type are the same record, so a new
 *       shared field only needs to be added in one place.</li>
 * </ul>
 *
 * @param cookieSecure Whether Set-Cookie responses use the {@code Secure} flag.
 *                      Disabled in the {@code dev} profile (plain HTTP).
 * @param frontendUrl  Base URL of the Angular app. Used to construct magic-link URLs.
 * @param fromAddress  The {@code From} address on outgoing magic-link emails.
 * @param shared       Everything exposed to the frontend via {@code GET /api/v1/config}.
 */
@ConfigurationProperties(prefix = "coursewise.config")
public record AppConfigProperties(
        @DefaultValue("true")  boolean cookieSecure,
        @DefaultValue("http://localhost:4200") String frontendUrl,
        @DefaultValue("noreply@coursewise.com")   String fromAddress,
        AppConfigResponse shared
) {
}
