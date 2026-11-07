package com.pawtrack.identity.service;

import java.util.UUID;

import com.pawtrack.identity.entity.Household;
import com.pawtrack.identity.entity.HouseholdRole;
import com.pawtrack.identity.entity.User;
import com.pawtrack.identity.exception.ForbiddenException;
import com.pawtrack.identity.messaging.EventPublisher;
import com.pawtrack.identity.messaging.IdentityEvent.HouseholdMemberAdded;
import com.pawtrack.identity.messaging.IdentityEvent.UserUpdated;
import com.pawtrack.identity.repository.HouseholdRepository;
import com.pawtrack.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the local user and their default household "My pets" from the JWT claims.
 * <p>
 * Runs in its own transaction so that a parallel first request for the same user (the UI calls several
 * endpoints at once after login) only loses this insert, not its whole request: the caller catches the
 * duplicate-key failure and reads the row the other request committed.
 */
@Component
@RequiredArgsConstructor
class UserProvisioner {

    private static final int MAX_DISPLAY_NAME_LENGTH = 100;

    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;
    private final EventPublisher events;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void provision(UUID id, Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (!StringUtils.hasText(email)) {
            throw new ForbiddenException("The access token has no email claim");
        }
        // Flush now so a concurrent insert fails here, before anything else is written.
        User user = userRepository.saveAndFlush(new User(id, email, displayName(jwt, email)));

        Household household = householdRepository.save(new Household(Household.DEFAULT_NAME, user));

        events.publish(UserUpdated.of(user));
        events.publish(new HouseholdMemberAdded(household.getId(), user.getId(), HouseholdRole.OWNER));
    }

    private static String displayName(Jwt jwt, String email) {
        String name = jwt.getClaimAsString("name");
        if (!StringUtils.hasText(name)) {
            name = jwt.getClaimAsString("preferred_username");
        }
        if (!StringUtils.hasText(name)) {
            name = email;
        }
        name = name.strip();
        return name.length() > MAX_DISPLAY_NAME_LENGTH ? name.substring(0, MAX_DISPLAY_NAME_LENGTH) : name;
    }
}
