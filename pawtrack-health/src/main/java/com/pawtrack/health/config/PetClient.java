package com.pawtrack.health.config;

import java.util.UUID;

import com.pawtrack.health.dto.PetRef;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Asks pet whether the caller may access a pet (health.yaml: {@code GET /api/v1/pets/{petId}} with the
 * forwarded token must answer 200). Implementations throw
 * {@link com.pawtrack.health.exception.NotFoundException} (pet answered 404),
 * {@link com.pawtrack.health.exception.ForbiddenException} (403) or
 * {@link com.pawtrack.health.exception.DependencyUnavailableException} (anything else).
 */
public interface PetClient {

    PetRef getPet(Jwt jwt, UUID petId);
}
