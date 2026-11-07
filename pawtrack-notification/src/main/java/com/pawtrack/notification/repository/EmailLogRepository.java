package com.pawtrack.notification.repository;

import java.util.List;
import java.util.UUID;

import com.pawtrack.notification.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {

    List<EmailLog> findByReferenceId(UUID referenceId);
}
