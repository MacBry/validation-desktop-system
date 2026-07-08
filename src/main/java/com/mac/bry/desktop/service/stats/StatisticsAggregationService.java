package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.CapabilityIndexes;
import com.mac.bry.desktop.dto.stats.DefrostCycle;
import com.mac.bry.desktop.dto.stats.StatsReportDTO;
import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.service.MetrologicalStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Serwis orkiestrujący obliczenia statystyczne dla pojedynczej serii pomiarowej.
 * Integruje moduły: statystyki opisowej, testowania hipotez, SPC oraz analizy szeregów czasowych.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticsAggregationService {

    private final MetrologicalStatsService metrologicalStatsService;
    private final HypothesisTestingService hypothesisTestingService;

    // Progi detekcji defrostu
    private static final double DEFROST_RATE_THRESHOLD = 0.2; // °C/min
    private static final double DEFROST_AMPLITUDE_THRESHOLD = 2.0; // °C

    /**
     * Agreguje wszystkie analizy statystyczne dla podanej serii pomiarowej.
     * 
     * @param series Seria pomiarowa.
     * @return Skonsolidowany raport w formacie DTO.
     */
    public StatsReportDTO aggregate(ThermoMeasurementSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Measurement series cannot be null");
        }

        // 1. Upewnienie się, że statystyki metrologiczne w encji są wyliczone
        if (series.getMinTemperature() == null || series.getMaxTemperature() == null) {
            log.info("Brak wyliczonych statystyk w encji serii ID: {}. Wyliczam...", series.getId());
            metrologicalStatsService.calculateStatistics(series);
        }

        List<ThermoMeasurementPoint> points = series.getMeasurements();
        if (points == null || points.isEmpty()) {
            log.warn("Seria ID: {} nie zawiera punktów pomiarowych. Zwracam pusty raport.", series.getId());
            return StatsReportDTO.builder()
                    .positionName(series.getGridPosition() != null ? series.getGridPosition().name() : "UNKNOWN")
                    .recorderSerialNumber(series.getThermoRecorder() != null ? series.getThermoRecorder().getSerialNumber() : "UNKNOWN")
                    .defrostCycles(new ArrayList<>())
                    .fftSpectrum(new double[0])
                    .build();
        }

        // Wyciągnięcie surowych wartości do tablicy double[]
        double[] values = points.stream()
                .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                .toArray();

        // 2. Testy Hipotez (Normalność rozkładu Jarque-Bera)
        double jbPValue = hypothesisTestingService.performJarqueBera(values);
        double skew = SensorStatsEngine.calculateSkewness(values);
        double kurt = SensorStatsEngine.calculateKurtosis(values);
        double jbStatistic = (values.length / 6.0) * (skew * skew + (kurt * kurt) / 4.0);
        boolean isNormal = jbPValue >= 0.05;

        // 3. Statystyczne Sterowanie Procesem (SPC)
        CoolingChamber chamber = series.getCoolingChamber();
        Double lsl = null;
        Double usl = null;
        Double cp = null;
        Double cpk = null;
        boolean isCapable = false;
        boolean isAcceptable = false;

        if (chamber != null && chamber.getEffectiveMinTempLimit() != null && chamber.getEffectiveMaxTempLimit() != null) {
            lsl = chamber.getEffectiveMinTempLimit();
            usl = chamber.getEffectiveMaxTempLimit();
            CapabilityIndexes capability = SpcEngine.calculateCapability(values, lsl, usl);
            cp = capability.getCp();
            cpk = capability.getCpk();
            isCapable = capability.isHighlyCapable();
            isAcceptable = capability.isAcceptable();
        }

        // 4. Detekcja cykli defrostu
        String sensorName = series.getGridPosition() != null ? series.getGridPosition().name() : "Sensor";
        List<DefrostCycle> defrostCycles = DefrostCycleDetector.detectCycles(
                points, sensorName, DEFROST_RATE_THRESHOLD, DEFROST_AMPLITUDE_THRESHOLD
        );

        double maxDefrostAmplitude = 0.0;
        double totalDefrostDuration = 0.0;
        for (DefrostCycle cycle : defrostCycles) {
            if (cycle.getAmplitude() > maxDefrostAmplitude) {
                maxDefrostAmplitude = cycle.getAmplitude();
            }
            totalDefrostDuration += cycle.getDurationMinutes();
        }
        double avgDefrostDuration = defrostCycles.isEmpty() ? 0.0 : totalDefrostDuration / defrostCycles.size();

        // 5. Analiza Widmowa FFT i dominanty oscylacji
        double[] fftSpectrum = FftCalculator.calculateFftSpectrum(values);
        int maxIdx = -1;
        double maxAmp = -1.0;
        for (int i = 1; i < fftSpectrum.length; i++) {
            if (fftSpectrum[i] > maxAmp) {
                maxAmp = fftSpectrum[i];
                maxIdx = i;
            }
        }

        double dominantPeriodMinutes = 0.0;
        double dominantFrequency = 0.0;
        int m = fftSpectrum.length * 2;
        if (maxIdx > 0 && m > 0) {
            int interval = series.getLoggingIntervalMinutes() != null ? series.getLoggingIntervalMinutes() : 15;
            dominantPeriodMinutes = (double) (m * interval) / maxIdx;
            dominantFrequency = 1.0 / dominantPeriodMinutes;
        }

        // Budowa skonsolidowanego raportu DTO
        return StatsReportDTO.builder()
                .positionName(series.getGridPosition() != null ? series.getGridPosition().name() : "UNKNOWN")
                .recorderSerialNumber(series.getThermoRecorder() != null ? series.getThermoRecorder().getSerialNumber() : "UNKNOWN")
                .minTemp(series.getMinTemperature())
                .maxTemp(series.getMaxTemperature())
                .avgTemp(series.getAvgTemperature())
                .medianTemp(series.getMedianTemperature())
                .stdDev(series.getStdDeviation())
                .variance(series.getVariance())
                .cvPercentage(series.getCvPercentage())
                .percentile5(series.getPercentile5())
                .percentile95(series.getPercentile95())
                .mktTemp(series.getMktTemperature())
                .expandedUncertainty(series.getExpandedUncertainty())
                .jbStatistic(jbStatistic)
                .jbPValue(jbPValue)
                .isNormallyDistributed(isNormal)
                .lsl(lsl)
                .usl(usl)
                .cp(cp)
                .cpk(cpk)
                .isCapable(isCapable)
                .isAcceptable(isAcceptable)
                .defrostCycles(defrostCycles)
                .defrostCyclesCount(defrostCycles.size())
                .maxDefrostAmplitude(maxDefrostAmplitude)
                .avgDefrostDurationMinutes(avgDefrostDuration)
                .dominantFrequency(dominantFrequency)
                .dominantPeriodMinutes(dominantPeriodMinutes)
                .fftSpectrum(fftSpectrum)
                .build();
    }
}
