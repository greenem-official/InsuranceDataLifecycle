package org.daylight.insurancecompamy.repositories.postgresql;

import org.daylight.insurancecompamy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
