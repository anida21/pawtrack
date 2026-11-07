package com.pawtrack.health.controller;

import java.util.List;
import java.util.UUID;

import com.pawtrack.health.dto.VaccinationRequest;
import com.pawtrack.health.dto.VaccinationResponse;
import com.pawtrack.health.service.VaccinationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** health.yaml: /api/v1/pets/{petId}/vaccinations[/{id}]. */
@RestController
@RequestMapping("/api/v1/pets/{petId}/vaccinations")
@RequiredArgsConstructor
public class VaccinationController {

    private final VaccinationService service;

    @GetMapping
    public List<VaccinationResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId) {
        return service.list(jwt, petId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VaccinationResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId,
                                      @Valid @RequestBody VaccinationRequest request) {
        return service.create(jwt, petId, request);
    }

    @PutMapping("/{id}")
    public VaccinationResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId,
                                      @PathVariable UUID id, @Valid @RequestBody VaccinationRequest request) {
        return service.update(jwt, petId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId, @PathVariable UUID id) {
        service.delete(jwt, petId, id);
    }
}
