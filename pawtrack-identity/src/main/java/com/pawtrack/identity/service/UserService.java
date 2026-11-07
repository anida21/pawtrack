package com.pawtrack.identity.service;

import java.util.UUID;

import com.pawtrack.identity.dto.UpdateUserRequest;
import com.pawtrack.identity.dto.UserResponse;
import com.pawtrack.identity.entity.User;
import com.pawtrack.identity.exception.ConflictException;
import com.pawtrack.identity.mapper.UserMapper;
import com.pawtrack.identity.messaging.EventPublisher;
import com.pawtrack.identity.messaging.IdentityEvent.UserUpdated;
import com.pawtrack.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProvisioner provisioner;
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
     * together with a default household "My pets" in which they are OWNER (see {@link UserProvisioner}).
     */
    @Transactional
    public User currentUser(Jwt jwt) {
        UUID id = UUID.fromString(jwt.getSubject());
        return userRepository.findById(id).orElseGet(() -> provisionOrReuse(id, jwt));
    }

    private User provisionOrReuse(UUID id, Jwt jwt) {
        try {
            provisioner.provision(id, jwt);
        } catch (DataIntegrityViolationException ex) {
            // Either a parallel first request created this user (read it below), or the email belongs
            // to a different account.
        }
        return userRepository.findById(id)
            .orElseThrow(() -> new ConflictException("Another account already uses the email in this token"));
    }
}
