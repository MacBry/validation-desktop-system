package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.ControlChartData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ControlChartCalculatorTest {

    @Test
    @DisplayName("should calculate correct Shewhart and I-MR limits on stable values")
    void shouldCalculateCorrectImrAndShewhartLimits() {
        double[] values = { 5.0, 5.2, 4.8, 5.1, 4.9 };

        ControlChartData data = ControlChartCalculator.calculateShewhartLimits(values);

        // 1. Walidacja Klasycznej Karty Shewharta (X-bar & S)
        assertThat(data.getSubgroupMeans()).containsExactly(5.0);
        // stdDev dla próbki: sqrt(0.1 / 4) = sqrt(0.025) = 0.15811388
        double expectedS = Math.sqrt(0.025);
        assertThat(data.getSubgroupStdDevs().get(0)).isCloseTo(expectedS, within(1e-5));
        assertThat(data.getXBarCentralLine()).isCloseTo(5.0, within(1e-5));
        assertThat(data.getXBarUcl()).isCloseTo(5.0 + 1.427 * expectedS, within(1e-5));
        assertThat(data.getXBarLcl()).isCloseTo(5.0 - 1.427 * expectedS, within(1e-5));
        assertThat(data.getSUcl()).isCloseTo(2.089 * expectedS, within(1e-5));
        assertThat(data.getSLcl()).isEqualTo(0.0);

        // 2. Walidacja Karty I-MR
        assertThat(data.getIndividualValues()).containsExactly(5.0, 5.2, 4.8, 5.1, 4.9);
        assertThat(data.getICentralLine()).isCloseTo(5.0, within(1e-5));
        
        assertThat(data.getMovingRanges()).hasSize(4);
        assertThat(data.getMovingRanges().get(0)).isCloseTo(0.2, within(1e-5));
        assertThat(data.getMovingRanges().get(1)).isCloseTo(0.4, within(1e-5));
        assertThat(data.getMovingRanges().get(2)).isCloseTo(0.3, within(1e-5));
        assertThat(data.getMovingRanges().get(3)).isCloseTo(0.2, within(1e-5));
        assertThat(data.getMrCentralLine()).isCloseTo(0.275, within(1e-5));

        double expectedSigma = 0.275 / 1.128;
        assertThat(data.getIUcl()).isCloseTo(5.0 + 3.0 * expectedSigma, within(1e-5));
        assertThat(data.getILcl()).isCloseTo(5.0 - 3.0 * expectedSigma, within(1e-5));
        assertThat(data.getMrUcl()).isCloseTo(3.268 * 0.275, within(1e-5));
        assertThat(data.getMrLcl()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("should handle empty or small datasets gracefully")
    void shouldHandleEdgeCases() {
        double[] values = { 5.0 };
        ControlChartData data = ControlChartCalculator.calculateShewhartLimits(values);
        assertThat(data.getIndividualValues()).isEmpty();
        assertThat(data.getICentralLine()).isEqualTo(0.0);
        assertThat(data.getSubgroupMeans()).isEmpty();
        assertThat(data.getXBarCentralLine()).isEqualTo(0.0);
    }
}
