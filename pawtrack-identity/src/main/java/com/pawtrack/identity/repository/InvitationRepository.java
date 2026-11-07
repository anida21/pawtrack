package com.pawtrack.identity.repository;

import java.util.Optional;
import java.util.UUID;

import com.pawtrack.identity.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByCode(String code);
}
