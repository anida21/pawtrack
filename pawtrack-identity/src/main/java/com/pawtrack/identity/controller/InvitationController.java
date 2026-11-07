package com.pawtrack.identity.controller;

import java.util.UUID;

import com.pawtrack.identity.dto.HouseholdResponse;
import com.pawtrack.identity.dto.InvitationRequest;
import com.pawtrack.identity.dto.InvitationResponse;
import com.pawtrack.identity.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Invitations")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @Operation(summary = "OWNER invites by email. Returns a one-time code valid 7 days. "
        + "Publishes household.invitation.created.")
    @PostMapping("/households/{householdId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse createInvitation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId,
                                               @Valid @RequestBody InvitationRequest request) {
        return invitationService.create(jwt, householdId, request);
    }

    @Operation(summary = "Current user joins the household. Publishes household.member.added.")
    @PostMapping("/invitations/{code}/accept")
    public HouseholdResponse acceptInvitation(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        return invitationService.accept(jwt, code);
    }
}
