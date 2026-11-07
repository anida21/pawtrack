package com.pawtrack.notification.repository;

import java.util.List;
import java.util.UUID;

import com.pawtrack.notification.entity.Pet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetRepository extends JpaRepository<Pet, UUID> {

    List<Pet> findByHouseholdIdAndDeletedFalse(UUID householdId);
}
