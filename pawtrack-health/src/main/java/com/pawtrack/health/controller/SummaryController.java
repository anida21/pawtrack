package com.pawtrack.health.controller;

import java.util.UUID;

import com.pawtrack.health.dto.HealthSummary;
import com.pawtrack.health.service.SummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** health.yaml: /api/v1/pets/{petId}/summary. */
@RestController
@RequiredArgsConstructor
public class SummaryController {

    private final SummaryService service;

    @GetMapping("/api/v1/pets/{petId}/summary")
    public HealthSummary summary(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId) {
        return service.summary(jwt, petId);
    }
}
