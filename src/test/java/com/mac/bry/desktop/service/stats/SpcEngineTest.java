package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.CapabilityIndexes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SpcEngineTest {

    @Test
    @DisplayName("should compute Cp and Cpk correctly for normal centered process")
    void shouldComputeCapabilityCorrectlyForCenteredProcess() {
        // data centered at 5.0, range [2.0, 8.0]
        double[] values = { 3.0, 4.0, 5.0, 6.0, 7.0, 5.0, 5.0, 5.0 }; // variance = 10/7 = 1.428 -> stdDev ~ 1.195
        
        double lsl = 2.0;
        double usl = 8.0;

        CapabilityIndexes indexes = SpcEngine.calculateCapability(values, lsl, usl);

        // mean is 5.0, stdDev is ~1.195
        // Cp = (8 - 2) / (6 * 1.195) = 6 / 7.171 ~ 0.837
        // Cpk = min( (8-5)/(3*1.195), (5-2)/(3*1.195) ) ~ 0.837
        assertThat(indexes.getCp()).isCloseTo(0.837, within(0.01));
        assertThat(indexes.getCpk()).isCloseTo(0.837, within(0.01));
    }

    @Test
    @DisplayName("should compute lower Cpk for shifted process")
    void shouldComputeLowerCpkForShiftedProcess() {
        // shifted towards USL (8.0)
        double[] values = { 6.0, 6.5, 7.0, 7.5, 7.0, 7.0, 7.0 };
        double lsl = 2.0;
        double usl = 8.0;

        CapabilityIndexes indexes = SpcEngine.calculateCapability(values, lsl, usl);
        
        // Cp should be higher than Cpk because mean is shifted towards USL
        assertThat(indexes.getCp()).isGreaterThan(indexes.getCpk());
    }
}
