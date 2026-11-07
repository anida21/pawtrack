package com.pawtrack.health.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.pawtrack.health.dto.WeightRequest;
import com.pawtrack.health.dto.WeightResponse;
import com.pawtrack.health.service.WeightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** health.yaml: /api/v1/pets/{petId}/weights. */
@RestController
@RequestMapping("/api/v1/pets/{petId}/weights")
@RequiredArgsConstructor
public class WeightController {

    private final WeightService service;

    @GetMapping
    public List<WeightResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(jwt, petId, from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WeightResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID petId,
                                 @Valid @RequestBody WeightRequest request) {
        return service.create(jwt, petId, request);
    }
}
