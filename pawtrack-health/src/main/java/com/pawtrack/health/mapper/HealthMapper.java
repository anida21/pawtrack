package com.pawtrack.health.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.pawtrack.health.dto.MedicationRequest;
import com.pawtrack.health.dto.MedicationResponse;
import com.pawtrack.health.dto.VaccinationRequest;
import com.pawtrack.health.dto.VaccinationResponse;
import com.pawtrack.health.dto.VetVisitRequest;
import com.pawtrack.health.dto.VetVisitResponse;
import com.pawtrack.health.dto.WeightRequest;
import com.pawtrack.health.dto.WeightResponse;
import com.pawtrack.health.entity.Medication;
import com.pawtrack.health.entity.Vaccination;
import com.pawtrack.health.entity.VetVisit;
import com.pawtrack.health.entity.Weight;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

/** Entity ↔ DTO. The {@code apply} methods are full replaces of the editable fields (PUT semantics). */
@Mapper
public interface HealthMapper {

    String DEFAULT_CURRENCY = "BAM";

    VaccinationResponse toResponse(Vaccination vaccination);

    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void apply(VaccinationRequest request, @MappingTarget Vaccination vaccination);

    VetVisitResponse toResponse(VetVisit visit);

    @Mapping(target = "cost", source = "cost", qualifiedByName = "money")
    @Mapping(target = "currency", source = "currency", defaultValue = DEFAULT_CURRENCY)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void apply(VetVisitRequest request, @MappingTarget VetVisit visit);

    @Mapping(target = "active", expression = "java(medication.isActiveOn(today))")
    MedicationResponse toResponse(Medication medication, @Context LocalDate today);

    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void apply(MedicationRequest request, @MappingTarget Medication medication);

    WeightResponse toResponse(Weight weight);

    @Mapping(target = "weightKg", source = "weightKg", qualifiedByName = "kilograms")
    @Mapping(target = "deletedAt", ignore = true)
    void apply(WeightRequest request, @MappingTarget Weight weight);

    /** Costs are stored with 2 decimals (health.yaml). */
    @Named("money")
    default BigDecimal money(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** Weights are stored with 3 decimals (health.yaml). */
    @Named("kilograms")
    default BigDecimal kilograms(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    default Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
