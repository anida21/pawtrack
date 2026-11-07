package com.pawtrack.health.config;

import java.util.UUID;

import com.pawtrack.health.dto.PetRef;
import com.pawtrack.health.exception.DependencyUnavailableException;
import com.pawtrack.health.exception.ForbiddenException;
import com.pawtrack.health.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls pet {@code GET /api/v1/pets/{petId}} with the caller's own token on every request (no cache, so a
 * membership change or a deleted pet takes effect immediately). Pet's 404 and 403 are passed on;
 * anything else means the access cannot be checked (503).
 */
@Slf4j
@Component
public class RestPetClient implements PetClient {

    static final String PET = "/api/v1/pets/{petId}";

    private final RestClient restClient;

    public RestPetClient(@Qualifier("petRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PetRef getPet(Jwt jwt, UUID petId) {
        PetRef pet;
        try {
            pet = restClient.get()
                .uri(PET, petId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue())
                .retrieve()
                .body(PetRef.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new NotFoundException("Pet not found");
            }
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
                throw new ForbiddenException("You may not access this pet");
            }
            throw unavailable(jwt, petId, ex);
        } catch (RestClientException ex) {
            throw unavailable(jwt, petId, ex);
        }
        if (pet == null || pet.id() == null || pet.householdId() == null) {
            throw unavailable(jwt, petId, new IllegalStateException("Incomplete pet response"));
        }
        return pet;
    }

    private static DependencyUnavailableException unavailable(Jwt jwt, UUID petId, Exception ex) {
        log.warn("Pet access check failed for pet {} and user {}", petId, jwt.getSubject(), ex);
        return new DependencyUnavailableException("Access to the pet could not be checked, try again later", ex);
    }
}
