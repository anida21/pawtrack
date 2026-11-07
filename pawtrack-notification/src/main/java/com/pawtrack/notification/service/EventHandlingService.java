package com.pawtrack.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.pawtrack.notification.config.PawtrackProperties;
import com.pawtrack.notification.entity.EmailLog;
import com.pawtrack.notification.entity.HouseholdMember;
import com.pawtrack.notification.entity.HouseholdMemberId;
import com.pawtrack.notification.entity.Pet;
import com.pawtrack.notification.entity.Recipient;
import com.pawtrack.notification.messaging.ConsumedEvent;
import com.pawtrack.notification.messaging.ConsumedEvent.HouseholdDeleted;
import com.pawtrack.notification.messaging.ConsumedEvent.HouseholdInvitationCreated;
import com.pawtrack.notification.messaging.ConsumedEvent.HouseholdMemberAdded;
import com.pawtrack.notification.messaging.ConsumedEvent.HouseholdMemberRemoved;
import com.pawtrack.notification.messaging.ConsumedEvent.MedicationCancelled;
import com.pawtrack.notification.messaging.ConsumedEvent.MedicationScheduled;
import com.pawtrack.notification.messaging.ConsumedEvent.PetCreated;
import com.pawtrack.notification.messaging.ConsumedEvent.PetDeleted;
import com.pawtrack.notification.messaging.ConsumedEvent.UserUpdated;
import com.pawtrack.notification.messaging.ConsumedEvent.VaccinationRecorded;
import com.pawtrack.notification.repository.HouseholdMemberRepository;
import com.pawtrack.notification.repository.MedicationScheduleRepository;
import com.pawtrack.notification.repository.PetRepository;
import com.pawtrack.notification.repository.ProcessedEventRepository;
import com.pawtrack.notification.repository.RecipientRepository;
import com.pawtrack.notification.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies one consumed event in one transaction. The eventId is claimed in {@code processed_events} in the
 * same transaction, so a redelivered event is skipped, and an event whose handling fails is not marked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventHandlingService {

    private final ProcessedEventRepository processedEvents;
    private final RecipientRepository recipientRepository;
    private final HouseholdMemberRepository memberRepository;
    private final PetRepository petRepository;
    private final ReminderRepository reminderRepository;
    private final MedicationScheduleRepository scheduleRepository;
    private final VaccinationReminderService vaccinationReminders;
    private final MedicationReminderService medicationReminders;
    private final MailService mailService;
    private final PawtrackProperties properties;
    private final Clock clock;

    @Transactional
    public void handle(UUID eventId, String type, ConsumedEvent event) {
        Instant now = clock.instant();
        if (processedEvents.claim(eventId, type, now) == 0) {
            log.debug("Skipping duplicate event {} ({})", type, eventId);
            return;
        }
        log.debug("Handling event {} ({})", type, eventId);
        // Java 17: no pattern matching for switch yet
        if (event instanceof HouseholdMemberAdded e) {
            memberAdded(e, now);
        } else if (event instanceof HouseholdMemberRemoved e) {
            memberRepository.findById(new HouseholdMemberId(e.householdId(), e.userId()))
                .ifPresent(memberRepository::delete);
        } else if (event instanceof HouseholdDeleted e) {
            householdDeleted(e, now);
        } else if (event instanceof HouseholdInvitationCreated e) {
            sendInvitation(e);
        } else if (event instanceof UserUpdated e) {
            userUpdated(e, now);
        } else if (event instanceof PetCreated e) {
            petCreated(e);
        } else if (event instanceof PetDeleted e) {
            petDeleted(e, now);
        } else if (event instanceof VaccinationRecorded e) {
            vaccinationReminders.onRecorded(e);
        } else if (event instanceof MedicationScheduled e) {
            medicationReminders.onScheduled(e);
        } else if (event instanceof MedicationCancelled e) {
            medicationReminders.onCancelled(e);
        } else {
            throw new IllegalStateException("No handler for " + event.getClass());
        }
    }

    private void memberAdded(HouseholdMemberAdded e, Instant now) {
        String role = e.role() == null ? "MEMBER" : e.role();
        memberRepository.findById(new HouseholdMemberId(e.householdId(), e.userId())).ifPresentOrElse(
            member -> member.changeRole(role),
            () -> memberRepository.save(new HouseholdMember(e.householdId(), e.userId(), role, now)));
    }

    private void userUpdated(UserUpdated e, Instant now) {
        Recipient recipient = recipientRepository.findById(e.userId()).orElseGet(() -> new Recipient(e.userId()));
        String timezone = HouseholdTimeZones.parse(e.timezone()).isPresent()
            ? e.timezone() : properties.reminders().defaultTimezone().getId();
        recipient.update(e.email(), e.displayName(), timezone,
            e.emailNotifications() == null || e.emailNotifications(), now);
        recipientRepository.save(recipient);
    }

    private void petCreated(PetCreated e) {
        petRepository.findById(e.petId()).ifPresentOrElse(
            pet -> pet.update(e.householdId(), e.name()),
            () -> petRepository.save(new Pet(e.petId(), e.householdId(), e.name())));
    }

    private void petDeleted(PetDeleted e, Instant now) {
        Pet pet = petRepository.findById(e.petId())
            .orElseGet(() -> petRepository.save(new Pet(e.petId(), e.householdId(), PetNames.UNKNOWN_PET)));
        pet.markDeleted();
        medicationReminders.deactivate(scheduleRepository.findByPetIdAndActiveTrue(e.petId()), now);
        reminderRepository.cancelPendingByPet(e.petId(), now);
    }

    private void householdDeleted(HouseholdDeleted e, Instant now) {
        petRepository.findByHouseholdIdAndDeletedFalse(e.householdId()).forEach(Pet::markDeleted);
        medicationReminders.deactivate(scheduleRepository.findByHouseholdIdAndActiveTrue(e.householdId()), now);
        reminderRepository.cancelPendingByHousehold(e.householdId(), now);
        memberRepository.deleteByHouseholdId(e.householdId());
    }

    /**
     * Invitations are transactional mail: sent regardless of emailNotifications (the invitee may not even
     * have an account yet). If SMTP fails the event is retried, then dead-lettered.
     */
    private void sendInvitation(HouseholdInvitationCreated e) {
        String subject = "You're invited to join %s on PawTrack".formatted(e.householdName());
        String body = """
            Hello,

            you have been invited to join the household "%s" on PawTrack.

            Sign in at %s and accept the invitation with this code:

                %s

            The code is valid until %s (UTC).

            -- PawTrack
            """.formatted(e.householdName(), mailService.appUrl(), e.code(), e.expiresAt());
        mailService.send(EmailLog.Kind.INVITATION, e.householdId(), e.email(), subject, body);
    }
}
