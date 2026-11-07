package com.pawtrack.pet.config;

import java.util.List;

import com.pawtrack.pet.dto.HouseholdMembership;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Asks identity, the authority for memberships, which households the caller belongs to.
 * Implementations throw {@link com.pawtrack.pet.exception.DependencyUnavailableException} when identity cannot answer.
 */
public interface IdentityClient {

    List<HouseholdMembership> myHouseholds(Jwt jwt);

    /** Forgets anything cached for {@code userId} (JWT subject), so the next check asks identity again. */
    void evict(String userId);
}
