package com.pawtrack.health.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.pawtrack.health.dto.WeightRequest;
import com.pawtrack.health.dto.WeightResponse;
import com.pawtrack.health.entity.Weight;
import com.pawtrack.health.exception.BadRequestException;
import com.pawtrack.health.mapper.HealthMapper;
import com.pawtrack.health.repository.WeightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Weight history of a pet, oldest first (chart order). */
@Service
@RequiredArgsConstructor
public class WeightService {

    /** Open ends of the date filter; within Postgres' date range. */
    private static final LocalDate EARLIEST = LocalDate.of(1, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

    private final WeightRepository repository;
    private final HealthMapper mapper;
    private final PetAccess access;

    /** {@code from} and {@code to} are optional and inclusive. */
    @Transactional(readOnly = true)
    public List<WeightResponse> list(Jwt jwt, UUID petId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("from must not be after to");
        }
        access.check(jwt, petId);
        return repository.findByPetIdAndDeletedAtIsNullAndMeasuredOnBetweenOrderByMeasuredOnAscCreatedAtAsc(
                petId, from != null ? from : EARLIEST, to != null ? to : LATEST).stream()
            .map(mapper::toResponse)
            .toList();
    }

    @Transactional
    public WeightResponse create(Jwt jwt, UUID petId, WeightRequest request) {
        access.checkForWrite(jwt, petId);
        Weight weight = new Weight(petId, UUID.fromString(jwt.getSubject()));
        mapper.apply(request, weight);
        repository.save(weight);
        return mapper.toResponse(weight);
    }
}
