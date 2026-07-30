package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.OperatorShiftConfig;
import com.mac.bry.desktop.security.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OperatorShiftConfigRepository extends JpaRepository<OperatorShiftConfig, Long> {

    Optional<OperatorShiftConfig> findByUserAndActiveTrue(User user);

    /**
     * Konfiguracja globalna (user_id IS NULL) — fallback dla operatorów bez
     * własnego wpisu.
     */
    Optional<OperatorShiftConfig> findFirstByUserIsNullAndActiveTrue();
}