package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.Calibration;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CalibrationRepository extends JpaRepository<Calibration, Long> {
    @EntityGraph(attributePaths = {"points", "thermoRecorder", "thermoRecorder.model"})
    List<Calibration> findByThermoRecorderIdOrderByCalibrationDateDesc(Long recorderId);

    /**
     * Najnowsze świadectwa per rejestrator, których ważność upływa do podanej daty
     * (włącznie z już przeterminowanymi). Starsze, zastąpione świadectwa są pomijane —
     * liczy się wyłącznie aktualne świadectwo każdego rejestratora.
     */
    @Query("""
            SELECT c FROM Calibration c
            JOIN FETCH c.thermoRecorder tr
            WHERE c.validUntil <= :threshold
              AND c.calibrationDate = (
                  SELECT MAX(c2.calibrationDate) FROM Calibration c2
                  WHERE c2.thermoRecorder = c.thermoRecorder)
            ORDER BY c.validUntil ASC
            """)
    List<Calibration> findLatestExpiringUntil(@Param("threshold") LocalDate threshold);
}
