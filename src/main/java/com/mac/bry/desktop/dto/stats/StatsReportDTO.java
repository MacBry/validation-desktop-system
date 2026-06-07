package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * DTO zawierające skonsolidowane wyniki analiz statystycznych, metrologicznych i SPC
 * dla pojedynczej serii pomiarowej. Klasa jest przystosowana do renderowania w raportach PDF/Word.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsReportDTO {
    private String positionName;
    private String recorderSerialNumber;

    // 1. Statystyka Opisowa i Metrologiczna
    private double minTemp;
    private double maxTemp;
    private double avgTemp;
    private double medianTemp;
    private double stdDev;
    private double variance;
    private double cvPercentage;
    private double percentile5;
    private double percentile95;
    private double mktTemp;
    private double expandedUncertainty;

    // 2. Testy Hipotez (Normalność Rozkładu)
    private double jbStatistic;
    private double jbPValue;
    private boolean isNormallyDistributed; // true jeśli p-value >= 0.05

    // 3. Statystyczne Sterowanie Procesem (SPC)
    private Double lsl; // Lower Specification Limit (dolny limit komory)
    private Double usl; // Upper Specification Limit (górny limit komory)
    private Double cp;  // Potencjalna zdolność procesu
    private Double cpk; // Rzeczywista zdolność procesu
    private boolean isCapable;   // true jeśli cpk >= 1.33 (wysoce stabilny)
    private boolean isAcceptable; // true jeśli cpk >= 1.0 (akceptowalny)

    // 4. Analiza Szeregów Czasowych i Cykli (FFT / Defrost)
    private List<DefrostCycle> defrostCycles;
    private int defrostCyclesCount;
    private double maxDefrostAmplitude;
    private double avgDefrostDurationMinutes;
    private double dominantFrequency;      // cykle na minutę
    private double dominantPeriodMinutes;   // okres dominujący w minutach
    private double[] fftSpectrum;
}
