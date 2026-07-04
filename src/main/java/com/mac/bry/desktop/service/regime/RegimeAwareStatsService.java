package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.dto.stats.ConditionalStatsDTO;
import com.mac.bry.desktop.model.Calibration;
import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.RunMode;
import com.mac.bry.desktop.model.regime.SegmentType;
import com.mac.bry.desktop.service.MetrologicalStatsService;
import com.mac.bry.desktop.service.regime.verdict.VerdictContext;
import com.mac.bry.desktop.service.regime.verdict.VerdictPolicyRegistry;
import com.mac.bry.desktop.service.regime.verdict.VerdictResult;
import com.mac.bry.desktop.service.stats.SpcEngine;
import com.mac.bry.desktop.config.RegimeDetectionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serwis obliczający statystyki warunkowe — wyłącznie na punktach należących
 * do zaakceptowanych segmentów {@code STEADY_STATE}.
 * <p>
 * Deleguje matematykę do {@link MetrologicalStatsService} bez duplikacji logiki.
 * Buduje syntetyczną serię z przefiltrowanych punktów i przepuszcza ją przez
 * istniejący pipeline obliczeń.
 * <p>
 * Zgodnie z BR-02 (BA-DP001): metryki kwalifikacyjne wyłącznie na STEADY_STATE.
 * Zgodnie z NFR-01: w pełni deterministyczny.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegimeAwareStatsService {

    private final MetrologicalStatsService metrologicalStatsService;
    private final RegimeDetectionProperties props;
    private final VerdictPolicyRegistry verdictPolicyRegistry;

    /**
     * Oblicza statystyki warunkowe dla serii pomiarowej na podstawie wykrytych segmentów.
     *
     * @param series    Oryginalna seria pomiarowa.
     * @param segments  Wykryte segmenty (wynik {@link RegimeDetectionService#detect}).
     * @param runMode   Tryb runu zadeklarowany przez operatora.
     * @param lsl       Dolny limit specyfikacji [°C] — może być null.
     * @param usl       Górny limit specyfikacji [°C] — może być null.
     * @return {@link ConditionalStatsDTO} wypełniony wartościami z fazy STEADY_STATE.
     */
    public ConditionalStatsDTO calculateConditionalStatistics(
            ThermoMeasurementSeries series,
            List<MeasurementSegment> segments,
            RunMode runMode,
            Double lsl,
            Double usl) {

        String posLabel = resolvePositionLabel(series);
        String sn = series.getThermoRecorder() != null
                ? series.getThermoRecorder().getSerialNumber() : "?";

        // 1. Filtruj punkty do zaakceptowanych STEADY_STATE
        List<ThermoMeasurementPoint> steadyPoints = filterToSteadyState(
                series.getMeasurements(), segments);

        int totalPoints = series.getMeasurements() != null ? series.getMeasurements().size() : 0;

        if (steadyPoints.size() < props.getMinSteadyPointsForStats()) {
            log.warn("RegimeAwareStatsService [{}]: zbyt mało punktów STEADY_STATE: {} (min: {})",
                    posLabel, steadyPoints.size(), props.getMinSteadyPointsForStats());
            double coverage = totalPoints > 0
                    ? (double) steadyPoints.size() / totalPoints * 100.0 : 0.0;
            VerdictResult verdict = evaluateVerdict(runMode, false, coverage, null, null, segments);
            return ConditionalStatsDTO.builder()
                    .positionLabel(posLabel)
                    .recorderSerialNumber(sn)
                    .runMode(runMode)
                    .hasSteadyStateData(false)
                    .steadyStatePointCount(steadyPoints.size())
                    .steadyStateCoveragePercent(coverage)
                    .verdictNote(verdict.formattedNote())
                    .verdictStatus(verdict.status())
                    .build();
        }

        // 2. Oblicz podstawowe statystyki na surowych wartościach STEADY_STATE
        double[] rawSteady = steadyPoints.stream()
                .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                .toArray();

        double minSteady    = Arrays.stream(rawSteady).min().orElse(Double.NaN);
        double maxSteady    = Arrays.stream(rawSteady).max().orElse(Double.NaN);
        double avgSteady    = Arrays.stream(rawSteady).average().orElse(Double.NaN);
        double medianSteady = computeMedian(rawSteady);
        double stdDevSteady = computeStdDev(rawSteady, avgSteady);

        // 3. Zdolność procesu (Cp, Cpk) na STEADY_STATE — jeśli mamy limity
        Double cpSteady = null, cpkSteady = null;
        Boolean cpkPass = null, stdDevPass = null;

        if (lsl != null && usl != null) {
            var capability = SpcEngine.calculateCapability(rawSteady, lsl, usl);
            cpSteady  = capability.getCp();
            cpkSteady = capability.getCpk();
            cpkPass   = cpkSteady >= 1.0;
        }

        // 4. Limit std dev WHO
        double stdDevLimit = resolveWhoStdDevLimit(series);
        if (stdDevLimit > 0) {
            stdDevPass = stdDevSteady <= stdDevLimit;
        }

        // 5. Niepewność rozszerzona — obliczona przez MetrologicalStatsService
        // Budujemy syntetyczną serię tylko do wyznaczenia U (bez zapisu do bazy)
        Double expandedU = computeExpandedUncertainty(series, steadyPoints);

        // 6. Pokrycie
        double coveragePct = (double) steadyPoints.size() / totalPoints * 100.0;

        // 7. Werdykt przez politykę zależną od trybu runu (DP-001 §4.5, Strategy)
        VerdictResult verdict = evaluateVerdict(runMode, true, coveragePct, cpkPass, stdDevPass, segments);

        log.debug("RegimeAwareStatsService [{}]: steady={} pkt ({:.1f}%), "
                        + "avg={:.2f}°C, std={:.3f}°C, Cpk={}",
                posLabel, steadyPoints.size(), coveragePct,
                avgSteady, stdDevSteady, cpkSteady != null ? String.format("%.3f", cpkSteady) : "N/A");

        return ConditionalStatsDTO.builder()
                .positionLabel(posLabel)
                .recorderSerialNumber(sn)
                .runMode(runMode)
                .hasSteadyStateData(true)
                .steadyStatePointCount(steadyPoints.size())
                .steadyStateCoveragePercent(coveragePct)
                .minSteady(minSteady)
                .maxSteady(maxSteady)
                .avgSteady(avgSteady)
                .medianSteady(medianSteady)
                .stdDevSteady(stdDevSteady)
                .cpSteady(cpSteady)
                .cpkSteady(cpkSteady)
                .expandedUncertaintySteady(expandedU)
                .cpkPassSteady(cpkPass)
                .stdDevPassSteady(stdDevPass)
                .verdictNote(verdict.formattedNote())
                .verdictStatus(verdict.status())
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Filtruje punkty serii do tych należących do zaakceptowanych segmentów STEADY_STATE.
     */
    private List<ThermoMeasurementPoint> filterToSteadyState(
            List<ThermoMeasurementPoint> allPoints,
            List<MeasurementSegment> segments) {

        if (allPoints == null || allPoints.isEmpty()) return List.of();

        List<MeasurementSegment> steadyAccepted = segments.stream()
                .filter(s -> s.getType() == SegmentType.STEADY_STATE && s.isAccepted())
                .collect(Collectors.toList());

        if (steadyAccepted.isEmpty()) return List.of();

        return allPoints.stream()
                .filter(p -> steadyAccepted.stream().anyMatch(s -> s.contains(p.getTimestampLocal())))
                .collect(Collectors.toList());
    }

    private double computeMedian(double[] values) {
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int n = sorted.length;
        return (n % 2 == 0)
                ? (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
                : sorted[n / 2];
    }

    private double computeStdDev(double[] values, double mean) {
        if (values.length < 2) return 0.0;
        double sumSq = 0.0;
        for (double v : values) {
            double d = v - mean;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / (values.length - 1));
    }

    /**
     * Limit std dev WHO zależny od typu komory:
     * Lodówka/Chłodnia: ≤ 0,3°C | Zamrażarka/Mroźnia: ≤ 1,0°C.
     * Zwraca 0.0 gdy typ nieznany (brak limitu).
     */
    private double resolveWhoStdDevLimit(ThermoMeasurementSeries series) {
        if (series.getCoolingChamber() == null) return 0.0;
        return switch (series.getCoolingChamber().getChamberType()) {
            case FRIDGE  -> 0.3;
            case FREEZER -> 1.0;
            default      -> 0.0;
        };
    }

    /**
     * Oblicza niepewność rozszerzoną U przez MetrologicalStatsService.
     * Tworzy syntetyczną serię z przefiltrowanych punktów, przepuszcza przez service,
     * odczytuje wynik i odrzuca obiekt (bez zapisu do bazy).
     */
    private Double computeExpandedUncertainty(
            ThermoMeasurementSeries original,
            List<ThermoMeasurementPoint> steadyPoints) {
        try {
            // Syntetyczna kopia tylko do obliczeń U
            ThermoMeasurementSeries synthetic = ThermoMeasurementSeries.builder()
                    .thermoRecorder(original.getThermoRecorder())
                    .coolingChamber(original.getCoolingChamber())
                    .batteryLevelPercent(original.getBatteryLevelPercent() != null
                            ? original.getBatteryLevelPercent() : 100)
                    .loggingIntervalMinutes(original.getLoggingIntervalMinutes() != null
                            ? original.getLoggingIntervalMinutes() : 1)
                    .measurementsCount(steadyPoints.size())
                    .programmingTimeUtc(original.getProgrammingTimeUtc())
                    .startDelayMinutes(original.getStartDelayMinutes() != null
                            ? original.getStartDelayMinutes() : 0)
                    .firstMeasurementTimeUtc(original.getFirstMeasurementTimeUtc())
                    .firstMeasurementTimeLocal(original.getFirstMeasurementTimeLocal())
                    .importedAt(original.getImportedAt())
                    .importedBy(original.getImportedBy())
                    .rawHexDump("")
                    .measurements(new ArrayList<>(steadyPoints))
                    .build();

            // Wywołanie istniejącego pipeline bez kalibracji (U bez korekcji)
            metrologicalStatsService.calculateStatistics(synthetic);
            return synthetic.getExpandedUncertainty();
        } catch (Exception e) {
            log.warn("RegimeAwareStatsService: błąd obliczania U dla STEADY_STATE: {}", e.getMessage());
            return null;
        }
    }

    private String resolvePositionLabel(ThermoMeasurementSeries series) {
        return (series.getGridPosition() != null)
                ? series.getGridPosition().getLabel()
                : "Nieznana pozycja";
    }

    /** Deleguje ocenę do polityki werdyktu wybranej wg trybu runu (Strategy, DP-001 §4.5). */
    private VerdictResult evaluateVerdict(RunMode runMode, boolean hasSteadyStateData,
                                          double coveragePct, Boolean cpkPass, Boolean stdDevPass,
                                          List<MeasurementSegment> segments) {
        VerdictContext ctx = VerdictContext.builder()
                .hasSteadyStateData(hasSteadyStateData)
                .steadyStateCoveragePercent(coveragePct)
                .cpkPass(cpkPass)
                .stdDevPass(stdDevPass)
                .segments(segments)
                .build();
        return verdictPolicyRegistry.forMode(runMode).evaluate(ctx);
    }
}
