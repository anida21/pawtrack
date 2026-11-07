package com.pawtrack.identity.service;

import java.util.UUID;

import com.pawtrack.identity.dto.UpdateUserRequest;
import com.pawtrack.identity.dto.UserResponse;
import com.pawtrack.identity.entity.Household;
import com.pawtrack.identity.entity.HouseholdRole;
import com.pawtrack.identity.entity.User;
import com.pawtrack.identity.exception.ForbiddenException;
import com.pawtrack.identity.mapper.UserMapper;
import com.pawtrack.identity.messaging.EventPublisher;
import com.pawtrack.identity.messaging.IdentityEvent.HouseholdMemberAdded;
import com.pawtrack.identity.messaging.IdentityEvent.UserUpdated;
import com.pawtrack.identity.repository.HouseholdRepository;
import com.pawtrack.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_DISPLAY_NAME_LENGTH = 100;

    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;
    private final UserMapper userMapper;
    private final EventPublisher events;

    @Transactional
    public UserResponse getMe(Jwt jwt) {
        return userMapper.toResponse(currentUser(jwt));
    }

    @Transactional
    public UserResponse updateMe(Jwt jwt, UpdateUserRequest request) {
        User user = currentUser(jwt);
        userMapper.update(request, user);
        events.publish(UserUpdated.of(user));
        return userMapper.toResponse(user);
    }

    /**
     * Returns the user behind the token. On the very first call the user is created from the JWT claims,
     * together with a default household "My pets" in which they are OWNER.
     */
    @Transactional
    public User currentUser(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        return userRepository.findById(id).orElseGet(() -> provision(id, jwt));
    }

    private User provision(UUID id, Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (!StringUtils.hasText(email)) {
            throw new ForbiddenException("The access token has no email claim");
        }
        User user = userRepository.save(new User(id, email, displayName(jwt, email)));

        Household household = householdRepository.save(new Household(Household.DEFAULT_NAME, user));

        events.publish(UserUpdated.of(user));
        events.publish(new HouseholdMemberAdded(household.getId(), user.getId(), HouseholdRole.OWNER));
        return user;
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
