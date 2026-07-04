package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.config.RegimeDetectionProperties;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.regime.AirflowSourcePreset;
import com.mac.bry.desktop.model.regime.DetectionSource;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fasada detekcji reżimów pracy — główny punkt wejścia dla Fazy 1 (DP-001) i Fazy 2.
 * <p>
 * Orkiestruje detekcję w trzech krokach:
 * <ol>
 *   <li><b>OLS Segmentacja</b> — wyznaczenie segmentów STEADY_STATE / EQUILIBRATION
 *       na podstawie kroczącej regresji liniowej.</li>
 *   <li><b>CUSUM</b> — nakładka: wykrycie trwałych zmian poziomu (SETPOINT_CHANGE),
 *       co reklasyfikuje segmenty EQUILIBRATION z wyraźnym shiftem.</li>
 *   <li><b>ExcursionDetector</b> — nakładka: detekcja i klasyfikacja szpilek
 *       (DEFROST, DOOR_EVENT, EXCURSION) nakładanych na segmenty bazowe.</li>
 * </ol>
 * <p>
 * Cała logika jest za feature flagą {@code regime.detection.enabled}.
 * Gdy flaga jest wyłączona, metoda {@link #detect} zwraca {@link DetectionResult#disabled()}
 * i żadne segmenty nie są zapisywane.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegimeDetectionService {

    private final OlsSegmentor olsSegmentor;
    private final CusumDetector cusumDetector;
    private final ExcursionDetector excursionDetector;
    private final RegimeDetectionProperties props;

    /**
     * Wykonuje detekcję reżimów pracy bez innych kanałów (wersja uproszczona/kompatybilna).
     */
    public DetectionResult detect(ThermoMeasurementSeries series) {
        return detect(series, Map.of());
    }

    /**
     * Wykonuje pełną detekcję reżimów dla pojedynczej serii pomiarowej, uwzględniając inne kanały.
     * <p>
     * Wywołanie jest bezpieczne nawet gdy feature flag jest wyłączony —
     * zwraca {@link DetectionResult#disabled()} bez żadnych obliczeń.
     *
     * @param series      Seria z załadowanymi punktami pomiarowymi.
     * @param allChannels Wszystkie kanały aktywne w danej sesji rewalidacji.
     * @return {@link DetectionResult} z listą segmentów (może być pusta gdy flaga off).
     */
    public DetectionResult detect(
            ThermoMeasurementSeries series,
            Map<GridPosition, ThermoMeasurementSeries> allChannels) {

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

        // ── Krok 3: Detekcja i nakładanie ekskursji (Faza 2) ────────────────
        if (series.getGridPosition() != null) {
            Map<GridPosition, ThermoMeasurementSeries> channels = new HashMap<>(allChannels);
            if (!channels.containsKey(series.getGridPosition())) {
                channels.put(series.getGridPosition(), series);
            }
            // Konfiguracja źródła nawiewu z komory (IMPL-EXC002 §5.1)
            AirflowSourcePreset preset = resolvePreset(series);
            Set<GridPosition> customPositions = resolveCustomPositions(series);

            Map<GridPosition, List<MeasurementSegment>> allExcursions =
                    excursionDetector.detectAll(channels, preset, customPositions);
            List<MeasurementSegment> excursions = allExcursions.getOrDefault(series.getGridPosition(), List.of());
            if (!excursions.isEmpty()) {
                log.debug("ExcursionDetector: wykryto {} szpilek/ekskursji dla pozycji {}", excursions.size(), series.getGridPosition());
                segments = overlayExcursions(segments, excursions, series);
            }
        }

        log.info("RegimeDetectionService: seria {} → {} segmentów: STEADY={}, EQUIL={}, SETPOINT_CHANGE={}, DEFROST={}, DOOR_EVENT={}, EXCURSION={}",
                series.getId(),
                segments.size(),
                countType(segments, SegmentType.STEADY_STATE),
                countType(segments, SegmentType.EQUILIBRATION),
                countType(segments, SegmentType.SETPOINT_CHANGE),
                countType(segments, SegmentType.DEFROST),
                countType(segments, SegmentType.DOOR_EVENT),
                countType(segments, SegmentType.EXCURSION));

        return DetectionResult.of(segments);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Nakłada wyniki detekcji szpilek na listę segmentów bazowych.
     * Wycina okres szpilki z dotychczasowego segmentu.
     */
    private List<MeasurementSegment> overlayExcursions(
            List<MeasurementSegment> baseSegments,
            List<MeasurementSegment> excursions,
            ThermoMeasurementSeries series) {

        if (excursions == null || excursions.isEmpty()) {
            return baseSegments;
        }

        List<MeasurementSegment> sortedBases = new ArrayList<>(baseSegments);
        List<MeasurementSegment> sortedExcursions = new ArrayList<>(excursions);

        sortedBases.sort(Comparator.comparing(MeasurementSegment::getFromTimestamp));
        sortedExcursions.sort(Comparator.comparing(MeasurementSegment::getFromTimestamp));

        List<MeasurementSegment> result = new ArrayList<>();

        for (MeasurementSegment base : sortedBases) {
            List<MeasurementSegment> overlapping = sortedExcursions.stream()
                    .filter(e -> overlaps(base, e))
                    .sorted(Comparator.comparing(MeasurementSegment::getFromTimestamp))
                    .toList();

            if (overlapping.isEmpty()) {
                result.add(base);
                continue;
            }

            LocalDateTime currentStart = base.getFromTimestamp();
            for (MeasurementSegment exc : overlapping) {
                LocalDateTime excStart = exc.getFromTimestamp();
                if (excStart.isAfter(currentStart)) {
                    result.add(buildSegment(series, currentStart, excStart, base.getType(), base.getConfidence(), base.getNote()));
                }

                LocalDateTime overlapStart = excStart.isBefore(base.getFromTimestamp()) ? base.getFromTimestamp() : excStart;
                LocalDateTime overlapEnd = exc.getToTimestamp().isAfter(base.getToTimestamp()) ? base.getToTimestamp() : exc.getToTimestamp();

                if (overlapEnd.isAfter(overlapStart)) {
                    result.add(buildSegment(series, overlapStart, overlapEnd, exc.getType(), exc.getConfidence(), exc.getNote()));
                }

                currentStart = overlapEnd;
            }

            if (base.getToTimestamp().isAfter(currentStart)) {
                result.add(buildSegment(series, currentStart, base.getToTimestamp(), base.getType(), base.getConfidence(), base.getNote()));
            }
        }

        return mergeSameTypeNeighbors(result, series);
    }

    private boolean overlaps(MeasurementSegment s1, MeasurementSegment s2) {
        return !s1.getToTimestamp().isBefore(s2.getFromTimestamp())
                && !s2.getToTimestamp().isBefore(s1.getFromTimestamp());
    }

    private MeasurementSegment buildSegment(
            ThermoMeasurementSeries series,
            LocalDateTime from,
            LocalDateTime to,
            SegmentType type,
            Double confidence,
            String note) {
        return MeasurementSegment.builder()
                .series(series)
                .fromTimestamp(from)
                .toTimestamp(to)
                .type(type)
                .confidence(confidence)
                .source(DetectionSource.ALGORITHM)
                .accepted(true)
                .note(note)
                .build();
    }

    private List<MeasurementSegment> mergeSameTypeNeighbors(
            List<MeasurementSegment> segments,
            ThermoMeasurementSeries series) {

        if (segments.isEmpty()) return segments;

        List<MeasurementSegment> merged = new ArrayList<>();
        MeasurementSegment current = segments.get(0);

        for (int i = 1; i < segments.size(); i++) {
            MeasurementSegment next = segments.get(i);
            if (current.getType() == next.getType()) {
                current = MeasurementSegment.builder()
                        .series(series)
                        .fromTimestamp(current.getFromTimestamp())
                        .toTimestamp(next.getToTimestamp())
                        .type(current.getType())
                        .confidence(Math.min(current.getConfidence() != null ? current.getConfidence() : 1.0,
                                             next.getConfidence() != null ? next.getConfidence() : 1.0))
                        .source(DetectionSource.ALGORITHM)
                        .accepted(true)
                        .note(current.getNote() != null ? current.getNote() : next.getNote())
                        .build();
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

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
            List<CusumDetector.ChangePoint> inside = changePoints.stream()
                    .filter(cp -> {
                        if (cp.getIndex() >= points.size()) return false;
                        var ts = points.get(cp.getIndex()).getTimestampLocal();
                        return seg.contains(ts);
                    })
                    .toList();

            if (inside.isEmpty() || seg.getType() == SegmentType.STEADY_STATE) {
                result.add(seg);
                continue;
            }

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

    /** Preset źródła nawiewu z komory serii; fallback do domyślnego z konfiguracji. */
    private AirflowSourcePreset resolvePreset(ThermoMeasurementSeries series) {
        if (series.getCoolingChamber() != null
                && series.getCoolingChamber().getAirflowSourcePreset() != null) {
            return series.getCoolingChamber().getAirflowSourcePreset();
        }
        return props.getPropagationDefaultPreset();
    }

    /** Pozycje CUSTOM bliskiego pola z komory serii (CSV → EnumSet). */
    private Set<GridPosition> resolveCustomPositions(ThermoMeasurementSeries series) {
        if (series.getCoolingChamber() == null) return null;
        String csv = series.getCoolingChamber().getCustomAirflowPositions();
        if (csv == null || csv.isBlank()) return null;
        Set<GridPosition> positions = EnumSet.noneOf(GridPosition.class);
        for (String s : csv.split(",")) {
            try {
                positions.add(GridPosition.valueOf(s.trim()));
            } catch (IllegalArgumentException e) {
                log.warn("Nieznana pozycja GridPosition w custom_airflow_positions: {}", s.trim());
            }
        }
        return positions;
    }
}
