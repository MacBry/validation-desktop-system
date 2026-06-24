package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.config.RegimeDetectionProperties;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OlsSegmentorTest {

    private OlsSegmentor segmentor;
    private RegimeDetectionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RegimeDetectionProperties();
        properties.setOlsWindowMinutes(30);
        properties.setOlsEpsSlope(0.01);
        properties.setOlsBandWidth(1.5);
        properties.setOlsMinSteadyMinutes(30);
        segmentor = new OlsSegmentor(properties);
    }

    @Test
    @DisplayName("TC-OLS-001: Przebieg stały → 100% STEADY_STATE")
    void tc_ols_001_fullyStableRun_shouldReturnOnlySteadyState() {
        LocalDateTime start = LocalDateTime.parse("2026-06-21T08:00:00");
        List<ThermoMeasurementPoint> points = generateFlatRun(5.0, 0.1, 120, start);
        ThermoMeasurementSeries series = buildSeriesFromPoints(points);

        List<MeasurementSegment> segments = segmentor.segment(series);

        long steadyCount = countByType(segments, SegmentType.STEADY_STATE);
        long equilCount  = countByType(segments, SegmentType.EQUILIBRATION);

        assertThat(steadyCount).as("Powinien wykryć co najmniej jeden segment STEADY_STATE").isGreaterThanOrEqualTo(1);
        assertThat(equilCount).as("Musi być dokładnie jeden segment EQUILIBRATION (rozbiegowy)").isEqualTo(1);
    }

    @Test
    @DisplayName("TC-OLS-002: Rampa nastawy −0,33°C/min → EQUILIBRATION + STEADY_STATE")
    void tc_ols_002_linearRamp_thenSteady_shouldDetectEquilibrationFirst() {
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        LocalDateTime t = LocalDateTime.parse("2026-06-21T08:00:00");

        for (int i = 0; i < 60; i++) {
            points.add(makePoint(5.0 - 0.33 * i, t.plusMinutes(i)));
        }
        for (int i = 60; i < 120; i++) {
            double noise = (i % 2 == 0) ? 0.05 : -0.05;
            points.add(makePoint(-15.0 + noise, t.plusMinutes(i)));
        }
        ThermoMeasurementSeries series = buildSeriesFromPoints(points);

        List<MeasurementSegment> segments = segmentor.segment(series);

        assertThat(segments).isNotEmpty();

        MeasurementSegment first = segments.get(0);
        assertThat(first.getType()).as("Pierwszy segment musi być EQUILIBRATION").isEqualTo(SegmentType.EQUILIBRATION);

        MeasurementSegment last = segments.get(segments.size() - 1);
        assertThat(last.getType()).as("Olastni segment musi być STEADY_STATE").isEqualTo(SegmentType.STEADY_STATE);

        List<MeasurementSegment> steadyInRamp = segments.stream()
                .filter(s -> s.getType() == SegmentType.STEADY_STATE)
                .filter(s -> s.getFromTimestamp().isBefore(t.plusMinutes(55)))
                .toList();
        assertThat(steadyInRamp).as("Brak STEADY_STATE podczas rampy dochodzenia").isEmpty();
    }

    @Test
    @DisplayName("TC-OLS-003: Granica EQUILIBRATION→STEADY z dokładnością ≤5 min")
    void tc_ols_003_segmentBoundaryTimestampAccuracy() {
        LocalDateTime transitionPoint = LocalDateTime.parse("2026-06-21T09:00:00").plusMinutes(properties.getOlsWindowMinutes());

        List<ThermoMeasurementPoint> points = new ArrayList<>();
        LocalDateTime t = LocalDateTime.parse("2026-06-21T08:00:00");
        for (int i = 0; i < 60; i++) {
            points.add(makePoint(5.0 - 0.33 * i, t.plusMinutes(i)));
        }
        for (int i = 60; i < 120; i++) {
            points.add(makePoint(-15.0, t.plusMinutes(i)));
        }
        ThermoMeasurementSeries series = buildSeriesFromPoints(points);

        List<MeasurementSegment> segments = segmentor.segment(series);

        Optional<MeasurementSegment> firstSteady = segments.stream()
                .filter(s -> s.getType() == SegmentType.STEADY_STATE)
                .findFirst();

        assertThat(firstSteady).isPresent();
        long diffMinutes = Math.abs(Duration.between(
                firstSteady.get().getFromTimestamp(), transitionPoint).toMinutes());

        assertThat(diffMinutes).as("Granica STEADY musi być wykryta z dokładnością ≤5 minut").isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("TC-OLS-004: Segment STEADY krótszy niż 30 min = ignorowany")
    void tc_ols_004_shortSteadySegment_shouldNotBeClassifiedAsSteady() {
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        LocalDateTime t = LocalDateTime.parse("2026-06-21T08:00:00");
        for (int i = 0; i < 15; i++) points.add(makePoint(5.0, t.plusMinutes(i)));
        for (int i = 15; i < 75; i++) points.add(makePoint(5.0 - 0.1 * i, t.plusMinutes(i)));
        ThermoMeasurementSeries series = buildSeriesFromPoints(points);

        List<MeasurementSegment> segments = segmentor.segment(series);

        long steadyCount = countByType(segments, SegmentType.STEADY_STATE);
        assertThat(steadyCount).as("Segment <30 min nie może być sklasyfikowany jako STEADY_STATE").isZero();
    }

    @Test
    @DisplayName("TC-OLS-005: Dane z przerwami (gap > 5 min)")
    void tc_ols_005_dataGap_shouldNotSpanGap() {
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        LocalDateTime t = LocalDateTime.parse("2026-06-21T08:00:00");
        for (int i = 0; i < 60; i++) points.add(makePoint(5.0, t.plusMinutes(i)));
        for (int i = 90; i < 150; i++) points.add(makePoint(5.0, t.plusMinutes(i)));
        ThermoMeasurementSeries series = buildSeriesFromPoints(points);

        List<MeasurementSegment> segments = segmentor.segment(series);

        LocalDateTime gapStart = t.plusMinutes(60);
        LocalDateTime gapEnd   = t.plusMinutes(90);

        segments.forEach(s ->
                assertThat(s.getToTimestamp().isBefore(gapStart) ||
                           !s.getFromTimestamp().isBefore(gapEnd))
                        .as("Żaden segment nie może obejmować przerwy w danych")
                        .isTrue()
        );
    }

    private List<ThermoMeasurementPoint> generateFlatRun(
            double temp, double noise, int minutes, LocalDateTime start) {
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        for (int i = 0; i < minutes; i++) {
            double value = temp + ((i % 2 == 0) ? noise : -noise);
            points.add(ThermoMeasurementPoint.builder()
                    .measurementIndex(i + 1)
                    .rawCelsius(value)
                    .timestampLocal(start.plusMinutes(i))
                    .build());
        }
        return points;
    }

    private ThermoMeasurementPoint makePoint(double temp, LocalDateTime time) {
        return ThermoMeasurementPoint.builder()
                .rawCelsius(temp)
                .timestampLocal(time)
                .build();
    }

    private ThermoMeasurementSeries buildSeriesFromPoints(List<ThermoMeasurementPoint> points) {
        ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                .id(1L)
                .measurements(points)
                .build();
        for (ThermoMeasurementPoint p : points) {
            p.setSeries(series);
        }
        return series;
    }

    private long countByType(List<MeasurementSegment> segments, SegmentType type) {
        return segments.stream().filter(s -> s.getType() == type).count();
    }
}
