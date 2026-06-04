package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TestoSimulationServiceTest {

    private final TestoSimulationService simulationService = new TestoSimulationService();

    @Test
    @DisplayName("should generate correct number of simulation points")
    void shouldGenerateCorrectNumberOfPoints() {
        int count = 50;
        int intervalMinutes = 5;

        List<ThermoMeasurementPoint> points = simulationService.generateSimulationPoints(count, intervalMinutes);

        assertThat(points).hasSize(count);
        for (int i = 0; i < count; i++) {
            ThermoMeasurementPoint point = points.get(i);
            assertThat(point.getMeasurementIndex()).isEqualTo(i + 1);
            assertThat(point.getRawCelsius()).isBetween(2.0, 8.0); // should be around 2.8 - 6.8
            if (i > 0) {
                long durationMinutes = java.time.Duration.between(
                        points.get(i - 1).getTimestampLocal(), 
                        point.getTimestampLocal()
                ).toMinutes();
                assertThat(durationMinutes).isEqualTo(intervalMinutes);
            }
        }
    }
}
