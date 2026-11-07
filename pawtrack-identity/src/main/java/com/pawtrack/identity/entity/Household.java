package com.pawtrack.identity.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Aggregate root for a household and its members.
 */
@Entity
@Table(name = "households")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Household {

    public static final String DEFAULT_NAME = "My pets";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Setter
    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "household", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("joinedAt ASC")
    private List<HouseholdMember> members = new ArrayList<>();

    /** Creates a household with {@code owner} as its first OWNER. */
    public Household(String name, User owner) {
        this.name = name;
        this.createdBy = owner.getId();
        this.createdAt = Instant.now();
        addMember(owner, HouseholdRole.OWNER);
    }

    public HouseholdMember addMember(User user, HouseholdRole role) {
        HouseholdMember member = new HouseholdMember(this, user, role);
        members.add(member);
        return member;
    }

    public void removeMember(HouseholdMember member) {
        members.remove(member);
    }

    public Optional<HouseholdMember> findMember(UUID userId) {
        return members.stream()
            .filter(member -> member.getUser().getId().equals(userId))
            .findFirst();
    }

    public long ownerCount() {
        return members.stream().filter(HouseholdMember::isOwner).count();
    }
}
