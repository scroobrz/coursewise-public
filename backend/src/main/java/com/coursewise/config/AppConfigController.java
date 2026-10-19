package com.coursewise.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint that exposes application configuration required by the Angular
 * frontend. This endpoint is permit-listed in {@code SecurityConfig} and does not
 * require an active session.
 *
 * <p>Fetching this endpoint on app startup has two side effects the frontend relies on:
 * <ol>
 *   <li>It seeds the {@code XSRF-TOKEN} cookie used by CSRF protection.</li>
 *   <li>It provides validation rules (regex, lengths) so Angular form validators
 *       stay in sync with the backend without hardcoding any values.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class AppConfigController {

    private final AppConfigProperties config;

    /**
     * Returns the public application configuration.
     *
     * @return {@link AppConfigProperties#shared()} verbatim — backend-only fields
     *         are structurally unreachable from it, so there's nothing to filter.
     */
    @GetMapping
    public ResponseEntity<AppConfigResponse> getConfig() {
        return ResponseEntity.ok(config.shared());
    }
}
