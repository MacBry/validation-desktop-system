package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.config.RegimeDetectionProperties;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.model.regime.DetectionSource;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Fasada detekcji reżimów pracy — główny punkt wejścia dla Fazy 1 (DP-001).
 * <p>
 * Orkiestruje detekcję w dwóch krokach:
 * <ol>
 *   <li><b>OLS Segmentacja</b> — wyznaczenie segmentów STEADY_STATE / EQUILIBRATION
 *       na podstawie kroczącej regresji liniowej.</li>
 *   <li><b>CUSUM</b> — nakładka: wykrycie trwałych zmian poziomu (SETPOINT_CHANGE),
 *       co reklasyfikuje segmenty EQUILIBRATION z wyraźnym shiftem.</li>
 * </ol>
 * <p>
 * Cała logika jest za feature flagą {@code regime.detection.enabled}.
 * Gdy flaga jest wyłączona, metoda {@link #detect} zwraca {@link DetectionResult#disabled()}
 * i żadne segmenty nie są zapisywane.
 * <p>
 * Faza 2 (ExcursionDetector) zostanie dodana jako kolejny krok w tej fasadzie.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegimeDetectionService {

    private final OlsSegmentor olsSegmentor;
    private final CusumDetector cusumDetector;
    private final RegimeDetectionProperties props;

    /**
     * Wykonuje pełną detekcję reżimów dla pojedynczej serii pomiarowej.
     * <p>
     * Wywołanie jest bezpieczne nawet gdy feature flag jest wyłączony —
     * zwraca {@link DetectionResult#disabled()} bez żadnych obliczeń.
     *
     * @param series Seria z załadowanymi punktami pomiarowymi.
     * @return {@link DetectionResult} z listą segmentów (może być pusta gdy flaga off).
     */
    public DetectionResult detect(ThermoMeasurementSeries series) {
        if (!props.isEnabled()) {
            log.trace("RegimeDetectionService: feature flag wyłączony — pomijam detekcję dla serii {}",
                    series.getId());
            return DetectionResult.disabled();
        }

        if (series.getMeasurements() == null || series.getMeasurements().isEmpty()) {
            log.warn("RegimeDetectionService: seria {} nie ma punktów pomiarowych", series.getId());
            return DetectionResult.of(List.of());
        }

        log.debug("RegimeDetectionService: rozpoczynam detekcję dla serii {} ({} punktów)",
                series.getId(), series.getMeasurements().size());

        // ── Krok 1: OLS Segmentacja (STEADY_STATE / EQUILIBRATION) ─────────
        List<MeasurementSegment> segments = new ArrayList<>(olsSegmentor.segment(series));
        log.debug("OLS: {} segmentów po segmentacji bazowej", segments.size());

        // ── Krok 2: CUSUM — wykrycie SETPOINT_CHANGE ────────────────────────
        double[] rawValues = extractRawValues(series.getMeasurements());
        double sigma = estimateBaselineSigma(rawValues, props.getCusumBaselinePoints());

        List<CusumDetector.ChangePoint> changePoints = cusumDetector.detect(rawValues, sigma);

        if (!changePoints.isEmpty()) {
            log.debug("CUSUM: {} change point(s) wykrytych", changePoints.size());
            segments = applyChangePoints(segments, changePoints, series);
        }

        log.info("RegimeDetectionService: seria {} → {} segmentów: STEADY={}, EQUIL={}, SETPOINT_CHANGE={}",
                series.getId(),
                segments.size(),
                countType(segments, SegmentType.STEADY_STATE),
                countType(segments, SegmentType.EQUILIBRATION),
                countType(segments, SegmentType.SETPOINT_CHANGE));

        return DetectionResult.of(segments);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Nakłada wyniki CUSUM na listę segmentów OLS.
     * Każdy change point dzieli istniejący segment i reklasyfikuje część
     * EQUILIBRATION → SETPOINT_CHANGE (jeśli jest to zmiana trwała).
     */
    private List<MeasurementSegment> applyChangePoints(
            List<MeasurementSegment> segments,
            List<CusumDetector.ChangePoint> changePoints,
            ThermoMeasurementSeries series) {

        List<ThermoMeasurementPoint> points = series.getMeasurements();
        List<MeasurementSegment> result = new ArrayList<>();

        for (MeasurementSegment seg : segments) {
            // Znajdź change points wewnątrz tego segmentu
            List<CusumDetector.ChangePoint> inside = changePoints.stream()
                    .filter(cp -> {
                        if (cp.getIndex() >= points.size()) return false;
                        var ts = points.get(cp.getIndex()).getTimestampLocal();
                        return seg.contains(ts);
                    })
                    .toList();

            if (inside.isEmpty() || seg.getType() == SegmentType.STEADY_STATE) {
                // Brak change pointów w tym segmencie, lub to STEADY — nie dotykamy
                result.add(seg);
                continue;
            }

            // EQUILIBRATION z change pointem → oznacz jako SETPOINT_CHANGE
            MeasurementSegment retyped = MeasurementSegment.builder()
                    .series(series)
                    .fromTimestamp(seg.getFromTimestamp())
                    .toTimestamp(seg.getToTimestamp())
                    .type(SegmentType.SETPOINT_CHANGE)
                    .confidence(0.80)
                    .source(DetectionSource.ALGORITHM)
                    .accepted(true)
                    .note("CUSUM wykrył trwałą zmianę poziomu (" + inside.size()
                            + " change point(s)): " + inside.stream()
                            .map(cp -> cp.getDirection().name())
                            .reduce((a, b) -> a + ", " + b).orElse(""))
                    .build();
            result.add(retyped);
        }

        return result;
    }

    private double[] extractRawValues(List<ThermoMeasurementPoint> points) {
        double[] values = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            values[i] = points.get(i).getRawCelsius();
        }
        return values;
    }

    private double estimateBaselineSigma(double[] values, int baselinePoints) {
        int n = Math.min(baselinePoints, values.length);
        if (n < 2) return 1.0;
        double mu = 0.0;
        for (int i = 0; i < n; i++) mu += values[i];
        mu /= n;
        double sumSq = 0.0;
        for (int i = 0; i < n; i++) {
            double d = values[i] - mu;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / (n - 1));
    }

    private long countType(List<MeasurementSegment> segments, SegmentType type) {
        return segments.stream().filter(s -> s.getType() == type).count();
    }
}
