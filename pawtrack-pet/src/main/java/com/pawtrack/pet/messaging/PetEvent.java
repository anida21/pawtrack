package com.pawtrack.pet.messaging;

import java.util.UUID;

import com.pawtrack.pet.entity.Pet;
import com.pawtrack.pet.entity.Species;

/**
 * Events published by this service. Each record is the {@code data} part of the envelope
 * and must match {@code pawtrack-development/contracts/events.md}.
 */
public sealed interface PetEvent {

    /** Event type, also used as the routing key. */
    String type();

    record PetCreated(UUID petId, UUID householdId, String name, Species species) implements PetEvent {

        public static PetCreated of(Pet pet) {
            return new PetCreated(pet.getId(), pet.getHouseholdId(), pet.getName(), pet.getSpecies());
        }

        @Override
        public String type() {
            return "pet.created";
        }
    }

    record PetUpdated(UUID petId, UUID householdId, String name, Species species) implements PetEvent {

        public static PetUpdated of(Pet pet) {
            return new PetUpdated(pet.getId(), pet.getHouseholdId(), pet.getName(), pet.getSpecies());
        }

        @Override
        public String type() {
            return "pet.updated";
        }
    }

    record PetDeleted(UUID petId, UUID householdId) implements PetEvent {

        public static PetDeleted of(Pet pet) {
            return new PetDeleted(pet.getId(), pet.getHouseholdId());
        }

        @Override
        public String type() {
            return "pet.deleted";
        }
    }
}
