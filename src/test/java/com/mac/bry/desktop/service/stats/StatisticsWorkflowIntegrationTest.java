package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.*;
import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.repository.DepartmentRepository;
import com.mac.bry.desktop.service.TestoRevalidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class StatisticsWorkflowIntegrationTest {

    @Autowired
    private TestoRevalidationService revalidationService;

    @Autowired
    private ThermoRecorderRepository thermoRecorderRepository;

    @Autowired
    private CalibrationRepository calibrationRepository;

    @Autowired
    private CalibrationPointRepository calibrationPointRepository;

    @Autowired
    private ThermoMeasurementSeriesRepository seriesRepository;

    @Autowired
    private CoolingDeviceRepository coolingDeviceRepository;

    @Autowired
    private CoolingChamberRepository coolingChamberRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ThermoRecorderModelRepository modelRepository;

    @Test
    @DisplayName("IT-STAT-01: Powinien przeprowadzić pełny przepływ walidacyjny z zapisem i weryfikacją w bazie danych")
    void shouldPerformFullRevalidationWorkflowAndVerifyInDb() {
        // 1. Przygotowanie danych podstawowych w bazie (Ewidencja metrologiczna i GxP)
        Department dept = new Department();
        dept.setName("Dział Zapewnienia Jakości");
        dept.setAbbreviation("QA");
        dept = departmentRepository.save(dept);

        CoolingDevice device = CoolingDevice.builder()
                .inventoryNumber("DEV-STATS-INTEG")
                .name("Szafa chłodnicza szczepionkowa QA")
                .department(dept)
                .build();
        device = coolingDeviceRepository.save(device);

        CoolingChamber chamber = CoolingChamber.builder()
                .chamberName("Komora Główna A")
                .chamberType(ChamberType.FRIDGE)
                .minOperatingTemp(2.0)
                .maxOperatingTemp(8.0)
                .coolingDevice(device)
                .build();
        chamber = coolingChamberRepository.save(chamber);

        ThermoRecorderModel model = modelRepository.findByName("Testo 174T")
                .orElseGet(() -> modelRepository.save(ThermoRecorderModel.builder()
                        .name("Testo 174T")
                        .channelCount(1)
                        .defaultResolution(new BigDecimal("0.100"))
                        .active(true)
                        .build()));

        ThermoRecorder recorder = ThermoRecorder.builder()
                .serialNumber("SN-INTEG-999")
                .model(model)
                .resolution(new BigDecimal("0.10"))
                .status(RecorderStatus.ACTIVE)
                .department(dept)
                .build();
        recorder = thermoRecorderRepository.save(recorder);

        // Świadectwo wzorcowania (k=2, U=0.04)
        Calibration cal = Calibration.builder()
                .thermoRecorder(recorder)
                .calibrationDate(LocalDateTime.now().toLocalDate().minusDays(10))
                .certificateNumber("CERT-INTEG-999")
                .validUntil(LocalDateTime.now().toLocalDate().plusDays(355))
                .build();
        cal = calibrationRepository.save(cal);

        CalibrationPoint calPoint = CalibrationPoint.builder()
                .calibration(cal)
                .temperatureValue(new BigDecimal("5.0"))
                .systematicError(new BigDecimal("0.02"))
                .uncertainty(new BigDecimal("0.04"))
                .build();
        cal.addPoint(calPoint);
        calibrationPointRepository.save(calPoint);
        recorder.addCalibration(cal);

        // 2. Inicjalizacja sesji rewalidacji
        RevalidationSession session = revalidationService.initSession(device, chamber, GxPProcedureType.PERIODIC_REVALIDATION);

        // 3. Stworzenie zbioru walidacyjnego (20 punktów z ustalonymi statystykami referencyjnymi Excel/R)
        double[] validationData = {
                5.0, 5.2, 4.8, 5.0, 5.1, 4.9, 5.0, 5.0, 5.1, 4.9,
                5.0, 5.2, 4.8, 5.0, 5.1, 4.9, 5.0, 5.0, 5.1, 4.9
        };

        LocalDateTime startTime = LocalDateTime.of(2026, 6, 7, 10, 0, 0);

        ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                .thermoRecorder(recorder)
                .coolingChamber(chamber)
                .batteryLevelPercent(99)
                .loggingIntervalMinutes(60)
                .measurementsCount(validationData.length)
                .programmingTimeUtc(startTime.minusHours(1))
                .startDelayMinutes(0)
                .firstMeasurementTimeUtc(startTime)
                .firstMeasurementTimeLocal(startTime)
                .importedAt(LocalDateTime.now())
                .importedBy("Integration Test User")
                .rawHexDump("INTEGRATION_TEST_VALIDATION_DATASET_1")
                .revalidationGroupId(session.getSessionId())
                .gridPosition(RevalidationSession.GridPosition.TOP_FRONT_LEFT)
                .procedureType(session.getProcedureType())
                .build();

        for (int i = 0; i < validationData.length; i++) {
            series.addMeasurement(ThermoMeasurementPoint.builder()
                    .measurementIndex(i + 1)
                    .timestampLocal(startTime.plusMinutes(i * 60))
                    .rawCelsius(validationData[i])
                    .build());
        }

        // Zintegrowanie danych dla narożnika w sesji
        RevalidationSession.PositionData posData = RevalidationSession.PositionData.builder()
                .serialNumber(recorder.getSerialNumber())
                .model(recorder.getModel())
                .recorder(recorder)
                .latestCalibration(cal)
                .series(series)
                .build();
        session.getAssignedPositions().put(RevalidationSession.GridPosition.TOP_FRONT_LEFT, posData);

        // 4. Zapis sesji w bazie danych (powoduje to wyliczenie statystyk przed zapisem)
        revalidationService.saveRevalidationSession(session);

        // 5. Pobranie serii z bazy i weryfikacja dokładności obliczeń (porównanie ze wskaźnikami referencyjnymi)
        List<ThermoMeasurementSeries> persistedSeriesList = seriesRepository.findByCoolingChamberId(chamber.getId());
        assertThat(persistedSeriesList).hasSize(1);
        ThermoMeasurementSeries persistedSeries = persistedSeriesList.get(0);

        // Asercje poprawności wyliczeń (porównanie z referencyjnymi wartościami Excel/R)
        assertThat(persistedSeries.getMinTemperature()).isEqualTo(4.8);
        assertThat(persistedSeries.getMaxTemperature()).isEqualTo(5.2);
        assertThat(persistedSeries.getAvgTemperature()).isEqualTo(5.0);
        assertThat(persistedSeries.getMedianTemperature()).isEqualTo(5.0);
        
        // StdDev
        assertThat(persistedSeries.getStdDeviation()).isCloseTo(0.1124, within(0.005));
        
        // CV% = (StdDev / Avg) * 100
        assertThat(persistedSeries.getCvPercentage()).isCloseTo(2.2478, within(0.005));
        
        // Percentyle
        assertThat(persistedSeries.getPercentile5()).isEqualTo(4.8);
        assertThat(persistedSeries.getPercentile95()).isEqualTo(5.2);

        // Brak naruszeń (limity to 2.0 - 8.0)
        assertThat(persistedSeries.getViolationCount()).isEqualTo(0);
        assertThat(persistedSeries.getTotalTimeOutOfRangeMinutes()).isEqualTo(0);

        // Budżet GUM: uA=0.1124/sqrt(20)=0.0251; uB1=0.04/2=0.02; uB2=0.1/(2*sqrt(3))=0.0289
        // uC = sqrt(uA^2 + uB1^2 + uB2^2) = 0.0432
        // U_expanded = 2 * uC = 0.0864
        assertThat(persistedSeries.getExpandedUncertainty()).isCloseTo(0.0864, within(0.005));

        // Stabilność
        assertThat(persistedSeries.getDriftClassification()).isEqualTo("STABLE");
    }
}
