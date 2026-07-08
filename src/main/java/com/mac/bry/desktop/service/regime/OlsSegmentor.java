package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.config.RegimeDetectionProperties;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.model.regime.DetectionSource;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Segmentator stanu ustalonego oparty na kroczącej regresji liniowej (Rolling OLS).
 * <p>
 * Algorytm (DP-001 §4.4):
 * <ol>
 *   <li>Dla każdego punktu i oblicza nachylenie OLS na oknie [i-W, i].</li>
 *   <li>Oblicza szerokość pasma (max-min) w tym samym oknie.</li>
 *   <li>Punkt i jest kandydatem na STEADY_STATE gdy: |slope| &lt; EPS i band &lt; BAND.</li>
 *   <li>Łączy sąsiednie kandydaty w segmenty metodą run-length encoding.</li>
 *   <li>Segmenty STEADY krótsze niż MIN_STEADY_MINUTES są reklasyfikowane jako EQUILIBRATION.</li>
 * </ol>
 * <p>
 * Determinizm: algorytm jest w pełni deterministyczny — brak losowości.
 * Ten sam wejściowy zestaw danych zawsze produkuje identyczną segmentację.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OlsSegmentor {

    private final RegimeDetectionProperties props;

    /**
     * Segmentuje serię pomiarową na reżimy STEADY_STATE / EQUILIBRATION.
     *
     * @param series Seria z załadowanymi punktami pomiarowymi (posortowanymi rosnąco).
     * @return Lista segmentów posortowanych chronologicznie.
     */
    public List<MeasurementSegment> segment(ThermoMeasurementSeries series) {
        List<ThermoMeasurementPoint> points = series.getMeasurements();
        int window = resolveWindowPoints(series);

        if (points == null || points.size() < window * 2) {
            log.warn("OlsSegmentor: seria {} ma zbyt mało punktów ({}) do segmentacji (wymagane: {})",
                    series.getId(), points == null ? 0 : points.size(), window * 2);
            return List.of();
        }

        int n = points.size();
        boolean[] steadyMask = new boolean[n];

        // Krok 1: klasyfikacja każdego punktu jako STEADY lub nie
        for (int i = window; i < n; i++) {
            List<ThermoMeasurementPoint> win = points.subList(i - window, i);
            double slope = computeOlsSlope(win);
            double band  = computeBandWidth(win);
            steadyMask[i] = Math.abs(slope) < props.getOlsEpsSlope()
                         && band < props.getOlsBandWidth();
        }

        // Krok 2: RLE — łączenie sąsiednich punktów tej samej klasy w segmenty
        List<MeasurementSegment> rawSegments = buildRawSegments(points, steadyMask, series, window);

        // Krok 3: filtrowanie zbyt krótkich STEADY_STATE → reklasyfikacja na EQUILIBRATION
        return mergeShortSteadySegments(rawSegments, series);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Wyznacza nachylenie prostej regresji liniowej (slope β₁) metodą OLS.
     * x = indeks minutowy (0, 1, 2, ...), y = temperatura.
     */
    private double computeOlsSlope(List<ThermoMeasurementPoint> window) {
        int n = window.size();
        if (n < 2) return 0.0;

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = window.get(i).getRawCelsius();
            sumX  += x;
            sumY  += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        double denom = (double) n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 1e-10) return 0.0;
        return (n * sumXY - sumX * sumY) / denom;
    }

    /**
     * Szerokość pasma = max(y) - min(y) w oknie.
     */
    private double computeBandWidth(List<ThermoMeasurementPoint> window) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (ThermoMeasurementPoint p : window) {
            double v = p.getRawCelsius();
            if (v < min) min = v;
            if (v > max) max = v;
        }
        return max - min;
    }

    /**
     * Run-Length Encoding: łączy sąsiednie punkty tej samej klasy w segmenty.
     */
    private List<MeasurementSegment> buildRawSegments(
            List<ThermoMeasurementPoint> points,
            boolean[] steadyMask,
            ThermoMeasurementSeries series,
            int window) {

        List<MeasurementSegment> result = new ArrayList<>();
        int n = points.size();

        // Pierwsze `window` punktów zawsze = EQUILIBRATION (brak danych OLS)
        // Ale musimy uważać na przerwy czasowe w tym początkowym fragmencie
        int i = 0;
        int start = 0;
        while (i < window && i < n) {
            if (i > start) {
                long gap = java.time.temporal.ChronoUnit.MINUTES.between(
                        points.get(i - 1).getTimestampLocal(),
                        points.get(i).getTimestampLocal());
                if (gap > 5) {
                    result.add(buildSegment(series,
                            points.get(start).getTimestampLocal(),
                            points.get(i - 1).getTimestampLocal(),
                            SegmentType.EQUILIBRATION,
                            1.0));
                    start = i;
                }
            }
            i++;
        }
        if (start < window && start < n) {
            result.add(buildSegment(series,
                    points.get(start).getTimestampLocal(),
                    points.get(Math.min(window, n) - 1).getTimestampLocal(),
                    SegmentType.EQUILIBRATION,
                    1.0));
        }

        i = window;
        while (i < n) {
            boolean currentSteady = steadyMask[i];
            start = i;

            while (i < n && steadyMask[i] == currentSteady) {
                if (i > start) {
                    long gap = java.time.temporal.ChronoUnit.MINUTES.between(
                            points.get(i - 1).getTimestampLocal(),
                            points.get(i).getTimestampLocal());
                    if (gap > 5) {
                        break;
                    }
                }
                i++;
            }
            int end = i - 1;

            SegmentType type = currentSteady ? SegmentType.STEADY_STATE : SegmentType.EQUILIBRATION;
            result.add(buildSegment(
                    series,
                    points.get(start).getTimestampLocal(),
                    points.get(end).getTimestampLocal(),
                    type,
                    currentSteady ? 0.85 : 0.90));
        }

        return result;
    }

    /**
     * Reklasyfikuje segmenty STEADY_STATE krótsze niż MIN_STEADY_MINUTES na EQUILIBRATION.
     * Zapobiega klasyfikacji krótkich stabilnych odcinków podczas transientu jako stanu ustalonego.
     */
    private List<MeasurementSegment> mergeShortSteadySegments(
            List<MeasurementSegment> segments,
            ThermoMeasurementSeries series) {

        List<MeasurementSegment> result = new ArrayList<>();
        for (MeasurementSegment seg : segments) {
            if (seg.getType() == SegmentType.STEADY_STATE
                    && seg.durationMinutes() < props.getOlsMinSteadyMinutes()) {
                log.debug("OlsSegmentor: reklasyfikuję krótki STEADY_STATE ({} min < {} min) → EQUILIBRATION",
                        seg.durationMinutes(), props.getOlsMinSteadyMinutes());
                seg.setType(SegmentType.EQUILIBRATION);
                seg.setNote("Reklasyfikowany: zbyt krótki segment STEADY_STATE (" + seg.durationMinutes() + " min)");
            }
            result.add(seg);
        }

        // Łącz sąsiednie EQUILIBRATION po reklasyfikacji
        return mergeSameTypeNeighbors(result, series);
    }

    /**
     * Łączy sąsiadujące segmenty tego samego typu w jeden (po reklasyfikacji).
     */
    private List<MeasurementSegment> mergeSameTypeNeighbors(
            List<MeasurementSegment> segments,
            ThermoMeasurementSeries series) {

        if (segments.isEmpty()) return segments;

        List<MeasurementSegment> merged = new ArrayList<>();
        MeasurementSegment current = segments.get(0);

        for (int i = 1; i < segments.size(); i++) {
            MeasurementSegment next = segments.get(i);
            if (current.getType() == next.getType()) {
                // Połącz — rozszerz current do końca next
                current = buildSegment(series,
                        current.getFromTimestamp(),
                        next.getToTimestamp(),
                        current.getType(),
                        Math.min(current.getConfidence() != null ? current.getConfidence() : 1.0,
                                 next.getConfidence() != null ? next.getConfidence() : 1.0));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private MeasurementSegment buildSegment(
            ThermoMeasurementSeries series,
            LocalDateTime from,
            LocalDateTime to,
            SegmentType type,
            double confidence) {

        return MeasurementSegment.builder()
                .series(series)
                .fromTimestamp(from)
                .toTimestamp(to)
                .type(type)
                .confidence(confidence)
                .source(DetectionSource.ALGORITHM)
                .accepted(true)
                .build();
    }

    private int resolveWindowPoints(ThermoMeasurementSeries series) {
        Integer interval = series.getLoggingIntervalMinutes();
        if (interval == null || interval <= 0) {
            interval = 1;
        }
        return Math.max(3, (int) Math.ceil((double) props.getOlsWindowMinutes() / interval));
    }
}
