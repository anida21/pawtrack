package com.pawtrack.health.support;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.pawtrack.health.config.PetClient;
import com.pawtrack.health.dto.PetRef;
import com.pawtrack.health.exception.DependencyUnavailableException;
import com.pawtrack.health.exception.ForbiddenException;
import com.pawtrack.health.exception.NotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Stands in for pet in API tests: pets are created per test, access is granted per user, and it answers like
 * pet's {@code GET /api/v1/pets/{petId}} would (404 unknown, 403 no access). The real HTTP client
 * ({@code RestPetClient}) is covered by its own test.
 */
public class FakePets implements PetClient {

    private final Map<UUID, FakePet> pets = new ConcurrentHashMap<>();
    private volatile boolean down;

    /** A new pet in a new household that {@code owner} may access. */
    public UUID create(TestUser owner, String name) {
        UUID petId = UUID.randomUUID();
        FakePet pet = new FakePet(UUID.randomUUID(), name, ConcurrentHashMap.newKeySet());
        pet.users().add(owner.id());
        pets.put(petId, pet);
        return petId;
    }

    /** Lets {@code user} access the pet (any household role pet answers 200 for, SITTER included). */
    public void grant(TestUser user, UUID petId) {
        pets.get(petId).users().add(user.id());
    }

    public UUID householdOf(UUID petId) {
        return pets.get(petId).householdId();
    }

    /** Pet moved to another household / renamed in pet (no event; health learns it on the next write). */
    public void move(UUID petId, UUID householdId, String name) {
        FakePet old = pets.get(petId);
        pets.put(petId, new FakePet(householdId, name, old.users()));
    }

    /** Simulates pet being unreachable. */
    public void setDown(boolean down) {
        this.down = down;
    }

    @Override
    public PetRef getPet(Jwt jwt, UUID petId) {
        if (down) {
            throw new DependencyUnavailableException("pet is down (test)", null);
        }
        FakePet pet = pets.get(petId);
        if (pet == null) {
            throw new NotFoundException("Pet not found");
        }
        if (!pet.users().contains(UUID.fromString(jwt.getSubject()))) {
            throw new ForbiddenException("You may not access this pet");
        }
        return new PetRef(petId, pet.householdId(), pet.name());
    }

    private record FakePet(UUID householdId, String name, Set<UUID> users) {
    }
}
