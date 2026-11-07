package com.pawtrack.notification.service;

import java.util.UUID;

import com.pawtrack.notification.entity.Pet;
import com.pawtrack.notification.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads the local pet copy; events may arrive before {@code pet.created}, so unknown pets are tolerated. */
@Component
@RequiredArgsConstructor
public class PetNames {

    static final String UNKNOWN_PET = "Your pet";

    private final PetRepository petRepository;

    public String nameOf(UUID petId) {
        return petRepository.findById(petId).map(Pet::getName).orElse(UNKNOWN_PET);
    }

    public boolean isDeleted(UUID petId) {
        return petRepository.findById(petId).map(Pet::isDeleted).orElse(false);
    }
}
