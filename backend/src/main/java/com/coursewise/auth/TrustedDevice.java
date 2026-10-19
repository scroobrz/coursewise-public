package com.coursewise.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a trusted device for a user, allowing them to bypass the
 * magic-link 2FA step on subsequent logins from the same browser.
 * <p>
 * When a user checks "Trust this device" during 2FA verification,
 * a secure token is generated and stored both in this table and as
 * a long-lived cookie on the client. On future logins from that
 * device, the password check alone is sufficient.
 */
@Entity
@Table(name = "trusted_devices", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "device_token"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrustedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_token", nullable = false)
    private String deviceToken;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Constructs a new TrustedDevice entry.
     *
     * @param user        The user who owns this trusted device
     * @param deviceToken The secure random token stored in the client cookie
     * @param deviceName  A human-readable description (e.g. "Chrome on macOS")
     * @param expiresAt   When this trust expires (see {@code coursewise.config.trusted-device-days})
     */
    public TrustedDevice(User user, String deviceToken, String deviceName, OffsetDateTime expiresAt) {
        this.user = user;
        this.deviceToken = deviceToken;
        this.deviceName = deviceName;
        this.expiresAt = expiresAt;
    }

    /**
     * Checks whether this trusted device entry has expired.
     *
     * @return true if the current time is after the expiry time
     */
    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(this.expiresAt);
    }

    public void renew(OffsetDateTime newExpiresAt) {
        this.expiresAt = newExpiresAt;
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
