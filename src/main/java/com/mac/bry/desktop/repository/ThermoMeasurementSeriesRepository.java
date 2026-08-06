package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.dto.RecorderReadoutSummary;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Metadane odczytów rejestratora, od najnowszego. Projekcja, a nie encja —
     * {@code rawHexDump} to LONGTEXT z surową transmisją USB i nie ma powodu
     * ciągnąć go do karty szczegółów.
     * <p>
     * Ogranicz wynik przez {@link org.springframework.data.domain.Pageable},
     * gdy potrzebny jest sam ostatni odczyt.
     */
    @Query("""
            select new com.mac.bry.desktop.dto.RecorderReadoutSummary(
                s.importedAt, s.importedBy, s.batteryRemainingDays,
                s.loggingIntervalMinutes, s.measurementsCount, c.chamberName)
            from ThermoMeasurementSeries s
            left join s.coolingChamber c
            where s.thermoRecorder.id = :recorderId
            order by s.importedAt desc
            """)
    List<RecorderReadoutSummary> findReadoutSummaries(@Param("recorderId") Long recorderId, Pageable pageable);

    long countByThermoRecorderId(Long recorderId);
}
