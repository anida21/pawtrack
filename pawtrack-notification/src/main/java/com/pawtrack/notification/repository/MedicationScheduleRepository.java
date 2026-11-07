package com.pawtrack.notification.repository;

import java.util.List;
import java.util.UUID;

import com.pawtrack.notification.entity.MedicationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationScheduleRepository extends JpaRepository<MedicationSchedule, UUID> {

    List<MedicationSchedule> findByActiveTrue();

    List<MedicationSchedule> findByPetIdAndActiveTrue(UUID petId);

    List<MedicationSchedule> findByHouseholdIdAndActiveTrue(UUID householdId);
}
