package com.pawtrack.pet.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.pawtrack.pet.config.IdentityClient;
import com.pawtrack.pet.dto.HouseholdMembership;
import com.pawtrack.pet.dto.HouseholdRole;
import com.pawtrack.pet.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Access rule of pet.yaml: the caller must be a member of the pet's household; SITTER may only read.
 */
@Component
@RequiredArgsConstructor
public class HouseholdAccess {

    private final IdentityClient identityClient;

    /** householdId → role of the caller. */
    public Map<UUID, HouseholdRole> roles(Jwt jwt) {
        Map<UUID, HouseholdRole> roles = new LinkedHashMap<>();
        for (HouseholdMembership membership : identityClient.myHouseholds(jwt)) {
            roles.put(membership.householdId(), membership.role());
        }
        return roles;
    }

    public void requireRead(Jwt jwt, UUID householdId) {
        if (role(jwt, householdId) == null) {
            throw new ForbiddenException("You are not a member of this household");
        }
    }

    public void requireWrite(Jwt jwt, UUID householdId) {
        HouseholdRole role = role(jwt, householdId);
        if (role == null) {
            throw new ForbiddenException("You are not a member of this household");
        }
        if (!role.canWrite()) {
            throw new ForbiddenException("Sitters can view pets but not change them");
        }
    }

    /**
     * Role of the caller in {@code householdId}, or {@code null}. A household missing from the cached memberships
     * may simply be newer than the cache (just created or joined), so identity is asked once more before denying.
     * Removals need no such check: household.member.removed evicts the cache.
     */
    public HouseholdRole role(Jwt jwt, UUID householdId) {
        HouseholdRole role = roles(jwt).get(householdId);
        if (role == null) {
            identityClient.evict(jwt.getSubject());
            role = roles(jwt).get(householdId);
        }
        return role;
    }
}
