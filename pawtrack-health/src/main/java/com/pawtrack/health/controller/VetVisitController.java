package com.pawtrack.health.controller;

import java.util.List;
import java.util.UUID;

import com.pawtrack.health.dto.VetVisitRequest;
import com.pawtrack.health.dto.VetVisitResponse;
import com.pawtrack.health.service.VetVisitService;
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

/** health.yaml: /api/v1/pets/{petId}/vet-visits[/{id}]. */
@RestController
@RequestMapping("/api/v1/pets/{petId}/vet-visits")
@RequiredArgsConstructor
public class VetVisitController {

    private final VetVisitService service;

    @GetMapping
    public List<VetVisitResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId) {
        return service.list(jwt, petId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VetVisitResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId,
                                   @Valid @RequestBody VetVisitRequest request) {
        return service.create(jwt, petId, request);
    }

    @PutMapping("/{id}")
    public VetVisitResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId,
                                   @PathVariable UUID id, @Valid @RequestBody VetVisitRequest request) {
        return service.update(jwt, petId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId, @PathVariable UUID id) {
        service.delete(jwt, petId, id);
    }
}
