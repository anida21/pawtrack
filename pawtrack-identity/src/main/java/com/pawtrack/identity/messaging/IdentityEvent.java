package com.pawtrack.identity.messaging;

import java.time.Instant;
import java.util.UUID;

import com.pawtrack.identity.entity.HouseholdRole;
import com.pawtrack.identity.entity.User;

/**
 * Events published by this service. Each record is the {@code data} part of the envelope
 * and must match {@code pawtrack-development/contracts/events.md}.
 */
public sealed interface IdentityEvent {

    /** Event type, also used as the routing key. */
    String type();

    record HouseholdMemberAdded(UUID householdId, UUID userId, HouseholdRole role) implements IdentityEvent {
        @Override
        public String type() {
            return "household.member.added";
        }
    }

    record HouseholdMemberRemoved(UUID householdId, UUID userId) implements IdentityEvent {
        @Override
        public String type() {
            return "household.member.removed";
        }
    }

    record HouseholdDeleted(UUID householdId) implements IdentityEvent {
        @Override
        public String type() {
            return "household.deleted";
        }
    }

    record HouseholdInvitationCreated(UUID householdId, String householdName, String email, String code,
                                      UUID invitedBy, Instant expiresAt) implements IdentityEvent {
        @Override
        public String type() {
            return "household.invitation.created";
        }
    }

    record UserUpdated(UUID userId, String email, String displayName, String timezone,
                       boolean emailNotifications) implements IdentityEvent {

        public static UserUpdated of(User user) {
            return new UserUpdated(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getTimezone(), user.isEmailNotifications());
        }

        @Override
        public String type() {
            return "user.updated";
        }
    }
}
