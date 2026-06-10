package com.mac.bry.desktop.service.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SensorStatsEngineTest {

    @Test
    @DisplayName("should compute correct mean, median, variance, stddev, and rsd")
    void shouldComputeCorrectBasicStats() {
        double[] data = { 2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0 };

        double mean = SensorStatsEngine.calculateMean(data);
        double median = SensorStatsEngine.calculateMedian(data);
        double variance = SensorStatsEngine.calculateVariance(data);
        double stdDev = SensorStatsEngine.calculateStdDev(data);
        double rsd = SensorStatsEngine.calculateRsd(data);

        // N = 8
        // sum = 40
        // mean = 5.0
        // sorted: 2, 4, 4, 4, 5, 5, 7, 9 -> median = (4 + 5) / 2 = 4.5
        // diffs from mean (5.0): -3, -1, -1, -1, 0, 0, 2, 4
        // squared diffs: 9, 1, 1, 1, 0, 0, 4, 16 -> sum = 32
        // variance (unbiased) = 32 / (8-1) = 32/7 = 4.5714
        // stddev = sqrt(32/7) = 2.138
        // rsd = (2.138 / 5.0) * 100 = 42.76%

        assertThat(mean).isEqualTo(5.0);
        assertThat(median).isEqualTo(4.5);
        assertThat(variance).isCloseTo(4.5714, within(0.001));
        assertThat(stdDev).isCloseTo(2.138, within(0.001));
        assertThat(rsd).isCloseTo(42.76, within(0.01));
    }

    @Test
    @DisplayName("should compute correct skewness and kurtosis")
    void shouldComputeCorrectSkewnessAndKurtosis() {
        // Simple dataset with known skewness/kurtosis or standard normal-like sample
        double[] data = { 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0 };
        
        double skew = SensorStatsEngine.calculateSkewness(data);
        double kurt = SensorStatsEngine.calculateKurtosis(data);

        // For symmetric uniform-like data, skewness should be 0.0
        assertThat(skew).isCloseTo(0.0, within(0.0001));
        // excess kurtosis for uniform-like distribution is negative
        assertThat(kurt).isLessThan(0.0);
    }

    @Test
    @DisplayName("should compute exact skewness and kurtosis for GxP reference dataset")
    void shouldComputeExactSkewnessAndKurtosisForGxpReference() {
        double[] skewedData = { 2.0, 2.0, 2.0, 3.0, 8.0 };
        double skew = SensorStatsEngine.calculateSkewness(skewedData);
        double kurt = SensorStatsEngine.calculateKurtosis(skewedData);

        // Expected Fisher-Pearson values:
        // g1 ≈ 2.0922
        // g2 ≈ 4.4161
        assertThat(skew).isCloseTo(2.092235, within(0.0001));
        assertThat(kurt).isCloseTo(4.416090, within(0.0001));
    }

    @Test
    @DisplayName("should return NaN for undefined skewness and kurtosis on small samples")
    void shouldReturnNaNForSmallSamples() {
        double[] size1 = { 1.0 };
        double[] size2 = { 1.0, 2.0 };
        double[] size3 = { 1.0, 2.0, 3.0 };

        assertThat(SensorStatsEngine.calculateSkewness(size1)).isNaN();
        assertThat(SensorStatsEngine.calculateSkewness(size2)).isNaN();
        assertThat(SensorStatsEngine.calculateSkewness(size3)).isNotNaN(); // N = 3 is defined

        assertThat(SensorStatsEngine.calculateKurtosis(size1)).isNaN();
        assertThat(SensorStatsEngine.calculateKurtosis(size2)).isNaN();
        assertThat(SensorStatsEngine.calculateKurtosis(size3)).isNaN();
        
        double[] size4 = { 1.0, 2.0, 3.0, 4.0 };
        assertThat(SensorStatsEngine.calculateKurtosis(size4)).isNotNaN(); // N = 4 is defined
    }
}
