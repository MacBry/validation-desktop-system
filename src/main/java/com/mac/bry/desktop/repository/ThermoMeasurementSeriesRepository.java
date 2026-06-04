package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThermoMeasurementSeriesRepository extends JpaRepository<ThermoMeasurementSeries, Long> {

    @Override
    @EntityGraph(attributePaths = {
        "thermoRecorder", 
        "thermoRecorder.calibrations", 
        "coolingChamber", 
        "coolingChamber.coolingDevice"
    })
    List<ThermoMeasurementSeries> findAll();

    @EntityGraph(attributePaths = {"thermoRecorder", "coolingChamber"})
    List<ThermoMeasurementSeries> findByThermoRecorderId(Long recorderId);

    @EntityGraph(attributePaths = {
        "thermoRecorder", 
        "thermoRecorder.calibrations", 
        "coolingChamber", 
        "coolingChamber.coolingDevice"
    })
    List<ThermoMeasurementSeries> findByCoolingChamberId(Long chamberId);

    @Override
    @EntityGraph(attributePaths = {"thermoRecorder", "coolingChamber"})
    Optional<ThermoMeasurementSeries> findById(Long id);
}
