package com.coursewise.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a registered user in the application.
 * Users authenticate via password + magic-link 2FA.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true)
    @NotBlank(message = "Email is required")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "student_number", nullable = false, unique = true)
    @NotBlank(message = "Student number is required")
    private String studentNumber;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Constructs a new User from their university email address.
     * The student number is automatically derived from the email prefix.
     *
     * @param email The user's valid university email
     */
    public User(String email) {
        this.email = email;
        this.studentNumber = email.split("@")[0];
        this.emailVerified = false;
    }

    /**
     * Constructs a new User with email and hashed password for registration.
     *
     * @param email        The user's valid university email
     * @param passwordHash The BCrypt-hashed password
     */
    public User(String email, String passwordHash) {
        this(email);
        this.passwordHash = passwordHash;
    }

    /**
     * Marks this user's email as verified after clicking the verification link.
     */
    public void markEmailVerified() {
        this.emailVerified = true;
    }

    /**
     * Updates the stored password hash. Used during idempotent re-registration
     * when an unverified user registers again with a (potentially different) password.
     *
     * @param passwordHash The new BCrypt-hashed password
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Updates the user's profile names.
     */
    public void setProfileNames(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    /**
     * JPA lifecycle callback to automatically set the creation timestamp.
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }
}
