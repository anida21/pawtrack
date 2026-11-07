package com.pawtrack.notification.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Local copy of an identity membership, kept from {@code household.member.added/removed}.
 * Used for access checks and to find email recipients.
 */
@Entity
@Table(name = "household_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HouseholdMember {

    @EmbeddedId
    private HouseholdMemberId id;

    /** OWNER | MEMBER | SITTER, as sent by identity. */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    public HouseholdMember(UUID householdId, UUID userId, String role, Instant joinedAt) {
        this.id = new HouseholdMemberId(householdId, userId);
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public void changeRole(String role) {
        this.role = role;
    }

    public boolean isOwner() {
        return "OWNER".equals(role);
    }
}
