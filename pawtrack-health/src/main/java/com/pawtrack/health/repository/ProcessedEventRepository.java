package com.pawtrack.health.repository;

import java.util.UUID;

import com.pawtrack.health.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
