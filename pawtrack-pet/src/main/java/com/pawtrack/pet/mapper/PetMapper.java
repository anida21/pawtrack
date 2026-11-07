package com.pawtrack.pet.mapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import com.pawtrack.pet.dto.PetRequest;
import com.pawtrack.pet.dto.PetResponse;
import com.pawtrack.pet.entity.Pet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface PetMapper {

    @Mapping(target = "ageInMonths", expression = "java(ageInMonths(pet.getBirthDate()))")
    @Mapping(target = "hasPhoto", expression = "java(pet.hasPhoto())")
    PetResponse toResponse(Pet pet);

    /** Full replace of the editable fields (PUT semantics). */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "photoKey", ignore = true)
    @Mapping(target = "photoContentType", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void apply(PetRequest request, @MappingTarget Pet pet);

    /** Whole months since birth (UTC calendar), {@code null} without a birth date. */
    default Integer ageInMonths(LocalDate birthDate) {
        if (birthDate == null) {
            return null;
        }
        long months = ChronoUnit.MONTHS.between(birthDate, LocalDate.now(ZoneOffset.UTC));
        return (int) Math.max(0, months);
    }
}
