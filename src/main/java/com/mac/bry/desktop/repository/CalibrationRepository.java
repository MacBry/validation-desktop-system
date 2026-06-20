package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.Calibration;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CalibrationRepository extends JpaRepository<Calibration, Long> {
    @EntityGraph(attributePaths = {"points", "thermoRecorder", "thermoRecorder.model"})
    List<Calibration> findByThermoRecorderIdOrderByCalibrationDateDesc(Long recorderId);
}
