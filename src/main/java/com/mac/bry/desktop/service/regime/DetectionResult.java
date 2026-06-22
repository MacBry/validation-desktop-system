package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * Wynik detekcji reżimów dla pojedynczej serii pomiarowej.
 * Immutable value object — bezpieczny do przekazywania między serwisami.
 */
@Getter
public class DetectionResult {

    /** Wykryte segmenty posortowane chronologicznie. */
    private final List<MeasurementSegment> segments;

    /** Czy feature flag był aktywny — false oznacza pominięcie detekcji. */
    private final boolean detectionEnabled;

    private DetectionResult(List<MeasurementSegment> segments, boolean detectionEnabled) {
        this.segments = Collections.unmodifiableList(segments);
        this.detectionEnabled = detectionEnabled;
    }

    /** Normalny wynik z wykrytymi segmentami. */
    public static DetectionResult of(List<MeasurementSegment> segments) {
        return new DetectionResult(segments, true);
    }

    /** Wynik gdy feature flag jest wyłączony — pusta lista segmentów. */
    public static DetectionResult disabled() {
        return new DetectionResult(Collections.emptyList(), false);
    }

    /** Pomocnicze — zlicza segmenty danego typu (zaakceptowane). */
    public long countAccepted(SegmentType type) {
        return segments.stream()
                .filter(s -> s.getType() == type && s.isAccepted())
                .count();
    }

    /** Czy seria zawiera co najmniej jeden zaakceptowany segment STEADY_STATE. */
    public boolean hasSteadyState() {
        return segments.stream()
                .anyMatch(s -> s.getType() == SegmentType.STEADY_STATE && s.isAccepted());
    }
}
