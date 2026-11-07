package com.pawtrack.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pawtrack.notification.dto.ReminderRequest;
import com.pawtrack.notification.dto.ReminderResponse;
import com.pawtrack.notification.entity.HouseholdMemberId;
import com.pawtrack.notification.entity.Pet;
import com.pawtrack.notification.entity.Reminder;
import com.pawtrack.notification.entity.ReminderSource;
import com.pawtrack.notification.entity.ReminderStatus;
import com.pawtrack.notification.exception.BadRequestException;
import com.pawtrack.notification.exception.ConflictException;
import com.pawtrack.notification.exception.ForbiddenException;
import com.pawtrack.notification.exception.NotFoundException;
import com.pawtrack.notification.mapper.ReminderMapper;
import com.pawtrack.notification.repository.HouseholdMemberRepository;
import com.pawtrack.notification.repository.PetRepository;
import com.pawtrack.notification.repository.ReminderRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reminders API. Access uses the local membership copy: any member of the pet's household may read and
 * change its reminders. Unknown pet/reminder → 404, other household → 403.
 */
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final PetRepository petRepository;
    private final HouseholdMemberRepository memberRepository;
    private final ReminderMapper mapper;
    private final Clock clock;

    /** Filters as in {@code listReminders}; {@code from} inclusive, {@code to} exclusive. */
    public record Filter(UUID petId, ReminderStatus status, Instant from, Instant to) {
    }

    @Transactional(readOnly = true)
    public List<ReminderResponse> list(Jwt jwt, Filter filter) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        List<UUID> households = memberRepository.findHouseholdIdsByUserId(userId(jwt));
        if (households.isEmpty()) {
            return List.of();
        }
        Specification<Reminder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("householdId").in(households));
            if (filter.petId() != null) {
                predicates.add(cb.equal(root.get("petId"), filter.petId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("dueAt"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return reminderRepository.findAll(spec, Sort.by("dueAt", "id")).stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public ReminderResponse create(Jwt jwt, ReminderRequest request) {
        UUID userId = userId(jwt);
        Pet pet = petRepository.findById(request.petId())
            .filter(p -> !p.isDeleted())
            .orElseThrow(() -> new NotFoundException("Pet " + request.petId() + " not found"));
        requireMember(pet.getHouseholdId(), userId);
        Reminder reminder = new Reminder(pet.getPetId(), pet.getHouseholdId(), request.title().strip(),
            request.message(), request.dueAt(), ReminderSource.CUSTOM, null, userId);
        return mapper.toResponse(reminderRepository.save(reminder));
    }

    @Transactional
    public void cancel(Jwt jwt, UUID reminderId) {
        Reminder reminder = accessibleReminder(jwt, reminderId);
        if (reminder.getStatus() == ReminderStatus.CANCELLED) {
            return;
        }
        if (reminder.getStatus() == ReminderStatus.DONE) {
            throw new ConflictException("Reminder is already DONE");
        }
        reminder.cancel(clock.instant());
    }

    @Transactional
    public ReminderResponse markDone(Jwt jwt, UUID reminderId) {
        Reminder reminder = accessibleReminder(jwt, reminderId);
        if (reminder.getStatus() == ReminderStatus.CANCELLED) {
            throw new ConflictException("Reminder is CANCELLED");
        }
        if (reminder.getStatus() != ReminderStatus.DONE) {
            reminder.markDone(clock.instant());
        }
        return mapper.toResponse(reminder);
    }

    private Reminder accessibleReminder(Jwt jwt, UUID reminderId) {
        Reminder reminder = reminderRepository.findById(reminderId)
            .orElseThrow(() -> new NotFoundException("Reminder " + reminderId + " not found"));
        requireMember(reminder.getHouseholdId(), userId(jwt));
        return reminder;
    }

    private void requireMember(UUID householdId, UUID userId) {
        if (!memberRepository.existsById(new HouseholdMemberId(householdId, userId))) {
            throw new ForbiddenException("Not a member of the pet's household");
        }
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
