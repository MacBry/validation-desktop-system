package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.ControlChartData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NelsonRulesDetectorTest {

    @Test
    @DisplayName("should detect Rule 1 (point outside UCL/LCL) on Individual chart")
    void shouldDetectRule1XBar() {
        // CL = 5.0, UCL = 6.0, LCL = 4.0
        ControlChartData data = new ControlChartData(
                Arrays.asList(5.0, 5.2, 6.3, 4.9, 5.1), // 6.3 is outside UCL
                5.0, 6.0, 4.0,
                Arrays.asList(0.2, 0.2, 0.2, 0.2, 0.2),
                0.2, 0.5, 0.0
        );

        List<NelsonRulesDetector.Violation> violations = NelsonRulesDetector.detectXBarViolations(data);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).getRuleNumber()).isEqualTo(1);
        assertThat(violations.get(0).getSubgroupIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("should detect Rule 2 (9 points in a row on same side of CL) on Individual chart")
    void shouldDetectRule2XBar() {
        // CL = 5.0, 9 points above CL
        ControlChartData data = new ControlChartData(
                Arrays.asList(5.1, 5.2, 5.1, 5.3, 5.2, 5.4, 5.1, 5.3, 5.2, 4.9),
                5.0, 6.5, 3.5,
                Arrays.asList(0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2),
                0.2, 0.5, 0.0
        );

        List<NelsonRulesDetector.Violation> violations = NelsonRulesDetector.detectXBarViolations(data);
        // The 9th point (index 9, i.e. 1-indexed) is the first that completes the 9-consecutive-above sequence
        assertThat(violations).anyMatch(v -> v.getRuleNumber() == 2 && v.getSubgroupIndex() == 9);
    }

    @Test
    @DisplayName("should detect Rule 3 (6 points in a row steadily increasing or decreasing)")
    void shouldDetectRule3XBar() {
        ControlChartData data = new ControlChartData(
                Arrays.asList(4.8, 4.9, 5.0, 5.1, 5.2, 5.3), // 6 increasing points
                5.0, 6.5, 3.5,
                Arrays.asList(0.2, 0.2, 0.2, 0.2, 0.2, 0.2),
                0.2, 0.5, 0.0
        );

        List<NelsonRulesDetector.Violation> violations = NelsonRulesDetector.detectXBarViolations(data);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).getRuleNumber()).isEqualTo(3);
        assertThat(violations.get(0).getSubgroupIndex()).isEqualTo(6);
    }

    @Test
    @DisplayName("should detect Rule 4 (14 points in a row alternating up and down)")
    void shouldDetectRule4XBar() {
        ControlChartData data = new ControlChartData(
                Arrays.asList(5.0, 4.8, 5.2, 4.8, 5.2, 4.8, 5.2, 4.8, 5.2, 4.8, 5.2, 4.8, 5.2, 4.8), // 14 alternating points
                5.0, 6.5, 3.5,
                Arrays.asList(0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2),
                0.2, 0.5, 0.0
        );

        List<NelsonRulesDetector.Violation> violations = NelsonRulesDetector.detectXBarViolations(data);
        assertThat(violations).anyMatch(v -> v.getRuleNumber() == 4 && v.getSubgroupIndex() == 14);
    }

    @Test
    @DisplayName("should detect Rule 1 on MR chart (point outside UCL/LCL)")
    void shouldDetectRule1S() {
        // UCL = 0.5, LCL = 0.0
        ControlChartData data = new ControlChartData(
                Arrays.asList(5.0, 5.0, 5.0),
                5.0, 6.0, 4.0,
                Arrays.asList(0.2, 0.6, 0.1), // 0.6 is outside UCL
                0.2, 0.5, 0.0
        );

        List<NelsonRulesDetector.Violation> violations = NelsonRulesDetector.detectSViolations(data);
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).getRuleNumber()).isEqualTo(1);
        assertThat(violations.get(0).getSubgroupIndex()).isEqualTo(3); // MR_2 (from point 2 and 3) has index 3
        assertThat(violations.get(0).isSChart()).isTrue();
    }
}
