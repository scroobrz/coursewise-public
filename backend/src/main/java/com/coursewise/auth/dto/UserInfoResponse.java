package com.coursewise.auth.dto;

import com.coursewise.auth.User;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Data Transfer Object representing the authenticated user's information.
 *
 * @param id The unique identifier of the user
 * @param email The user's university email
 * @param studentNumber The user's derived student number
 * @param firstName The user's first name
 * @param lastName The user's last name
 * @param createdAt The date and time the user's account was created
 */
public record UserInfoResponse(
        UUID id,
        String email,
        String studentNumber,
        String firstName,
        String lastName,
        OffsetDateTime createdAt
) {
    public static UserInfoResponse from(User user) {
        return new UserInfoResponse(
                user.getId(),
                user.getEmail(),
                user.getStudentNumber(),
                user.getFirstName(),
                user.getLastName(),
                user.getCreatedAt()
        );
    }
}
