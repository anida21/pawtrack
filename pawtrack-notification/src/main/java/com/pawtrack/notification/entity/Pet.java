package com.pawtrack.notification.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Local copy of a pet ({@code petId → householdId, name}), kept from {@code pet.created / pet.deleted}.
 */
@Entity
@Table(name = "pets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pet {

    @Id
    @Column(name = "pet_id")
    private UUID petId;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean deleted;

    public Pet(UUID petId, UUID householdId, String name) {
        this.petId = petId;
        this.householdId = householdId;
        this.name = name;
    }

    public void update(UUID householdId, String name) {
        this.householdId = householdId;
        this.name = name;
    }

    public void markDeleted() {
        this.deleted = true;
    }
}
