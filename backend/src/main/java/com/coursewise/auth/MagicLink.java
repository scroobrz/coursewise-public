package com.coursewise.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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
 * Represents a magic link token used for passwordless authentication.
 * Tokens are single-use and have an expiration time.
 */
@Entity
@Table(name = "magic_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MagicLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false)
    @NotBlank(message = "Email is required")
    private String email;
    
    // SHA-256 hash of the token (see TokenHasher) — the raw token is never persisted.
    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private MagicLinkPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
    
    @Column(name = "used", nullable = false)
    private boolean used;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Constructs a new MagicLink entity.
     *
     * @param email The email address requesting the magic link
     * @param token The SHA-256 hash of the generated secure token (see TokenHasher)
     * @param purpose The action this token authorizes
     * @param expiresAt The expiration timestamp for the token
     */
    public MagicLink(String email, String token, MagicLinkPurpose purpose, OffsetDateTime expiresAt) {
        this.email = email;
        this.token = token;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
        this.used = false;
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

    /**
     * Marks the magic link as having been used, preventing further reuse.
     */
    public void markAsUsed() {
        this.used = true;
    }

    /**
     * Resets the magic link for local development seeding.
     * @param newExpiry The new expiration time
     */
    public void resetForDev(OffsetDateTime newExpiry) {
        this.used = false;
        this.expiresAt = newExpiry;
    }
}
