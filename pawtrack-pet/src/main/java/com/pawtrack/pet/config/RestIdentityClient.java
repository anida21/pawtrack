package com.pawtrack.pet.config;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.pawtrack.pet.dto.HouseholdMembership;
import com.pawtrack.pet.exception.DependencyUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls identity {@code GET /api/v1/me/households} with the caller's own token and keeps the answer
 * for {@code pawtrack.identity.membership-cache-ttl} (60 s, ARCHITECTURE.md), keyed by the token's subject.
 * A membership change in identity therefore reaches this service within one TTL.
 */
@Slf4j
@Component
public class RestIdentityClient implements IdentityClient {

    static final String MY_HOUSEHOLDS = "/api/v1/me/households";

    /** Above this many cached users, expired entries are swept on the next write. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final RestClient restClient;
    private final PawtrackProperties properties;
    private final Clock clock;
    private final Map<String, CachedMemberships> cache = new ConcurrentHashMap<>();

    @Autowired
    public RestIdentityClient(@Qualifier("identityRestClient") RestClient restClient, PawtrackProperties properties) {
        this(restClient, properties, Clock.systemUTC());
    }

    RestIdentityClient(RestClient restClient, PawtrackProperties properties, Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public List<HouseholdMembership> myHouseholds(Jwt jwt) {
        Instant now = clock.instant();
        CachedMemberships cached = cache.get(jwt.getSubject());
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.memberships();
        }
        List<HouseholdMembership> memberships = fetch(jwt);
        if (cache.size() >= SWEEP_THRESHOLD) {
            cache.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
        }
        cache.put(jwt.getSubject(),
            new CachedMemberships(memberships, now.plus(properties.identity().membershipCacheTtl())));
        return memberships;
    }

    private List<HouseholdMembership> fetch(Jwt jwt) {
        try {
            HouseholdMembership[] body = restClient.get()
                .uri(MY_HOUSEHOLDS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue())
                .retrieve()
                .body(HouseholdMembership[].class);
            return body == null ? List.of() : List.copyOf(Arrays.asList(body));
        } catch (RestClientException ex) {
            log.warn("Identity membership lookup failed for {}", jwt.getSubject(), ex);
            throw new DependencyUnavailableException("Household memberships could not be checked, try again later", ex);
        }
    }

    private record CachedMemberships(List<HouseholdMembership> memberships, Instant expiresAt) {
    }
}
