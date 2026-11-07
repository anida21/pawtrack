package com.pawtrack.notification.service;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import com.pawtrack.notification.config.PawtrackProperties;
import com.pawtrack.notification.entity.HouseholdMember;
import com.pawtrack.notification.repository.HouseholdMemberRepository;
import com.pawtrack.notification.repository.RecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A reminder has one dueAt for the whole household, so "09:00 user tz" needs one zone per household:
 * the OWNER's timezone, else the longest-standing member's, else {@code pawtrack.reminders.default-timezone}.
 */
@Component
@RequiredArgsConstructor
public class HouseholdTimeZones {

    private final HouseholdMemberRepository memberRepository;
    private final RecipientRepository recipientRepository;
    private final PawtrackProperties properties;

    public ZoneId zoneOf(UUID householdId) {
        return memberRepository.findByHouseholdId(householdId).stream()
            .sorted(Comparator.comparing((HouseholdMember m) -> !m.isOwner()))   // owners first, then by joinedAt
            .map(m -> recipientRepository.findById(m.getId().getUserId()))
            .flatMap(Optional::stream)
            .map(r -> parse(r.getTimezone()))
            .flatMap(Optional::stream)
            .findFirst()
            .orElse(properties.reminders().defaultTimezone());
    }

    static Optional<ZoneId> parse(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ZoneId.of(timezone));
        } catch (DateTimeException ex) {
            return Optional.empty();
        }
    }
}
