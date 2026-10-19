package com.coursewise.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link User} entities.
 */
public interface UserRepository extends JpaRepository<User, UUID> {
    /**
     * Finds a user by their unique email address.
     *
     * @param email The user's email address
     * @return An Optional containing the User if found
     */
    Optional<User> findByEmail(String email);
}
