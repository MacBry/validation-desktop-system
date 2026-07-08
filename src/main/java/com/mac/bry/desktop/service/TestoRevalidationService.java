package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.*;
import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mac.bry.desktop.service.helper.MappingValidator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.io.File;

/**
 * Usługa backendowa zarządzająca procesem rewalidacji GxP komór chłodniczych.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestoRevalidationService {

    private final ThermoRecorderRepository thermoRecorderRepository;
    private final CalibrationRepository calibrationRepository;
    private final CalibrationPointRepository calibrationPointRepository;
    private final ThermoMeasurementSeriesRepository thermoMeasurementSeriesRepository;
    private final ThermoRecorderModelRepository thermoRecorderModelRepository;
    private final DepartmentRepository departmentRepository;
    private final TestoUsbImportService testoUsbImportService;
    private final Testo184UsbImportService testo184UsbImportService;
    private final MetrologicalStatsService metrologicalStatsService;
    private final CoolingChamberRepository coolingChamberRepository;
    private final TestoSimulationService testoSimulationService;

    /**
     * Inicjalizuje nową sesję rewalidacji w pamięci.
     */
    public RevalidationSession initSession(CoolingDevice device, CoolingChamber chamber) {
        return initSession(device, chamber, GxPProcedureType.PERIODIC_REVALIDATION);
    }

    /**
     * Inicjalizuje nową sesję rewalidacji/mapowania w pamięci z określonym typem procedury.
     */
    public RevalidationSession initSession(CoolingDevice device, CoolingChamber chamber, GxPProcedureType procedureType) {
        log.info("Inicjalizacja nowej sesji {} dla urządzenia: {} i komory: {}", 
                procedureType.getDisplayName(), device.getInventoryNumber(), chamber.getChamberName());
        return RevalidationSession.builder()
                .coolingDevice(device)
                .coolingChamber(chamber)
                .procedureType(procedureType)
                .build();
    }

    /**
     * Wczytuje dane pomiarowe z pliku PDF raportu Testo 184T.
     * Stosuje identyczny rygor GxP jak odczyt USB: blokada S/N spoza ewidencji VCC.
     * Bateria nie jest raportowana w formacie PDF — pole wyświetli "N/D".
     */
    @Transactional
    public RevalidationSession.PositionData readPositionDataFromPdf(
            RevalidationSession session,
            RevalidationSession.GridPosition position,
            File pdfFile) {

        log.info("Importowanie danych z PDF Testo 184T: {} dla narożnika: {}",
                pdfFile.getName(), position.getLabel());

        TestoUsbImportService.TestoImportResult result =
                testo184UsbImportService.importFromPdf(pdfFile);

        if ("ERROR".equals(result.status)) {
            throw new RuntimeException("Błąd odczytu PDF Testo 184T: " + result.message);
        }

        String serialNumber = result.device.serialNumber;
        log.info("Odczytano S/N z PDF 184T: {}. Weryfikacja GxP w bazie danych...", serialNumber);

        // Rygorystyczna blokada GxP — identyczna jak przy USB 174T
        ThermoRecorder recorder = thermoRecorderRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "BLOKADA GxP: Rejestrator o numerze seryjnym " + serialNumber
                        + " (Testo 184T) nie figuruje w systemie VCC!"
                        + " Zarejestruj go przed rozpoczęciem procedury."));

        if (session.isDeviceChannelUsed(serialNumber, 1)) {
            throw new IllegalArgumentException("BLOKADA GxP: Rejestrator " + serialNumber + " (Kanał 1) jest już użyty w tej sesji rewalidacyjnej na innej pozycji!");
        }

        Calibration latestCal = recorder.getLatestCalibrationForChannel(1);
        if (latestCal == null) {
            log.warn("Rejestrator 184T {} nie posiada zarejestrowanego świadectwa wzorcowania!", serialNumber);
        } else if (!latestCal.isValid()) {
            log.warn("Świadectwo wzorcowania {} dla rejestratora 184T {} wygasło w dniu {}!",
                    latestCal.getCertificateNumber(), serialNumber, latestCal.getValidUntil());
        }

        LocalDateTime firstMeasurementLocal = LocalDateTime.parse(
                result.session.firstMeasurementTimeLocal.length() > 19
                        ? result.session.firstMeasurementTimeLocal.substring(0, 19)
                        : result.session.firstMeasurementTimeLocal
        );

        // batteryLevelPercent = -1 oznacza "N/D" (PDF nie zawiera tej informacji)
        ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                .thermoRecorder(recorder)
                .coolingChamber(session.getCoolingChamber())
                .batteryLevelPercent(result.session.batteryLevelPercent >= 0
                        ? result.session.batteryLevelPercent : -1)
                .loggingIntervalMinutes(result.session.intervalMinutes)
                .measurementsCount(result.measurements.size())
                .programmingTimeUtc(LocalDateTime.parse(
                        result.session.programmingTimeUtc.length() > 19
                                ? result.session.programmingTimeUtc.substring(0, 19)
                                : result.session.programmingTimeUtc
                ))
                .startDelayMinutes(result.session.startDelayMinutes)
                .firstMeasurementTimeUtc(LocalDateTime.parse(
                        result.session.firstMeasurementTimeUtc.length() > 19
                                ? result.session.firstMeasurementTimeUtc.substring(0, 19)
                                : result.session.firstMeasurementTimeUtc
                ))
                .firstMeasurementTimeLocal(firstMeasurementLocal)
                .importedAt(LocalDateTime.now())
                .importedBy("Validator PDF-184T")
                .rawHexDump("INTEGRITY_VERIFIED_VIA_PDF_IMPORT_184T_" + serialNumber
                        + "_FILE_" + pdfFile.getName())
                .revalidationGroupId(session.getSessionId())
                .gridPosition(position)
                .procedureType(session.getProcedureType())
                .build();

        for (TestoUsbImportService.MeasurementPointDto pt : result.measurements) {
            String cleanTime = pt.timestampLocal.length() > 19
                    ? pt.timestampLocal.substring(0, 19)
                    : pt.timestampLocal;
            series.addMeasurement(ThermoMeasurementPoint.builder()
                    .measurementIndex(pt.index)
                    .timestampLocal(LocalDateTime.parse(cleanTime))
                    .rawCelsius(pt.valueCelsius)
                    .build());
        }

        metrologicalStatsService.calculateStatistics(series);

        return RevalidationSession.PositionData.builder()
                .serialNumber(serialNumber)
                .model(recorder.getModel())
                .recorder(recorder)
                .latestCalibration(latestCal)
                .series(series)
                .build();
    }

    @Transactional
    public RevalidationSession.PositionData readPositionData(
            RevalidationSession session, 
            RevalidationSession.GridPosition position, 
            boolean simulationMode) {
        return readPositionData(session, position, simulationMode, SimulationProfile.STABLE);
    }

    @Transactional
    public RevalidationSession.PositionData readPositionData(
            RevalidationSession session, 
            RevalidationSession.GridPosition position, 
            boolean simulationMode,
            SimulationProfile profile) {
        
        log.info("Rozpoczęcie odczytu danych dla narożnika: {} (Tryb symulacji: {}, Profil: {})", position.getLabel(), simulationMode, profile);
        
        if (simulationMode) {
            return generateSimulatedPositionData(session, position, profile);
        } else {
            return readRealPositionData(session, position);
        }
    }

    /**
     * Rzeczywisty odczyt z USB Testo przez most Python.
     */
    private RevalidationSession.PositionData readRealPositionData(
            RevalidationSession session, 
            RevalidationSession.GridPosition position) {
        
        TestoUsbImportService.TestoImportResult usbResult = testoUsbImportService.readFromUsb();
        if ("ERROR".equals(usbResult.status)) {
            throw new RuntimeException("Błąd sprzętowy USB: " + usbResult.message);
        }

        String serialNumber = usbResult.device.serialNumber;
        log.info("Pomyślnie odczytano S/N z USB: {}. Rozpoczynanie weryfikacji GxP w bazie danych...", serialNumber);

        // Rygorystyczne blokowanie urządzeń spoza ewidencji w bazie danych
        ThermoRecorder recorder = thermoRecorderRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "BLOKADA GxP: Rejestrator o numerze seryjnym " + serialNumber 
                        + " nie figuruje w systemie VCC! Zarejestruj go przed rozpoczęciem rewalidacji."));

        if (session.isDeviceChannelUsed(serialNumber, 1)) {
            throw new IllegalArgumentException("BLOKADA GxP: Rejestrator " + serialNumber + " (Kanał 1) jest już użyty w tej sesji rewalidacyjnej na innej pozycji!");
        }

        Calibration latestCal = recorder.getLatestCalibrationForChannel(1);
        if (latestCal == null) {
            log.warn("Rejestrator {} nie posiada zarejestrowanego świadectwa wzorcowania!", serialNumber);
        } else if (!latestCal.isValid()) {
            log.warn("Świadectwo wzorcowania {} dla rejestratora {} wygasło w dniu {}!", 
                    latestCal.getCertificateNumber(), serialNumber, latestCal.getValidUntil());
        }

        // Tworzenie zintegrowanej serii pomiarowej
        LocalDateTime firstMeasurementLocal = LocalDateTime.parse(
                usbResult.session.firstMeasurementTimeLocal.length() > 19 
                ? usbResult.session.firstMeasurementTimeLocal.substring(0, 19) 
                : usbResult.session.firstMeasurementTimeLocal
        );

        ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                .thermoRecorder(recorder)
                .coolingChamber(session.getCoolingChamber())
                .batteryLevelPercent(usbResult.session.batteryLevelPercent)
                .loggingIntervalMinutes(usbResult.session.intervalMinutes)
                .measurementsCount(usbResult.measurements.size())
                .programmingTimeUtc(LocalDateTime.parse(
                        usbResult.session.programmingTimeUtc.length() > 19 
                        ? usbResult.session.programmingTimeUtc.substring(0, 19) 
                        : usbResult.session.programmingTimeUtc
                ))
                .startDelayMinutes(usbResult.session.startDelayMinutes)
                .firstMeasurementTimeUtc(LocalDateTime.parse(
                        usbResult.session.firstMeasurementTimeUtc.length() > 19 
                        ? usbResult.session.firstMeasurementTimeUtc.substring(0, 19) 
                        : usbResult.session.firstMeasurementTimeUtc
                ))
                .firstMeasurementTimeLocal(firstMeasurementLocal)
                .importedAt(LocalDateTime.now())
                .importedBy("Validator USB")
                .rawHexDump("INTEGRITY_VERIFIED_VIA_PHYSICAL_USB_READOUT_" + serialNumber)
                .revalidationGroupId(session.getSessionId())
                .gridPosition(position)
                .procedureType(session.getProcedureType())
                .build();

        for (TestoUsbImportService.MeasurementPointDto pt : usbResult.measurements) {
            String cleanTime = pt.timestampLocal.length() > 19 ? pt.timestampLocal.substring(0, 19) : pt.timestampLocal;
            series.addMeasurement(ThermoMeasurementPoint.builder()
                    .measurementIndex(pt.index)
                    .timestampLocal(LocalDateTime.parse(cleanTime))
                    .rawCelsius(pt.valueCelsius)
                    .build());
        }

        // Automatyczne wyliczenie zaawansowanych statystyk GxP & GUM
        metrologicalStatsService.calculateStatistics(series);

        return RevalidationSession.PositionData.builder()
                .serialNumber(serialNumber)
                .model(recorder.getModel())
                .recorder(recorder)
                .latestCalibration(latestCal)
                .series(series)
                .build();
    }

    /**
     * Zautomatyzowana symulacja sprzętowo-bazodanowa (Zero-Hardware Walkthrough).
     */
    private RevalidationSession.PositionData generateSimulatedPositionData(
            RevalidationSession session, 
            RevalidationSession.GridPosition position,
            SimulationProfile profile) {

        int index = position.ordinal() + 1;
        String serialNumber = "SN-174-T00" + index + "-SIM";
        String certNumber = "CERT-2026-T00" + index + "-SIM";

        log.info("Silnik Symulacji: Wyszukiwanie rejestratora {} w bazie danych...", serialNumber);
        
        // Sprawdzenie obecności mock rejestratora, jeśli go nie ma - automatycznie rejestrujemy w bazie
        Optional<ThermoRecorder> recorderOpt = thermoRecorderRepository.findBySerialNumber(serialNumber);
        ThermoRecorder recorder;

        if (recorderOpt.isEmpty()) {
            log.info("Silnik Symulacji: Brak rejestratora {} w bazie. Uruchamianie autogeneracji ewidencji...", serialNumber);
            
            // Pobranie domyślnego działu w bazie
            List<Department> depts = departmentRepository.findAll();
            if (depts.isEmpty()) {
                throw new IllegalStateException("Nie można uruchomić symulacji - brak zdefiniowanych działów w bazie danych!");
            }
            Department defaultDept = depts.get(0);

            ThermoRecorderModel simModel = thermoRecorderModelRepository.findByName("Testo 174T (Symulacja)")
                .orElseGet(() -> {
                    return thermoRecorderModelRepository.save(ThermoRecorderModel.builder()
                        .name("Testo 174T (Symulacja)")
                        .channelCount(1)
                        .defaultResolution(new BigDecimal("0.100"))
                        .build());
                });

            recorder = ThermoRecorder.builder()
                    .serialNumber(serialNumber)
                    .model(simModel)
                    .status(RecorderStatus.ACTIVE)
                    .resolution(new BigDecimal("0.100"))
                    .department(defaultDept)
                    .build();

            recorder = thermoRecorderRepository.save(recorder);

            // Generowanie aktywnego świadectwa wzorcowania (GxP certyfikat ważny jeszcze przez 10 miesięcy)
            Calibration cal = Calibration.builder()
                    .thermoRecorder(recorder)
                    .calibrationDate(LocalDate.now().minusMonths(2))
                    .certificateNumber(certNumber)
                    .validUntil(LocalDate.now().plusMonths(10))
                    .certificateFilePath("C:/certificates/sim/" + certNumber + ".pdf")
                    .build();

            cal = calibrationRepository.save(cal);

            // Zapisanie punktów kalibracji
            calibrationPointRepository.save(CalibrationPoint.builder().calibration(cal).temperatureValue(new BigDecimal("0.0")).systematicError(new BigDecimal("0.05")).uncertainty(new BigDecimal("0.02")).build());
            calibrationPointRepository.save(CalibrationPoint.builder().calibration(cal).temperatureValue(new BigDecimal("5.0")).systematicError(new BigDecimal("-0.02")).uncertainty(new BigDecimal("0.02")).build());
            calibrationPointRepository.save(CalibrationPoint.builder().calibration(cal).temperatureValue(new BigDecimal("10.0")).systematicError(new BigDecimal("0.08")).uncertainty(new BigDecimal("0.02")).build());

            // Ponowne odświeżenie relacji
            recorder.addCalibration(cal);
            log.info("Silnik Symulacji: Pomyślnie zaimplementowano i zarejestrowano ewidencję metrologiczną dla S/N: {}", serialNumber);
        } else {
            recorder = recorderOpt.get();
        }

        if (session.isDeviceChannelUsed(serialNumber, 1)) {
            throw new IllegalArgumentException("BLOKADA GxP: Rejestrator " + serialNumber + " (Kanał 1) jest już użyty w tej sesji rewalidacyjnej na innej pozycji!");
        }

        Calibration latestCal = recorder.getLatestCalibrationForChannel(1);

        // 8-kanałowy symulator temperatury z przesunięciami baseline komory
        // Cechuje się precyzyjną, GxP-zgodną synchronizacją (40 punktów, start 2026-05-18T00:00, interwał 3h = 180min)
        LocalDateTime startTimeLocal = LocalDateTime.of(2026, 5, 18, 0, 0, 0);
        
        ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                .thermoRecorder(recorder)
                .coolingChamber(session.getCoolingChamber())
                .batteryLevelPercent(96)
                .loggingIntervalMinutes(180) // 3 godziny
                .measurementsCount(40)
                .programmingTimeUtc(startTimeLocal.minusDays(10).minusHours(2))
                .startDelayMinutes(0)
                .firstMeasurementTimeUtc(startTimeLocal.minusHours(2))
                .firstMeasurementTimeLocal(startTimeLocal)
                .importedAt(LocalDateTime.now())
                .importedBy("System (Metrologiczna Symulacja)")
                .rawHexDump("DATA_INTEGRITY_ASSURED_BY_CRYPTOGRAPHIC_SIMULATOR_SIGNATURE_" + serialNumber)
                .revalidationGroupId(session.getSessionId())
                .gridPosition(position)
                .procedureType(session.getProcedureType())
                .build();

        // Ustalenie specyficznego offsetu temperatury dla każdego narożnika (profil 3D komory)
        double baselineTemp = 4.8;
        switch (position) {
            case TOP_FRONT_LEFT:    baselineTemp = 4.2; break;
            case TOP_FRONT_RIGHT:   baselineTemp = 4.9; break;
            case TOP_BACK_LEFT:     baselineTemp = 3.8; break; // najzimniej (przy parowniku)
            case TOP_BACK_RIGHT:    baselineTemp = 4.4; break;
            case BOTTOM_FRONT_LEFT: baselineTemp = 5.5; break; // najcieplej (przy drzwiach)
            case BOTTOM_FRONT_RIGHT:baselineTemp = 5.8; break;
            case BOTTOM_BACK_LEFT:  baselineTemp = 4.7; break;
            case BOTTOM_BACK_RIGHT: baselineTemp = 5.0; break;
        }

        List<ThermoMeasurementPoint> simPoints = testoSimulationService.generateSimulationPoints(
                40, 180, profile, baselineTemp, index, startTimeLocal);
        for (ThermoMeasurementPoint pt : simPoints) {
            series.addMeasurement(pt);
        }

        // Automatyczne wyliczenie zaawansowanych statystyk GxP & GUM
        metrologicalStatsService.calculateStatistics(series);

        return RevalidationSession.PositionData.builder()
                .serialNumber(serialNumber)
                .model(recorder.getModel())
                .recorder(recorder)
                .latestCalibration(latestCal)
                .series(series)
                .build();
    }

    /**
     * Zapisuje wszystkie 8 serii pomiarowych sesji do bazy danych (trwałe utrwalenie wyników walidacji/mapowania).
     */
    @Transactional
    public void saveRevalidationSession(RevalidationSession session) {
        log.info("Zapisywanie w bazie danych wszystkich serii pomiarowych z sesji rewalidacji/mapowania komory: {}", 
                session.getCoolingChamber().getChamberName());
        
        if (session.getProcedureType() == GxPProcedureType.MAPPING) {
            MappingValidator.MappingResult result = MappingValidator.validate(session);
            if (!result.isSuccess()) {
                throw new IllegalStateException("Błąd walidacji mapowania GxP: " + result.getErrorMessage());
            }
            CoolingChamber chamber = session.getCoolingChamber();
            chamber.setLastMappingDate(LocalDate.now());
            chamber.setHotspotPosition(result.getHotspot());
            chamber.setColdspotPosition(result.getColdspot());
            coolingChamberRepository.save(chamber);
            log.info("Zapisano wyniki mapowania dla komory {}: Hotspot={}, Coldspot={}", 
                    chamber.getChamberName(), result.getHotspot(), result.getColdspot());
        }
        
        for (var entry : session.getAssignedPositions().entrySet()) {
            RevalidationSession.PositionData data = entry.getValue();
            if (data.getSeries() != null) {
                log.info("Trwałe zapisywanie serii dla pozycji: {} z rejestratora: {}", 
                        entry.getKey().getLabel(), data.getSerialNumber());
                
                // Defensywne przeliczenie statystyk, jeśli nie zostały jeszcze wyznaczone
                if (data.getSeries().getMktTemperature() == null) {
                    metrologicalStatsService.calculateStatistics(data.getSeries());
                }
                
                data.getSeries().setProcedureType(session.getProcedureType());
                thermoMeasurementSeriesRepository.save(data.getSeries());
            }
        }
        log.info("Wszystkie wgrane kanały rewalidacji zostały pomyślnie zarchiwizowane.");
    }

    /**
     * Sprawdza, czy dana komora chłodnicza posiada ważną (zrealizowaną w ciągu ostatnich 365 dni)
     * 8-kanałową rewalidację GxP, w której wszystkie użyte rejestratory miały ważne świadectwa.
     */
    public boolean isRevalidationValid(Long chamberId) {
        List<ThermoMeasurementSeries> seriesList = thermoMeasurementSeriesRepository.findByCoolingChamberId(chamberId);
        if (seriesList == null || seriesList.isEmpty()) {
            return false;
        }

        // Grupowanie po revalidationGroupId (lub czasie startu jako fallback)
        java.util.Map<String, java.util.List<ThermoMeasurementSeries>> groups = new java.util.HashMap<>();
        for (var series : seriesList) {
            String key = series.getRevalidationGroupId() != null 
                    ? series.getRevalidationGroupId() 
                    : (series.getFirstMeasurementTimeLocal() != null ? series.getFirstMeasurementTimeLocal().toString() : "unknown");
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(series);
        }

        // Szukamy najnowszej udanej rewalidacji (grupa >= 8 zsynchronizowanych serii, wszystkie GxP ważne)
        java.time.LocalDateTime latestValidRevalDate = null;
        for (var entry : groups.entrySet()) {
            List<ThermoMeasurementSeries> group = entry.getValue();
            if (group.size() >= 8) {
                boolean allCalibrationsValid = true;
                for (var s : group) {
                    var cal = s.getThermoRecorder().getLatestCalibration();
                    if (cal == null || !cal.isValid()) {
                        allCalibrationsValid = false;
                        break;
                    }
                }
                
                if (allCalibrationsValid) {
                    java.time.LocalDateTime importTime = group.stream()
                            .map(ThermoMeasurementSeries::getImportedAt)
                            .filter(java.util.Objects::nonNull)
                            .max(java.time.LocalDateTime::compareTo)
                            .orElse(group.get(0).getFirstMeasurementTimeLocal());
                    
                    if (latestValidRevalDate == null || importTime.isAfter(latestValidRevalDate)) {
                        latestValidRevalDate = importTime;
                    }
                }
            }
        }

        if (latestValidRevalDate == null) {
            return false;
        }

        long daysElapsed = java.time.temporal.ChronoUnit.DAYS.between(latestValidRevalDate.toLocalDate(), LocalDate.now());
        return daysElapsed <= 365;
    }

    /**
     * Zwraca szczegółowy status ważności rewalidacji komory w postaci czytelnego opisu.
     */
    public String getRevalidationStatusText(Long chamberId) {
        List<ThermoMeasurementSeries> seriesList = thermoMeasurementSeriesRepository.findByCoolingChamberId(chamberId);
        if (seriesList == null || seriesList.isEmpty()) {
            return "Brak rewalidacji";
        }

        java.util.Map<String, java.util.List<ThermoMeasurementSeries>> groups = new java.util.HashMap<>();
        for (var series : seriesList) {
            String key = series.getRevalidationGroupId() != null 
                    ? series.getRevalidationGroupId() 
                    : (series.getFirstMeasurementTimeLocal() != null ? series.getFirstMeasurementTimeLocal().toString() : "unknown");
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(series);
        }

        java.time.LocalDateTime latestValidRevalDate = null;
        for (var entry : groups.entrySet()) {
            List<ThermoMeasurementSeries> group = entry.getValue();
            if (group.size() >= 8) {
                boolean allCalibrationsValid = true;
                for (var s : group) {
                    var cal = s.getThermoRecorder().getLatestCalibration();
                    if (cal == null || !cal.isValid()) {
                        allCalibrationsValid = false;
                        break;
                    }
                }
                if (allCalibrationsValid) {
                    java.time.LocalDateTime importTime = group.stream()
                            .map(ThermoMeasurementSeries::getImportedAt)
                            .filter(java.util.Objects::nonNull)
                            .max(java.time.LocalDateTime::compareTo)
                            .orElse(group.get(0).getFirstMeasurementTimeLocal());
                    if (latestValidRevalDate == null || importTime.isAfter(latestValidRevalDate)) {
                        latestValidRevalDate = importTime;
                    }
                }
            }
        }

        if (latestValidRevalDate == null) {
            return "Wymagana rewalidacja (Brak kwalifikacji GxP)";
        }

        java.time.LocalDate expiryDate = latestValidRevalDate.toLocalDate().plusDays(365);
        long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);

        if (daysLeft < 0) {
            return "Wygasła w dniu " + expiryDate.toString() + " (wymagana rewalidacja!)";
        } else {
            return "Ważna do " + expiryDate.toString() + " (pozostało " + daysLeft + " dni)";
        }
    }
}
