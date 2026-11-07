package com.pawtrack.identity.controller;

import java.util.UUID;

import com.pawtrack.identity.dto.HouseholdRequest;
import com.pawtrack.identity.dto.HouseholdResponse;
import com.pawtrack.identity.service.HouseholdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Households")
@RestController
@RequestMapping("/api/v1/households")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService householdService;

    @PostMapping
    public ResponseEntity<HouseholdResponse> createHousehold(@AuthenticationPrincipal Jwt jwt,
                                                             @Valid @RequestBody HouseholdRequest request) {
        HouseholdResponse household = householdService.create(jwt, request);
        return ResponseEntity
            .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(household.id()).toUri())
            .body(household);
    }

    @GetMapping("/{householdId}")
    public HouseholdResponse getHousehold(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId) {
        return householdService.get(jwt, householdId);
    }

    @Operation(summary = "Rename. OWNER only.")
    @PutMapping("/{householdId}")
    public HouseholdResponse updateHousehold(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId,
                                             @Valid @RequestBody HouseholdRequest request) {
        return householdService.rename(jwt, householdId, request);
    }

    @Operation(summary = "OWNER only. Publishes household.deleted.")
    @DeleteMapping("/{householdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHousehold(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId) {
        householdService.delete(jwt, householdId);
    }

    @Operation(summary = "OWNER removes a member, or a member removes themself. The last OWNER cannot leave.")
    @DeleteMapping("/{householdId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId,
                             @PathVariable UUID userId) {
        householdService.removeMember(jwt, householdId, userId);
    }
}
