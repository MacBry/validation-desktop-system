package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.regime.SessionRunMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repozytorium trybów runu powiązanych z seriami pomiarowymi.
 */
@Repository
public interface SessionRunModeRepository extends JpaRepository<SessionRunMode, Long> {

    /**
     * Pobiera tryb runu dla danej serii (relacja 1:1).
     */
    Optional<SessionRunMode> findBySeriesId(Long seriesId);

    /**
     * Sprawdza czy seria ma zadeklarowany tryb runu.
     */
    boolean existsBySeriesId(Long seriesId);
}
