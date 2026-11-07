package com.pawtrack.pet.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.pawtrack.pet.config.IdentityClient;
import com.pawtrack.pet.dto.HouseholdMembership;
import com.pawtrack.pet.dto.HouseholdRole;
import com.pawtrack.pet.exception.DependencyUnavailableException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Stands in for identity in API tests: memberships are granted per test user, and it answers
 * without a cache, so a change is visible on the next request. The real HTTP client
 * ({@code RestIdentityClient}) is covered by its own test.
 */
public class FakeIdentity implements IdentityClient {

    private final Map<UUID, Map<UUID, HouseholdRole>> memberships = new ConcurrentHashMap<>();
    private volatile boolean down;

    public void grant(TestUser user, UUID householdId, HouseholdRole role) {
        memberships.computeIfAbsent(user.id(), id -> new ConcurrentHashMap<>()).put(householdId, role);
    }

    public void revoke(TestUser user, UUID householdId) {
        memberships.getOrDefault(user.id(), Map.of()).remove(householdId);
    }

    /** Simulates identity being unreachable. */
    public void setDown(boolean down) {
        this.down = down;
    }

    /** Nothing cached here. */
    @Override
    public void evict(String userId) {
    }

    @Override
    public List<HouseholdMembership> myHouseholds(Jwt jwt) {
        if (down) {
            throw new DependencyUnavailableException("identity is down (test)", null);
        }
        return memberships.getOrDefault(UUID.fromString(jwt.getSubject()), Map.of()).entrySet().stream()
            .map(entry -> new HouseholdMembership(entry.getKey(), "Household", entry.getValue()))
            .toList();
    }
}
