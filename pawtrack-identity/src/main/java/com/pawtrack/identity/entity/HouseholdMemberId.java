package com.pawtrack.identity.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class HouseholdMemberId implements Serializable {

    @Column(name = "household_id")
    private UUID householdId;

    @Column(name = "user_id")
    private UUID userId;
}
