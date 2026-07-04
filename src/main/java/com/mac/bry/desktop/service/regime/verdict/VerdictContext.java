package com.mac.bry.desktop.service.regime.verdict;

import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kontekst oceny werdyktu — komplet danych, na których polityka {@link VerdictPolicy}
 * podejmuje decyzję (DP-001 §4.5). Niemutowalny, deterministyczny.
 */
@Value
@Builder
public class VerdictContext {

    /** Czy jest wystarczająco danych STEADY_STATE do obliczeń kwalifikacyjnych. */
    boolean hasSteadyStateData;

    /** Odsetek przebiegu sklasyfikowany jako STEADY_STATE [%]. */
    double steadyStateCoveragePercent;

    /** Czy Cpk(STEADY) ≥ 1,0; null gdy brak limitów LSL/USL. */
    Boolean cpkPass;

    /** Czy std dev(STEADY) w limicie WHO; null gdy limit nieznany. */
    Boolean stdDevPass;

    /** Pełna lista segmentów po detekcji (STEADY_STATE, EQUILIBRATION, zdarzenia). */
    List<MeasurementSegment> segments;

    /**
     * Liczba niezidentyfikowanych ekskursji (EXCURSION) nakładających się na
     * kopertę czasową fazy ustalonej [min(from), max(to)] zaakceptowanych
     * segmentów STEADY_STATE. DEFROST i DOOR_EVENT są zdarzeniami wyjaśnionymi
     * i nie liczą się jako naruszenie kwalifikacji.
     */
    public long countExcursionsInSteadyEnvelope() {
        if (segments == null || segments.isEmpty()) return 0;

        LocalDateTime envelopeFrom = null;
        LocalDateTime envelopeTo = null;
        for (MeasurementSegment s : segments) {
            if (s.getType() == SegmentType.STEADY_STATE && s.isAccepted()) {
                if (envelopeFrom == null || s.getFromTimestamp().isBefore(envelopeFrom)) {
                    envelopeFrom = s.getFromTimestamp();
                }
                if (envelopeTo == null || s.getToTimestamp().isAfter(envelopeTo)) {
                    envelopeTo = s.getToTimestamp();
                }
            }
        }
        if (envelopeFrom == null) return 0;

        final LocalDateTime from = envelopeFrom;
        final LocalDateTime to = envelopeTo;
        return segments.stream()
                .filter(s -> s.getType() == SegmentType.EXCURSION)
                .filter(s -> !s.getToTimestamp().isBefore(from) && !s.getFromTimestamp().isAfter(to))
                .count();
    }
}
