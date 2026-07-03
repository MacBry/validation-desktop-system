package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.SpatialStatsResult;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SpatialStatsServiceTest {

    @Autowired
    private SpatialStatsService spatialStatsService;

    @Test
    @DisplayName("should calculate spatial stats and level comparisons for normal data")
    void shouldCalculateSpatialStatsForNormalData() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 7, 12, 0, 0);

        // TOP levels: mean around 5.0
        ThermoMeasurementSeries top1 = createSeries(GridPosition.TOP_FRONT_LEFT, 15, startTime, 5.0, 5.1, 4.9, 5.2, 5.0, 4.8, 5.1, 5.0);
        ThermoMeasurementSeries top2 = createSeries(GridPosition.TOP_BACK_RIGHT, 15, startTime, 5.1, 5.0, 5.2, 4.9, 5.1, 5.0, 4.8, 5.2);

        // BOTTOM levels: mean around 4.0
        ThermoMeasurementSeries bottom1 = createSeries(GridPosition.BOTTOM_FRONT_LEFT, 15, startTime, 4.0, 4.1, 3.9, 4.2, 4.0, 3.8, 4.1, 4.0);
        ThermoMeasurementSeries bottom2 = createSeries(GridPosition.BOTTOM_BACK_RIGHT, 15, startTime, 4.1, 4.0, 4.2, 3.9, 4.1, 4.0, 3.8, 4.2);

        List<ThermoMeasurementSeries> seriesList = Arrays.asList(top1, top2, bottom1, bottom2);

        SpatialStatsResult result = spatialStatsService.calculateSpatialStats(seriesList);

        assertThat(result).isNotNull();
        assertThat(result.hasLevelData()).isTrue();
        assertThat(result.getMeanRangeTop()).isCloseTo(0.2, org.assertj.core.api.Assertions.within(0.1));
        assertThat(result.getMeanRangeBottom()).isCloseTo(0.2, org.assertj.core.api.Assertions.within(0.1));
        assertThat(result.getMeanVerticalGradient()).isCloseTo(1.0, org.assertj.core.api.Assertions.within(0.1));

        // Normal distribution should be true since we generated normally clustered points
        assertThat(result.isNormallyDistributed()).isTrue();
        assertThat(result.getHomogeneityTestName()).contains("Welch");
        assertThat(result.getHomogeneityVerdict()).isNotNull();
        assertThat(result.getWelchAnovaResult()).isNotNull();
        // Since mean TOP (~5.0) vs. mean BOTTOM (~4.0) differs significantly:
        assertThat(result.getHomogeneityPValue()).isLessThan(0.05);
        assertThat(result.getHomogeneityVerdict()).contains("FAIL"); // FAIL means there is a significant difference
        assertThat(result.getGamesHowellResults()).isNotEmpty();
    }

    @Test
    @DisplayName("should calculate spatial stats and level comparisons for non-normal data")
    void shouldCalculateSpatialStatsForNonNormalData() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 7, 12, 0, 0);

        // TOP levels: heavily skewed data to break normality (using 100 points)
        double[] topTemps = new double[100];
        java.util.Arrays.fill(topTemps, 0, 95, 5.0);
        java.util.Arrays.fill(topTemps, 95, 100, 25.0);
        ThermoMeasurementSeries top = createSeries(GridPosition.TOP_FRONT_LEFT, 15, startTime, topTemps);

        // BOTTOM levels: skewed data
        double[] bottomTemps = new double[100];
        java.util.Arrays.fill(bottomTemps, 0, 95, 4.0);
        java.util.Arrays.fill(bottomTemps, 95, 100, 20.0);
        ThermoMeasurementSeries bottom = createSeries(GridPosition.BOTTOM_FRONT_LEFT, 15, startTime, bottomTemps);

        List<ThermoMeasurementSeries> seriesList = Arrays.asList(top, bottom);

        SpatialStatsResult result = spatialStatsService.calculateSpatialStats(seriesList);

        assertThat(result).isNotNull();
        assertThat(result.isNormallyDistributed()).isFalse();
        assertThat(result.getHomogeneityTestName()).contains("Kruskal");
        assertThat(result.getKruskalWallisPValue()).isCloseTo(result.getHomogeneityPValue(), org.assertj.core.api.Assertions.within(1e-9));
        assertThat(result.getDunnResults()).isNotEmpty();
    }

    @Test
    @DisplayName("should handle empty or null collections gracefully")
    void shouldHandleEmptyOrNullCollectionsGracefully() {
        SpatialStatsResult result1 = spatialStatsService.calculateSpatialStats(null);
        assertThat(result1).isNotNull();
        assertThat(result1.hasLevelData()).isFalse();

        SpatialStatsResult result2 = spatialStatsService.calculateSpatialStats(Collections.emptyList());
        assertThat(result2).isNotNull();
        assertThat(result2.hasLevelData()).isFalse();
    }

    private ThermoMeasurementSeries createSeries(GridPosition position, int interval, LocalDateTime startTime, double... temps) {
        ThermoMeasurementSeries s = ThermoMeasurementSeries.builder()
                .gridPosition(position)
                .loggingIntervalMinutes(interval)
                .build();
        for (int i = 0; i < temps.length; i++) {
            s.addMeasurement(ThermoMeasurementPoint.builder()
                    .measurementIndex(i + 1)
                    .timestampLocal(startTime.plusMinutes((long) i * interval))
                    .rawCelsius(temps[i])
                    .build());
        }
        return s;
    }
}
