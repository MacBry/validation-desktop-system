package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.CapabilityIndexes;

public class SpcEngine {

    public static CapabilityIndexes calculateCapability(double[] values, double lsl, double usl) {
        if (values == null || values.length < 2) {
            return new CapabilityIndexes(0.0, 0.0);
        }

        double mean = SensorStatsEngine.calculateMean(values);
        double stdDev = SensorStatsEngine.calculateStdDev(values);

        if (stdDev == 0.0) {
            // Uniknięcie dzielenia przez 0 przy idealnie stałej temperaturze
            return new CapabilityIndexes(99.9, 99.9);
        }

        // Obliczenie wskaźnika Cp (potencjalna zdolność)
        double cp = (usl - lsl) / (6.0 * stdDev);

        // Obliczenie wskaźnika Cpk (rzeczywista zdolność)
        double cpu = (usl - mean) / (3.0 * stdDev);
        double cpl = (mean - lsl) / (3.0 * stdDev);
        double cpk = Math.min(cpu, cpl);

        return new CapabilityIndexes(cp, cpk);
    }
}
