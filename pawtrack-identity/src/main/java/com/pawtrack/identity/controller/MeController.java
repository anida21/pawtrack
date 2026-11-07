package com.pawtrack.identity.controller;

import java.util.List;

import com.pawtrack.identity.dto.HouseholdMembershipResponse;
import com.pawtrack.identity.dto.UpdateUserRequest;
import com.pawtrack.identity.dto.UserResponse;
import com.pawtrack.identity.service.HouseholdService;
import com.pawtrack.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;
    private final HouseholdService householdService;

    @Operation(summary = "Current user. Creates the local user record from the JWT on first call, "
        + "together with a default household \"My pets\".")
    @GetMapping
    public UserResponse getMe(@AuthenticationPrincipal Jwt jwt) {
        return userService.getMe(jwt);
    }

    @PatchMapping
    public UserResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateMe(jwt, request);
    }

    @Operation(summary = "Households the current user belongs to (used by other services for access checks).")
    @GetMapping("/households")
    public List<HouseholdMembershipResponse> getMyHouseholds(@AuthenticationPrincipal Jwt jwt) {
        return householdService.getMyHouseholds(jwt);
    }
}
