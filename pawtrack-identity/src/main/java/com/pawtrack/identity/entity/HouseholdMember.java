package com.pawtrack.identity.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Membership of a user in a household. Owned by the {@link Household} aggregate.
 */
@Entity
@Table(name = "household_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HouseholdMember {

    @EmbeddedId
    private HouseholdMemberId id = new HouseholdMemberId();

    @MapsId("householdId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id")
    private Household household;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HouseholdRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    HouseholdMember(Household household, User user, HouseholdRole role) {
        this.household = household;
        this.user = user;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public boolean isOwner() {
        return role == HouseholdRole.OWNER;
    }
}
