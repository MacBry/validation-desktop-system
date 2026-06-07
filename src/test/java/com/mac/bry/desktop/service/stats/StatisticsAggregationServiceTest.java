package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.StatsReportDTO;
import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.service.MetrologicalStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class StatisticsAggregationServiceTest {

    @Autowired
    private StatisticsAggregationService aggregationService;

    @Autowired
    private MetrologicalStatsService metrologicalStatsService;

    private ThermoMeasurementSeries series;
    private CoolingChamber chamber;
    private ThermoRecorder recorder;

    @BeforeEach
    void setUp() {
        chamber = CoolingChamber.builder()
                .chamberName("Komora Walidacyjna A")
                .chamberType(ChamberType.FRIDGE)
                .minOperatingTemp(2.0)
                .maxOperatingTemp(8.0)
                .build();

        recorder = ThermoRecorder.builder()
                .serialNumber("SN-AGG-111")
                .model("Testo 174T")
                .resolution(new BigDecimal("0.10"))
                .build();

        series = ThermoMeasurementSeries.builder()
                .thermoRecorder(recorder)
                .coolingChamber(chamber)
                .loggingIntervalMinutes(15)
                .gridPosition(RevalidationSession.GridPosition.TOP_FRONT_LEFT)
                .procedureType(GxPProcedureType.PERIODIC_REVALIDATION)
                .build();
    }

    @Test
    @DisplayName("should perform complete statistics aggregation and verify StatsReportDTO fields")
    void shouldPerformAggregationSuccessfully() {
        // Generujemy stabilną temperaturę (24 punkty pomiarowe, ok. 5.0 °C z małą oscylacją)
        // Dodamy jedną "szpilkę" defrostu w środku
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 7, 12, 0, 0);
        for (int i = 0; i < 24; i++) {
            double temp = 5.0 + 0.2 * Math.sin(i * Math.PI / 6.0); // oscylacja co 12 pomiarów (180 min)
            if (i == 12) {
                temp = 9.0; // symulacja piku defrostu (skok o 4.1 °C, rate = 4.1 / 15 = 0.273 °C/min > 0.2)
            }
            series.addMeasurement(ThermoMeasurementPoint.builder()
                    .measurementIndex(i + 1)
                    .timestampLocal(startTime.plusMinutes(i * 15))
                    .rawCelsius(temp)
                    .build());
        }

        // Wyzwalanie agregatora
        StatsReportDTO report = aggregationService.aggregate(series);

        // Asercje na wyjściowe DTO
        assertThat(report).isNotNull();
        assertThat(report.getPositionName()).isEqualTo("TOP_FRONT_LEFT");
        assertThat(report.getRecorderSerialNumber()).isEqualTo("SN-AGG-111");

        // Statystyka opisowa
        assertThat(report.getMinTemp()).isCloseTo(4.8, within(0.1));
        assertThat(report.getMaxTemp()).isEqualTo(9.0);
        assertThat(report.getAvgTemp()).isGreaterThan(5.0);

        // Test normalności (Jarque-Bera)
        assertThat(report.getJbStatistic()).isGreaterThan(0.0);
        // Pik defrostu psuje idealną normalność, ale sprawdzamy czy wartość p-value jest wyznaczona
        assertThat(report.getJbPValue()).isBetween(0.0, 1.0);

        // Wskaźniki SPC (Komora 2.0°C - 8.0°C)
        assertThat(report.getLsl()).isEqualTo(2.0);
        assertThat(report.getUsl()).isEqualTo(8.0);
        assertThat(report.getCp()).isGreaterThan(0.0);
        assertThat(report.getCpk()).isGreaterThan(0.0);

        // Analiza cykli (FFT / Defrost)
        // Przy rateThreshold=0.2 i amplitudeThreshold=2.0, skok z 4.9 na 9.0 (amplituda 4.1) powinien być wykryty
        assertThat(report.getDefrostCyclesCount()).isEqualTo(1);
        assertThat(report.getMaxDefrostAmplitude()).isCloseTo(4.1, within(0.1));

        // Widmo FFT i dominanty
        assertThat(report.getFftSpectrum()).isNotEmpty();
        assertThat(report.getDominantPeriodMinutes()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("should handle null chamber limits gracefully without failing aggregation")
    void shouldHandleNullChamberLimitsGracefully() {
        chamber.setMinOperatingTemp(null);
        chamber.setMaxOperatingTemp(null);

        // Generujemy 10 prostych punktów
        for (int i = 0; i < 10; i++) {
            series.addMeasurement(ThermoMeasurementPoint.builder()
                    .measurementIndex(i + 1)
                    .timestampLocal(LocalDateTime.now().plusMinutes(i * 15))
                    .rawCelsius(5.0)
                    .build());
        }

        StatsReportDTO report = aggregationService.aggregate(series);

        assertThat(report).isNotNull();
        assertThat(report.getLsl()).isNull();
        assertThat(report.getUsl()).isNull();
        assertThat(report.getCp()).isNull();
        assertThat(report.getCpk()).isNull();
        assertThat(report.isCapable()).isFalse();
        assertThat(report.isAcceptable()).isFalse();
    }
}
