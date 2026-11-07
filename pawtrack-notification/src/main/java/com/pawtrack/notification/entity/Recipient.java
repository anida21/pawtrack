package com.pawtrack.notification.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Local copy of an identity user, kept from {@code user.updated} (an upsert).
 */
@Entity
@Table(name = "recipients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipient {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Recipient(UUID userId) {
        this.userId = userId;
    }

    public void update(String email, String displayName, String timezone, boolean emailNotifications, Instant now) {
        this.email = email;
        this.displayName = displayName;
        this.timezone = timezone;
        this.emailNotifications = emailNotifications;
        this.updatedAt = now;
    }
}
