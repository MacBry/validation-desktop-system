package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.Calibration;
import com.mac.bry.desktop.model.ThermoRecorder;
import com.mac.bry.desktop.repository.CalibrationRepository;
import com.mac.bry.desktop.repository.ThermoRecorderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThermoRecorderService {

    private final ThermoRecorderRepository recorderRepository;
    private final CalibrationRepository calibrationRepository;

    public List<ThermoRecorder> getAllRecorders() {
        return recorderRepository.findAll();
    }

    public List<ThermoRecorder> searchRecorders(String query) {
        if (query == null || query.isBlank()) {
            return getAllRecorders();
        }
        return recorderRepository.findBySerialNumberContainingIgnoreCaseOrModelContainingIgnoreCase(query, query);
    }

    @Transactional
    public ThermoRecorder saveRecorder(ThermoRecorder recorder) {
        log.info("Zapisywanie rejestratora: {}", recorder.getSerialNumber());
        return recorderRepository.save(recorder);
    }

    @Transactional
    public void deleteRecorder(Long id) {
        log.warn("Usuwanie rejestratora o ID: {}", id);
        recorderRepository.deleteById(id);
    }

    public String getCalibrationStatus(ThermoRecorder recorder) {
        Calibration latest = recorder.getLatestCalibration();
        if (latest == null) {
            return "BRAK WZORCOWANIA";
        }
        
        LocalDate validUntil = latest.getValidUntil();
        if (validUntil.isBefore(LocalDate.now())) {
            return "NIEWAŻNE (" + validUntil + ")";
        }
        
        if (validUntil.isBefore(LocalDate.now().plusDays(30))) {
            return "WYGASA WKRÓTCE (" + validUntil + ")";
        }
        
        return "WAŻNE DO " + validUntil;
    }

    public List<Calibration> getCalibrationsForRecorder(Long recorderId) {
        return calibrationRepository.findByThermoRecorderIdOrderByCalibrationDateDesc(recorderId);
    }

    @Transactional
    public void deleteCalibration(Long id) {
        log.warn("Usuwanie wzorcowania o ID: {}", id);
        calibrationRepository.deleteById(id);
    }
}
