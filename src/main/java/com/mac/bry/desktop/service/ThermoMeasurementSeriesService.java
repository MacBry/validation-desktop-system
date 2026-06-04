package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.repository.ThermoMeasurementSeriesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ThermoMeasurementSeriesService {

    private final ThermoMeasurementSeriesRepository seriesRepository;

    @Transactional
    public ThermoMeasurementSeries saveSeries(ThermoMeasurementSeries series) {
        log.info("Zapisywanie serii pomiarowej z USB dla rejestratora: {}, liczba próbek: {}", 
                series.getThermoRecorder().getSerialNumber(), series.getMeasurementsCount());
        
        // Zapewnienie relacji dwukierunkowej dla każdego punktu przed kaskadowym zapisem
        if (series.getMeasurements() != null) {
            series.getMeasurements().forEach(point -> point.setSeries(series));
        }
        
        return seriesRepository.save(series);
    }

    public Optional<ThermoMeasurementSeries> getSeriesById(Long id) {
        log.debug("Pobieranie serii pomiarowej o ID: {}", id);
        return seriesRepository.findById(id);
    }

    public List<ThermoMeasurementSeries> getSeriesForRecorder(Long recorderId) {
        log.debug("Pobieranie wszystkich serii pomiarowych dla rejestratora ID: {}", recorderId);
        return seriesRepository.findByThermoRecorderId(recorderId);
    }

    public List<ThermoMeasurementSeries> getSeriesForChamber(Long chamberId) {
        log.debug("Pobieranie wszystkich serii pomiarowych dla komory ID: {}", chamberId);
        return seriesRepository.findByCoolingChamberId(chamberId);
    }

    @Transactional
    public void deleteSeries(Long id) {
        log.warn("Usuwanie serii pomiarowej o ID: {}", id);
        seriesRepository.deleteById(id);
    }
}
