package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.ThermoRecorder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThermoRecorderRepository extends JpaRepository<ThermoRecorder, Long> {
    Optional<ThermoRecorder> findBySerialNumber(String serialNumber);

    @Override
    @EntityGraph(attributePaths = {"calibrations", "department", "laboratory"})
    List<ThermoRecorder> findAll();

    @EntityGraph(attributePaths = {"calibrations", "department", "laboratory"})
    List<ThermoRecorder> findBySerialNumberContainingIgnoreCaseOrModelContainingIgnoreCase(String serialNumber, String model);
}
