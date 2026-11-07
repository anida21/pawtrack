package com.pawtrack.identity.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import com.pawtrack.identity.config.PawtrackProperties;
import com.pawtrack.identity.dto.HouseholdResponse;
import com.pawtrack.identity.dto.InvitationRequest;
import com.pawtrack.identity.dto.InvitationResponse;
import com.pawtrack.identity.entity.Household;
import com.pawtrack.identity.entity.Invitation;
import com.pawtrack.identity.entity.User;
import com.pawtrack.identity.exception.ForbiddenException;
import com.pawtrack.identity.exception.GoneException;
import com.pawtrack.identity.exception.NotFoundException;
import com.pawtrack.identity.mapper.HouseholdMapper;
import com.pawtrack.identity.mapper.InvitationMapper;
import com.pawtrack.identity.messaging.EventPublisher;
import com.pawtrack.identity.messaging.IdentityEvent.HouseholdInvitationCreated;
import com.pawtrack.identity.messaging.IdentityEvent.HouseholdMemberAdded;
import com.pawtrack.identity.repository.InvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvitationService {

    /** 32 random bytes → 43 URL-safe characters (256 bits of entropy). */
    private static final int CODE_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserService userService;
    private final HouseholdService householdService;
    private final InvitationRepository invitationRepository;
    private final InvitationMapper invitationMapper;
    private final HouseholdMapper householdMapper;
    private final EventPublisher events;
    private final PawtrackProperties properties;

    @Transactional
    public InvitationResponse create(Jwt jwt, UUID householdId, InvitationRequest request) {
        User inviter = userService.currentUser(jwt);
        Household household = householdService.loadAsOwner(householdId, inviter.getId());

        Instant expiresAt = Instant.now().plus(Duration.ofDays(properties.invitations().validityDays()));
        Invitation invitation = invitationRepository.save(new Invitation(
            household, newCode(), normalize(request.email()), request.role().toHouseholdRole(),
            inviter.getId(), expiresAt));

        events.publish(new HouseholdInvitationCreated(household.getId(), household.getName(),
            invitation.getEmail(), invitation.getCode(), inviter.getId(), invitation.getExpiresAt()));
        return invitationMapper.toResponse(invitation);
    }

    /**
     * Adds the current user to the invited household. The code is single-use and only valid for the
     * email it was sent to. Accepting while already a member just consumes the code.
     */
    @Transactional
    public HouseholdResponse accept(Jwt jwt, String code) {
        User user = userService.currentUser(jwt);
        Invitation invitation = invitationRepository.findByCode(code)
            .orElseThrow(() -> new NotFoundException("Invitation not found"));

        Instant now = Instant.now();
        if (invitation.isAccepted()) {
            throw new GoneException("This invitation has already been used");
        }
        if (invitation.isExpired(now)) {
            throw new GoneException("This invitation expired at " + invitation.getExpiresAt());
        }
        if (!invitation.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new ForbiddenException("This invitation was sent to a different email address");
        }

        Household household = invitation.getHousehold();
        if (household.findMember(user.getId()).isEmpty()) {
            household.addMember(user, invitation.getRole());
            events.publish(new HouseholdMemberAdded(household.getId(), user.getId(), invitation.getRole()));
        }
        invitation.markAccepted(user.getId(), now);
        return householdMapper.toResponse(household);
    }

    private static String newCode() {
        byte[] bytes = new byte[CODE_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
