package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(MockitoExtension.class)
class MetrologicalStatsServiceTest {

    @InjectMocks
    private MetrologicalStatsService statsService;

    private CoolingChamber createMockChamber() {
        return CoolingChamber.builder()
                .chamberName("Komora Testowa")
                .minOperatingTemp(2.0)
                .maxOperatingTemp(8.0)
                .materialType(MaterialType.builder()
                        .name("Leki szczepionkowe")
                        .activationEnergy(new BigDecimal("83.14")) // 83.14 kJ/mol
                        .build())
                .build();
    }

    private ThermoRecorder createMockRecorder() {
        ThermoRecorder recorder = ThermoRecorder.builder()
                .serialNumber("SN-TEST-123")
                .model(ThermoRecorderModel.builder().name("Testo 174T").build())
                .resolution(new BigDecimal("0.100"))
                .build();

        Calibration calibration = Calibration.builder()
                .thermoRecorder(recorder)
                .calibrationDate(LocalDateTime.now().toLocalDate().minusMonths(1))
                .certificateNumber("CERT-12345")
                .validUntil(LocalDateTime.now().toLocalDate().plusMonths(11))
                .build();

        calibration.addPoint(CalibrationPoint.builder()
                .temperatureValue(new BigDecimal("5.0"))
                .systematicError(new BigDecimal("0.02"))
                .uncertainty(new BigDecimal("0.04"))
                .build());

        recorder.addCalibration(calibration);
        return recorder;
    }

    @Nested
    @DisplayName("Metrological Statistics Core Calculations")
    class MetrologicalCoreCalculations {

        @Test
        @DisplayName("UT-MET-01: Stable Series — correct descriptive stats, MKT, GUM and STABLE drift classification")
        void shouldCalculateDescriptiveStatsStableSeries() {
            CoolingChamber chamber = createMockChamber();
            ThermoRecorder recorder = createMockRecorder();

            ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                    .coolingChamber(chamber)
                    .thermoRecorder(recorder)
                    .build();

            LocalDateTime startTime = LocalDateTime.of(2026, 5, 18, 0, 0);

            // Generowanie 40 stabilnych punktów wokół 5.0 °C
            for (int i = 1; i <= 40; i++) {
                double val = 5.0 + (i % 2 == 0 ? 0.2 : -0.2); // np. 5.2, 4.8, 5.2, 4.8...
                series.addMeasurement(ThermoMeasurementPoint.builder()
                        .measurementIndex(i)
                        .timestampLocal(startTime.plusMinutes(i * 15)) // co 15 minut
                        .rawCelsius(val)
                        .build());
            }

            statsService.calculateStatistics(series);

            // Asercje statystyk opisowych
            assertThat(series.getMinTemperature()).isEqualTo(4.8);
            assertThat(series.getMaxTemperature()).isEqualTo(5.2);
            assertThat(series.getAvgTemperature()).isEqualTo(5.0);
            assertThat(series.getMedianTemperature()).isEqualTo(5.0);
            assertThat(series.getStdDeviation()).isCloseTo(0.2, within(0.01));
            assertThat(series.getCvPercentage()).isCloseTo(4.0, within(0.1)); // 0.2 / 5.0 * 100% = 4%

            // MKT Arrheniusa
            assertThat(series.getMktTemperature()).isCloseTo(5.0, within(0.1));

            // Klasyfikacja stabilności (Dryft zerowy)
            assertThat(series.getDriftClassification()).isEqualTo("STABLE");
            assertThat(series.getSpikeCount()).isEqualTo(0);

            // Brak naruszeń limitów komory (limit 2.0 - 8.0)
            assertThat(series.getViolationCount()).isEqualTo(0);
            assertThat(series.getTotalTimeOutOfRangeMinutes()).isEqualTo(0L);

            // Budżet niepewności GUM (k=2)
            // uA = stdDev / sqrt(40) = 0.2 / 6.32 = 0.0316
            // uB1 = certUncertainty / 2 = 0.04 / 2 = 0.02
            // uB2 = resolution / (2 * sqrt(3)) = 0.1 / 3.464 = 0.0289
            // uC = sqrt(uA^2 + uB1^2 + uB2^2) = sqrt(0.0010 + 0.0004 + 0.0008) = sqrt(0.0022) = 0.047
            // U_expanded = 2 * uC = 0.094
            assertThat(series.getExpandedUncertainty()).isCloseTo(0.094, within(0.01));
        }

        @Test
        @DisplayName("UT-MET-02: Transient Spike Series — correct spike detection and SPIKE drift classification")
        void shouldDetectSpikesAndMarkAsSpike() {
            CoolingChamber chamber = createMockChamber();
            ThermoRecorder recorder = createMockRecorder();

            ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                    .coolingChamber(chamber)
                    .thermoRecorder(recorder)
                    .build();

            LocalDateTime startTime = LocalDateTime.of(2026, 5, 18, 0, 0);

            // Generowanie 40 punktów, w tym jedna pojedyncza ogromna szpilka o indeksie 20 (np. otwarcie drzwi komory)
            for (int i = 1; i <= 40; i++) {
                double val = 5.0;
                if (i == 20) {
                    val = 9.8; // Ogromna szpilka przekraczająca limit komory (maxLimit = 8.0)
                }
                series.addMeasurement(ThermoMeasurementPoint.builder()
                        .measurementIndex(i)
                        .timestampLocal(startTime.plusMinutes(i * 15))
                        .rawCelsius(val)
                        .build());
            }

            statsService.calculateStatistics(series);

            // Powinien wykryć szpilkę metodą MAD
            assertThat(series.getSpikeCount()).isGreaterThan(0);
            assertThat(series.getDriftClassification()).isEqualTo("SPIKE");

            // Powinno odnotować naruszenie limitu komory
            assertThat(series.getViolationCount()).isEqualTo(1);
            assertThat(series.getTotalTimeOutOfRangeMinutes()).isGreaterThan(0L);
        }

        @Test
        @DisplayName("UT-MET-03: Steadily Drifting Series — correct DRIFT classification")
        void shouldDetectLinearDrift() {
            CoolingChamber chamber = createMockChamber();
            ThermoRecorder recorder = createMockRecorder();

            ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                    .coolingChamber(chamber)
                    .thermoRecorder(recorder)
                    .build();

            LocalDateTime startTime = LocalDateTime.of(2026, 5, 18, 0, 0);

            // Generowanie 40 punktów o powolnym, jednostajnym wzroście temperatury (np. ucieczka czynnika chłodniczego)
            // Wzrost o 0.05 °C na punkt (15 minut) -> dryft dobowy dryft24h = 0.05 * 4 * 24 = 4.8 °C/24h >> 0.1 °C/24h
            for (int i = 1; i <= 40; i++) {
                double val = 4.0 + (i * 0.05);
                series.addMeasurement(ThermoMeasurementPoint.builder()
                        .measurementIndex(i)
                        .timestampLocal(startTime.plusMinutes(i * 15))
                        .rawCelsius(val)
                        .build());
            }

            statsService.calculateStatistics(series);

            // Powinno wykryć trend liniowy dryftu i sklasyfikować jako DRIFT
            assertThat(series.getDriftClassification()).isEqualTo("DRIFT");
            assertThat(series.getTrendCoefficient()).isGreaterThan(0.0);
        }
    }
}
