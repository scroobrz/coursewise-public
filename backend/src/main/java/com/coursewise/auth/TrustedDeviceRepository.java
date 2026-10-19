package com.coursewise.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link TrustedDevice} entities.
 */
public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, UUID> {

    /**
     * Finds a trusted device by its secure token and user ID.
     *
     * @param deviceToken The token stored in the client cookie
     * @param userId The ID of the user attempting to log in
     * @return An Optional containing the TrustedDevice if found
     */
    Optional<TrustedDevice> findByDeviceTokenAndUserId(String deviceToken, UUID userId);

    /**
     * Checks if a device token is already known in the system.
     * Used to prevent device token fixation attacks.
     *
     * @param deviceToken The token to check
     * @return true if the token exists
     */
    boolean existsByDeviceToken(String deviceToken);

    /**
     * Lists all trusted devices for a user (for managing/revoking devices).
     *
     * @param user The user whose devices to list
     * @return A list of trusted devices belonging to the user
     */
    List<TrustedDevice> findByUser(User user);

    /**
     * Deletes all trusted devices for a user (used during account deletion or "log out everywhere").
     *
     * @param user The user whose devices to revoke
     */
    void deleteByUser(User user);
}
