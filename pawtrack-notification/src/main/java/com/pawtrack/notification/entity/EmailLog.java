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

/** One email handed to the SMTP server. */
@Entity
@Table(name = "email_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailLog {

    public enum Kind { REMINDER, INVITATION }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Kind kind;

    /** Reminder id (REMINDER) or household id (INVITATION). */
    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;

    @Column(nullable = false, updatable = false)
    private String recipient;

    @Column(nullable = false, updatable = false)
    private String subject;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    public EmailLog(Kind kind, UUID referenceId, String recipient, String subject, Instant sentAt) {
        this.kind = kind;
        this.referenceId = referenceId;
        this.recipient = recipient;
        this.subject = subject.length() > 255 ? subject.substring(0, 255) : subject;
        this.sentAt = sentAt;
    }
}
