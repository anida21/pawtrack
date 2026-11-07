package com.pawtrack.notification.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Something to tell the pet's household at {@code dueAt}.
 * Lifecycle: PENDING → SENT (scheduler) and PENDING|SENT → DONE | CANCELLED.
 */
@Entity
@Table(name = "reminders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reminder {

    public static final int TITLE_MAX = 120;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pet_id", nullable = false, updatable = false)
    private UUID petId;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(nullable = false, length = TITLE_MAX)
    private String title;

    @Column(length = 1000)
    private String message;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ReminderSource source;

    @Column(name = "source_id", updatable = false)
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderStatus status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Reminder(UUID petId, UUID householdId, String title, String message, Instant dueAt,
                    ReminderSource source, UUID sourceId, UUID createdBy) {
        this.petId = petId;
        this.householdId = householdId;
        this.title = title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX - 1) + "…" : title;
        this.message = message;
        this.dueAt = dueAt;
        this.source = source;
        this.sourceId = sourceId;
        this.createdBy = createdBy;
        this.status = ReminderStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public boolean isOpen() {
        return status == ReminderStatus.PENDING || status == ReminderStatus.SENT;
    }

    public void markSent(Instant now) {
        this.status = ReminderStatus.SENT;
        this.sentAt = now;
        this.updatedAt = now;
    }

    public void markDone(Instant now) {
        this.status = ReminderStatus.DONE;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.status = ReminderStatus.CANCELLED;
        this.updatedAt = now;
    }
}
