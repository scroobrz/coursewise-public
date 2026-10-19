package com.coursewise.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link MagicLink} entities.
 */
public interface MagicLinkRepository extends JpaRepository<MagicLink, UUID> {
    /**
     * Finds a magic link by the SHA-256 hash of its token (see TokenHasher).
     *
     * @param token The hashed magic link token
     * @return An Optional containing the MagicLink if found
     */
    Optional<MagicLink> findByToken(String token);

    /**
     * Finds the most recently created magic link for a given email.
     * Used for rate-limit checks (see {@code coursewise.config.resend-cooldown-seconds}).
     *
     * @param email The email address
     * @return The most recent MagicLink for this email, if any
     */
    Optional<MagicLink> findFirstByEmailOrderByCreatedAtDesc(String email);

    /**
     * Finds all unused (not yet consumed) magic links for a given email.
     * Used to invalidate old links before issuing a new one.
     *
     * @param email The email address
     * @return All active, unused MagicLinks for this email
     */
    List<MagicLink> findAllByEmailAndUsedFalse(String email);
}
