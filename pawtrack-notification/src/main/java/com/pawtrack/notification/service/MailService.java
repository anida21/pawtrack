package com.pawtrack.notification.service;

import java.time.Clock;
import java.util.UUID;

import com.pawtrack.notification.config.PawtrackProperties;
import com.pawtrack.notification.entity.EmailLog;
import com.pawtrack.notification.repository.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Plain-text email via SMTP (Mailpit locally); every sent email is written to {@code email_log}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    private final PawtrackProperties properties;
    private final Clock clock;

    /** @throws MailException if the SMTP server does not accept the message */
    public void send(EmailLog.Kind kind, UUID referenceId, String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        emailLogRepository.save(new EmailLog(kind, referenceId, to, subject, clock.instant()));
        log.info("Sent {} email '{}' to {}", kind, subject, to);
    }

    public String appUrl() {
        return properties.mail().appUrl();
    }
}
