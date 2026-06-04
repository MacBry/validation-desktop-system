package com.mac.bry.desktop.service;

import com.mac.bry.desktop.dto.*;
import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.*;
import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.AccessLogRepository;
import com.mac.bry.desktop.security.repository.DepartmentRepository;
import com.mac.bry.desktop.security.repository.LaboratoryRepository;
import com.mac.bry.desktop.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardStatisticsService {

    private final ThermoRecorderRepository thermoRecorderRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final CoolingDeviceRepository coolingDeviceRepository;
    private final CoolingChamberRepository coolingChamberRepository;
    private final ThermoMeasurementSeriesRepository thermoMeasurementSeriesRepository;
    private final AccessLogRepository accessLogRepository;

    @Transactional(readOnly = true)
    public DashboardStatistics calculateAllStatistics() {
        return new DashboardStatistics(
            calculateRecorderStatistics(),
            calculateCalibrationStatistics(),
            calculateUserStatistics(),
            departmentRepository.count(),
            laboratoryRepository.count(),
            calculateDeviceAndChamberStatistics(),
            calculateUsbOperationStatistics()
        );
    }

    private RecorderStatistics calculateRecorderStatistics() {
        List<ThermoRecorder> recorders = thermoRecorderRepository.findAll();
        long total = recorders.size();
        long active = recorders.stream().filter(r -> r.getStatus() == RecorderStatus.ACTIVE).count();
        long underCalibration = recorders.stream().filter(r -> r.getStatus() == RecorderStatus.UNDER_CALIBRATION).count();
        long inactive = total - active - underCalibration;

        log.debug("Recorder statistics: total={}, active={}, underCalibration={}, inactive={}",
                total, active, underCalibration, inactive);

        return new RecorderStatistics(total, active, underCalibration, inactive);
    }

    private CalibrationStatistics calculateCalibrationStatistics() {
        List<ThermoRecorder> recorders = thermoRecorderRepository.findAll();
        LocalDate today = LocalDate.now();
        LocalDate in30Days = today.plusDays(30);

        long expired = 0;
        long expiringSoon = 0;
        long valid = 0;

        for (ThermoRecorder r : recorders) {
            if (r.getCalibrations() == null || r.getCalibrations().isEmpty()) {
                expired++;
            } else {
                Calibration latest = r.getCalibrations().get(0);
                LocalDate validUntil = latest.getValidUntil();

                if (validUntil == null || validUntil.isBefore(today)) {
                    expired++;
                } else if (validUntil.isBefore(in30Days)) {
                    expiringSoon++;
                } else {
                    valid++;
                }
            }
        }

        log.debug("Calibration statistics: valid={}, expiringSoon={}, expired={}",
                valid, expiringSoon, expired);

        return new CalibrationStatistics(valid, expiringSoon, expired);
    }

    private UserStatistics calculateUserStatistics() {
        List<User> users = userRepository.findAll();
        long enabled = users.stream().filter(User::isEnabled).count();
        long locked = users.stream().filter(User::isLocked).count();

        log.debug("User statistics: enabled={}, locked={}", enabled, locked);

        return new UserStatistics(enabled, locked);
    }

    private DeviceStatistics calculateDeviceAndChamberStatistics() {
        long totalDevices = coolingDeviceRepository.count();
        List<CoolingChamber> chambers = coolingChamberRepository.findAll();
        List<ThermoMeasurementSeries> allSeries = thermoMeasurementSeriesRepository.findAll();

        Map<Long, List<ThermoMeasurementSeries>> chamberSeriesMap = new HashMap<>();
        for (ThermoMeasurementSeries series : allSeries) {
            if (series.getCoolingChamber() != null) {
                chamberSeriesMap.computeIfAbsent(series.getCoolingChamber().getId(), k -> new ArrayList<>())
                        .add(series);
            }
        }

        long validChambers = 0;
        long warningChambers = 0;
        long notValidatedChambers = 0;

        for (CoolingChamber chamber : chambers) {
            List<ThermoMeasurementSeries> seriesList = chamberSeriesMap.get(chamber.getId());
            if (seriesList == null || seriesList.isEmpty()) {
                notValidatedChambers++;
            } else {
                boolean hasInvalidCal = seriesList.stream()
                        .anyMatch(s -> {
                            Calibration cal = s.getThermoRecorder().getLatestCalibration();
                            return cal == null || !cal.isValid();
                        });
                if (hasInvalidCal) {
                    warningChambers++;
                } else {
                    validChambers++;
                }
            }
        }

        log.debug("Device statistics: totalDevices={}, chambers={}, valid={}, warning={}, notValidated={}",
                totalDevices, chambers.size(), validChambers, warningChambers, notValidatedChambers);

        return new DeviceStatistics(totalDevices, chambers.size(), validChambers, warningChambers, notValidatedChambers);
    }

    private UsbStatistics calculateUsbOperationStatistics() {
        List<com.mac.bry.desktop.security.model.AccessLog> usbReads = accessLogRepository.findByActionInOrderByTimestampDesc(List.of("USB_READING", "PDF_IMPORT"));
        List<com.mac.bry.desktop.security.model.AccessLog> usbProgs = accessLogRepository.findByActionOrderByTimestampDesc("USB_PROGRAMMING");
        long total = usbReads.size() + usbProgs.size();

        log.debug("USB statistics: reads={}, programs={}, total={}",
                usbReads.size(), usbProgs.size(), total);

        return new UsbStatistics(total, usbReads.size(), usbProgs.size());
    }
}
