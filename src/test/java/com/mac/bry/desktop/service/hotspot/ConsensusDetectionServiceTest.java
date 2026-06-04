package com.mac.bry.desktop.service.hotspot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsensusDetectionServiceTest {

    @Test
    @DisplayName("Should resolve ties using method priority sequence")
    void shouldResolveTiesUsingMethodPriority() {
        // Arrange
        // AbsMaxStrategy votes for SENSOR_2 (8.5 > 8.0)
        // MktStrategy votes for SENSOR_1 (8.0 > 7.5)
        // According to METHOD_PRIORITY: ABS_MAX_HOTSPOT (priority 3) > MKT_HOTSPOT (priority 5).
        // So SENSOR_2 should win.
        List<ExtremeDetectionStrategy> strategies = List.of(
                new AbsMaxStrategy(),
                new MktStrategy()
        );

        ConsensusDetectionService service = new ConsensusDetectionService(strategies);

        List<SensorStats> statsList = List.of(
                new SensorStats("SENSOR_1", 8.0, 2.0, 5.0, 8.0, 2.0, 8.0, 0.0, 0.0),
                new SensorStats("SENSOR_2", 8.5, 2.0, 5.0, 8.5, 2.0, 7.5, 0.0, 0.0)
        );

        // Act
        ConsensusDetectionService.ConsensusReport report = service.detectHotspot(statsList);

        // Assert
        assertThat(report.hasDetection()).isTrue();
        // Since ABS_MAX_HOTSPOT has higher priority than MKT_HOTSPOT, SENSOR_2 wins the tie-breaker
        assertThat(report.consensusSensorId()).isEqualTo("SENSOR_2");
        assertThat(report.consensusStrength()).isEqualTo(0.5); // 1 vote out of 2 strategies
    }

    @Test
    @DisplayName("Should ignore degenerate strategies returning empty verdicts")
    void shouldIgnoreDegenerateStrategies() {
        // Arrange
        // TimeOverLimitStrategy is degenerate when TOL is 0.0.
        // We register AbsMaxStrategy and TimeOverLimitStrategy(true).
        // Since tolHi is 0.0, TimeOverLimitStrategy should be ignored, and only AbsMaxStrategy votes.
        List<ExtremeDetectionStrategy> strategies = List.of(
                new AbsMaxStrategy(),
                new TimeOverLimitStrategy(true)
        );

        ConsensusDetectionService service = new ConsensusDetectionService(strategies);

        List<SensorStats> statsList = List.of(
                new SensorStats("SENSOR_1", 8.0, 2.0, 5.0, 8.0, 2.0, 8.0, 0.0, 0.0),
                new SensorStats("SENSOR_2", 8.5, 2.0, 5.0, 8.5, 2.0, 7.5, 0.0, 0.0)
        );

        // Act
        ConsensusDetectionService.ConsensusReport report = service.detectHotspot(statsList);

        // Assert
        assertThat(report.hasDetection()).isTrue();
        assertThat(report.consensusSensorId()).isEqualTo("SENSOR_2"); // Voted by AbsMaxStrategy
        assertThat(report.consensusStrength()).isEqualTo(1.0); // 1 vote out of 1 active strategy (TOL was ignored)
    }
}
