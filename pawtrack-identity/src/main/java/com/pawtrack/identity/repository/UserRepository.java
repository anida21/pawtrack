package com.pawtrack.identity.repository;

import java.util.UUID;

import com.pawtrack.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
}
