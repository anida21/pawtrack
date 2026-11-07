package com.pawtrack.notification.support;

import java.util.UUID;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * A Keycloak user as seen through its JWT. Every test creates fresh users, so tests never share data.
 * {@code email} or {@code name} may be {@code null} to leave that claim out of the token.
 */
public record TestUser(UUID id, String email, String name, String username) {

    public static TestUser random(String name) {
        UUID id = UUID.randomUUID();
        String username = "user-" + id.toString().substring(0, 8);
        return new TestUser(id, username + "@pawtrack.test", name, username);
    }

    public TestUser withName(String newName) {
        return new TestUser(id, email, newName, username);
    }

    public TestUser withEmail(String newEmail) {
        return new TestUser(id, newEmail, name, username);
    }

    /** Authenticates a MockMvc request as this user. */
    public RequestPostProcessor jwt() {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> {
            token.subject(id.toString()).claim("preferred_username", username);
            if (email != null) {
                token.claim("email", email);
            }
            if (name != null) {
                token.claim("name", name);
            }
        });
    }
}
