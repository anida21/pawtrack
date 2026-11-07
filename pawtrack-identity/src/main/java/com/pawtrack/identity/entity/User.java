package com.pawtrack.identity.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Local mirror of a Keycloak user. The id is the JWT {@code sub} claim, so it is assigned, never generated.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    public static final String DEFAULT_TIMEZONE = "Europe/Sarajevo";

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Setter
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Setter
    @Column(nullable = false, length = 64)
    private String timezone = DEFAULT_TIMEZONE;

    @Setter
    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public User(UUID id, String email, String displayName) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.createdAt = Instant.now();
    }
}
