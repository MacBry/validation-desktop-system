package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.ControlChartData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ControlChartCalculatorTest {

    @Test
    @DisplayName("should calculate correct I-MR limits on stable values")
    void shouldCalculateCorrectImrLimits() {
        double[] values = { 5.0, 5.2, 4.8, 5.1, 4.9 };
        // n = 5
        // individualValues: 5.0, 5.2, 4.8, 5.1, 4.9
        // sum = 25.0 -> mean = 5.0
        // MR:
        // |5.2 - 5.0| = 0.2
        // |4.8 - 5.2| = 0.4
        // |5.1 - 4.8| = 0.3
        // |4.9 - 5.1| = 0.2
        // movingRanges: 0.2, 0.4, 0.3, 0.2
        // sumMR = 1.1 -> meanMR = 1.1 / 4 = 0.275
        // sigmaEst = 0.275 / 1.128 = 0.243794
        // iUcl = 5.0 + 3 * 0.243794 = 5.73138
        // iLcl = 5.0 - 3 * 0.243794 = 4.26861
        // mrUcl = 3.268 * 0.275 = 0.8987

        ControlChartData data = ControlChartCalculator.calculateShewhartLimits(values);

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
    }
}
