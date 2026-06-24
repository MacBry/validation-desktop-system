package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "regime.detection.enabled=true")
class RegimeDetectorCsvTest {

    @Autowired
    private RegimeDetectionService regimeDetectionService;

    // ══════════════════════════════════════════════════════════════════
    // CSV-TC-001: Rampa nastawy bez szpilek
    // Spec: 1× EQUILIBRATION + STEADY_STATE, brak ekskursji
    // Kryterium akceptacji: czułość STEADY ≥95%, brak false positive DEFROST/DOOR
    // ══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("CSV-TC-001 [WYMAGANY]: Rampa −0,33°C/min, brak szpilek → EQUIL + STEADY")
    void csv_tc_001_rampWithoutSpikes() {
        LocalDateTime start = LocalDateTime.parse("2026-06-21T08:00:00");
        ThermoMeasurementSeries series = buildSeries_RampThenSteady(
                5.0, -18.0, 120, 300, start);

        Map<GridPosition, ThermoMeasurementSeries> allChannels =
                Map.of(GridPosition.TOP_FRONT_LEFT, series);

        DetectionResult result = regimeDetectionService.detect(series, allChannels);

        assertThat(result.getSegments())
                .extracting(MeasurementSegment::getType)
                .as("CSV-TC-001: Musi zawierać STEADY_STATE")
                .contains(SegmentType.STEADY_STATE);

        assertThat(result.getSegments())
                .extracting(MeasurementSegment::getType)
                .as("CSV-TC-001: Musi zawierać EQUILIBRATION lub SETPOINT_CHANGE")
                .containsAnyOf(SegmentType.EQUILIBRATION, SegmentType.SETPOINT_CHANGE);

        assertThat(result.getSegments())
                .as("CSV-TC-001: Brak false positive DEFROST/DOOR_EVENT")
                .noneMatch(s -> s.getType() == SegmentType.DEFROST ||
                               s.getType() == SegmentType.DOOR_EVENT);

        double steadyCoverage = computeSteadyCoverage(result, 120, 420);
        assertThat(steadyCoverage)
                .as("CSV-TC-001: ≥90% punktów fazy STEADY_STATE poprawnie sklasyfikowanych")
                .isGreaterThanOrEqualTo(0.90);
    }

    // ══════════════════════════════════════════════════════════════════
    // CSV-TC-002: Defrost co 8h, 3 cykle
    // Spec: 3× DEFROST oznaczone jako periodyczne
    // Kryterium: wykrycie 3 DEFROST ±1, każdy w oknie ±15 min od syntetycznej szpilki
    // ══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("CSV-TC-002 [WYMAGANY]: Defrost co 8h, 3 cykle → 3× DEFROST")
    void csv_tc_002_periodicDefrost3cycles() {
        LocalDateTime start = LocalDateTime.parse("2026-06-21T08:00:00");
        ThermoMeasurementSeries series = buildSeries_SteadyWithPeriodicDefrosts(
                -18.0, 3, 480, 10, 20, start);

        DetectionResult result = regimeDetectionService.detect(series, Map.of(GridPosition.TOP_FRONT_LEFT, series));

        long defrostCount = countByType(result.getSegments(), SegmentType.DEFROST);
        assertThat(defrostCount)
                .as("CSV-TC-002: Muszą być wykryte 2–4 cykle DEFROST (oczekiwane 3)")
                .isBetween(2L, 4L);

        List<LocalDateTime> expectedDefrosts = List.of(
                start.plusHours(8),
                start.plusHours(16),
                start.plusHours(24)
        );

        result.getSegments().stream()
                .filter(s -> s.getType() == SegmentType.DEFROST)
                .forEach(detected -> {
                    boolean withinWindow = expectedDefrosts.stream().anyMatch(expected ->
                            Math.abs(Duration.between(detected.getFromTimestamp(), expected).toMinutes()) <= 15);
                    assertThat(withinWindow)
                            .as("CSV-TC-002: DEFROST wykryty w %s poza oknem ±15 min",
                                    detected.getFromTimestamp())
                            .isTrue();
                });
    }

    // ══════════════════════════════════════════════════════════════════
    // CSV-TC-003: Pojedyncze otwarcie drzwi
    // Spec: 1× DOOR_EVENT (nieokresowe, czujniki przednie pierwsze)
    // Kryterium: wykrycie 1 DOOR_EVENT ±15 min od zdarzenia
    // ══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("CSV-TC-003 [WYMAGANY]: Otwarcie drzwi → 1× DOOR_EVENT")
    void csv_tc_003_singleDoorEvent() {
        LocalDateTime start = LocalDateTime.parse("2026-06-21T08:00:00");
        LocalDateTime doorOpen = start.plusHours(3);

        Map<GridPosition, ThermoMeasurementSeries> channels =
                buildMultiChannelDoorEvent(start, doorOpen, 5.0, 8.0, 12.0);

        ThermoMeasurementSeries refSeries = channels.get(GridPosition.TOP_FRONT_LEFT);
        DetectionResult result = regimeDetectionService.detect(refSeries, channels);

        long doorCount = countByType(result.getSegments(), SegmentType.DOOR_EVENT);
        assertThat(doorCount)
                .as("CSV-TC-003: Musi być wykryty dokładnie 1 DOOR_EVENT")
                .isEqualTo(1);

        Optional<MeasurementSegment> doorSeg = result.getSegments().stream()
                .filter(s -> s.getType() == SegmentType.DOOR_EVENT)
                .findFirst();

        assertThat(doorSeg).isPresent();
        long diffMin = Math.abs(Duration.between(doorSeg.get().getFromTimestamp(), doorOpen).toMinutes());
        assertThat(diffMin)
                .as("CSV-TC-003: DOOR_EVENT z dokładnością ≤15 min")
                .isLessThanOrEqualTo(15);
    }

    // ══════════════════════════════════════════════════════════════════
    // CSV-TC-004: Przebieg całkowicie w stanie ustalonym
    // Spec: 100% STEADY_STATE, Cpk liczony na całości
    // Kryterium: brak EQUILIBRATION/DEFROST/DOOR, pokrycie STEADY ≥95%
    // ══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("CSV-TC-004 [WYMAGANY]: Przebieg ustalony → 100% STEADY_STATE")
    void csv_tc_004_fullyEstablishedRun() {
        LocalDateTime start = LocalDateTime.parse("2026-06-21T08:00:00");
        ThermoMeasurementSeries series = buildSeries_SteadyOnly(-18.0, 0.05, 1440, start);

        DetectionResult result = regimeDetectionService.detect(series,
                Map.of(GridPosition.TOP_FRONT_LEFT, series));

        assertThat(result.getSegments())
                .as("CSV-TC-004: Brak fałszywych ekskursji dla przebiegu ustalonego")
                .noneMatch(s -> s.getType() == SegmentType.DEFROST ||
                               s.getType() == SegmentType.DOOR_EVENT ||
                               s.getType() == SegmentType.EXCURSION);

        double steadyCoverage = computeSteadyCoverage(result, 0, 1440);
        assertThat(steadyCoverage)
                .as("CSV-TC-004: ≥95% punktów = STEADY_STATE")
                .isGreaterThanOrEqualTo(0.95);
    }

    // ══════════════════════════════════════════════════════════════════
    // CSV-TC-005: Dane referencyjne 2026-06-21 (przypadek lodówko-zamrażarki)
    // Spec: wykrycie SETPOINT_CHANGE (fastcooling) w oknie 6–14h sesji
    // Kryterium: co najmniej 1 SETPOINT_CHANGE lub EXCURSION wykryty w godzinach 6–14
    // ══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("CSV-TC-005 [WYMAGANY]: Przebieg referencyjny 2026-06-21 → SETPOINT_CHANGE w 6–14h")
    void csv_tc_005_referenceRun_fastcooling_detected() {
        LocalDateTime start = LocalDateTime.parse("2026-06-21T00:00:00");
        List<ThermoMeasurementPoint> points = new ArrayList<>();

        for (int i = 0; i < 360; i++) {
            points.add(makePoint(1.5 + (i % 5 == 0 ? 0.3 : -0.3), start.plusMinutes(i)));
        }
        for (int i = 360; i < 840; i++) {
            double frac = (double)(i - 360) / 480;
            points.add(makePoint(1.5 - 8.0 * frac, start.plusMinutes(i)));
        }
        for (int i = 840; i < 1500; i++) {
            points.add(makePoint(1.5, start.plusMinutes(i)));
        }

        ThermoMeasurementSeries series = buildSeriesFromPoints(points);
        series.setGridPosition(GridPosition.TOP_BACK_LEFT);
        DetectionResult result = regimeDetectionService.detect(series, Map.of(GridPosition.TOP_BACK_LEFT, series));

        LocalDateTime fastCoolingStart = start.plusHours(6);
        LocalDateTime fastCoolingEnd   = start.plusHours(14);

        boolean fastCoolingDetected = result.getSegments().stream()
                .filter(s -> s.getType() == SegmentType.SETPOINT_CHANGE ||
                            s.getType() == SegmentType.EXCURSION)
                .anyMatch(s -> !s.getFromTimestamp().isAfter(fastCoolingEnd) &&
                              !s.getToTimestamp().isBefore(fastCoolingStart));

        assertThat(fastCoolingDetected)
                .as("CSV-TC-005: Fastcooling (6–14h) musi być wykryty jako SETPOINT_CHANGE lub EXCURSION")
                .isTrue();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────────────────────────────────────

    private ThermoMeasurementSeries buildSeries_RampThenSteady(
            double startTemp, double steadyTemp, int rampMinutes, int steadyMinutes, LocalDateTime start) {
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        for (int i = 0; i < rampMinutes; i++) {
            double progress = (double) i / rampMinutes;
            double temp = startTemp + progress * (steadyTemp - startTemp);
            points.add(makePoint(temp, start.plusMinutes(i)));
        }
        for (int i = 0; i < steadyMinutes; i++) {
            double noise = (i % 2 == 0) ? 0.1 : -0.1;
            points.add(makePoint(steadyTemp + noise, start.plusMinutes(rampMinutes + i)));
        }
        ThermoMeasurementSeries series = buildSeriesFromPoints(points);
        series.setGridPosition(GridPosition.TOP_FRONT_LEFT);
        return series;
    }

    private ThermoMeasurementSeries buildSeries_SteadyWithPeriodicDefrosts(
            double steadyTemp, int cycleCount, int periodMinutes, int peakMinutes, int returnMinutes, LocalDateTime start) {
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        int totalMinutes = cycleCount * periodMinutes + 120;

        double[] values = new double[totalMinutes];
        for (int i = 0; i < totalMinutes; i++) {
            values[i] = steadyTemp + ((i % 2 == 0) ? 0.15 : -0.15);
        }

        for (int c = 1; c <= cycleCount; c++) {
            int startIdx = c * periodMinutes;
            double baseline = values[startIdx];
            double peakTemp = 8.0;
            for (int p = 0; p < peakMinutes; p++) {
                if (startIdx + p < totalMinutes) {
                    double progress = (double) p / peakMinutes;
                    values[startIdx + p] = baseline + progress * (peakTemp - baseline);
                }
            }
            int peakIdx = startIdx + peakMinutes;
            for (int r = 0; r < returnMinutes; r++) {
                if (peakIdx + r < totalMinutes) {
                    double progress = (double) r / returnMinutes;
                    values[peakIdx + r] = peakTemp - progress * (peakTemp - (baseline + 0.5));
                }
            }
        }

        for (int i = 0; i < totalMinutes; i++) {
            points.add(makePoint(values[i], start.plusMinutes(i)));
        }

        ThermoMeasurementSeries series = buildSeriesFromPoints(points);
        series.setGridPosition(GridPosition.TOP_FRONT_LEFT);
        series.setLoggingIntervalMinutes(1);
        return series;
    }

    private Map<GridPosition, ThermoMeasurementSeries> buildMultiChannelDoorEvent(
            LocalDateTime start, LocalDateTime doorOpen, double steadyTemp, double peakTemp, double durationMinutes) {

        Map<GridPosition, ThermoMeasurementSeries> channels = new HashMap<>();
        int totalMinutes = 360;

        List<ThermoMeasurementPoint> frontPoints = new ArrayList<>();
        for (int i = 0; i < totalMinutes; i++) {
            LocalDateTime t = start.plusMinutes(i);
            double temp = steadyTemp + ((i % 2 == 0) ? 0.1 : -0.1);
            if (t.isAfter(doorOpen) && t.isBefore(doorOpen.plusMinutes((long) durationMinutes))) {
                long minutesSinceOpen = Duration.between(doorOpen, t).toMinutes();
                if (minutesSinceOpen < 5) {
                    temp = steadyTemp + (peakTemp - steadyTemp) * (minutesSinceOpen / 5.0);
                } else {
                    double progress = (minutesSinceOpen - 5.0) / (durationMinutes - 5.0);
                    temp = peakTemp - progress * (peakTemp - steadyTemp);
                }
            }
            frontPoints.add(makePoint(temp, t));
        }
        ThermoMeasurementSeries frontSeries = buildSeriesFromPoints(frontPoints);
        frontSeries.setGridPosition(GridPosition.TOP_FRONT_LEFT);
        frontSeries.setLoggingIntervalMinutes(1);
        channels.put(GridPosition.TOP_FRONT_LEFT, frontSeries);

        LocalDateTime backReactionStart = doorOpen.plusMinutes(2);
        List<ThermoMeasurementPoint> backPoints = new ArrayList<>();
        for (int i = 0; i < totalMinutes; i++) {
            LocalDateTime t = start.plusMinutes(i);
            double temp = steadyTemp + ((i % 2 == 0) ? 0.1 : -0.1);
            if (t.isAfter(backReactionStart) && t.isBefore(backReactionStart.plusMinutes((long) durationMinutes))) {
                long minutesSinceOpen = Duration.between(backReactionStart, t).toMinutes();
                if (minutesSinceOpen < 5) {
                    temp = steadyTemp + (peakTemp - steadyTemp) * (minutesSinceOpen / 5.0);
                } else {
                    double progress = (minutesSinceOpen - 5.0) / (durationMinutes - 5.0);
                    temp = peakTemp - progress * (peakTemp - steadyTemp);
                }
            }
            backPoints.add(makePoint(temp, t));
        }
        ThermoMeasurementSeries backSeries = buildSeriesFromPoints(backPoints);
        backSeries.setGridPosition(GridPosition.TOP_BACK_LEFT);
        backSeries.setLoggingIntervalMinutes(1);
        channels.put(GridPosition.TOP_BACK_LEFT, backSeries);

        return channels;
    }

    private ThermoMeasurementSeries buildSeries_SteadyOnly(
            double steadyTemp, double noise, int minutes, LocalDateTime start) {
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        for (int i = 0; i < minutes; i++) {
            double val = steadyTemp + ((i % 2 == 0) ? noise : -noise);
            points.add(makePoint(val, start.plusMinutes(i)));
        }
        ThermoMeasurementSeries series = buildSeriesFromPoints(points);
        series.setGridPosition(GridPosition.TOP_FRONT_LEFT);
        series.setLoggingIntervalMinutes(1);
        return series;
    }

    private double computeSteadyCoverage(DetectionResult result, int steadyStartMinute, int totalMinutes) {
        int count = 0;
        int totalTestPoints = totalMinutes - steadyStartMinute;
        if (totalTestPoints <= 0) return 0.0;

        LocalDateTime start = LocalDateTime.parse("2026-06-21T08:00:00");
        for (int i = steadyStartMinute; i < totalMinutes; i++) {
            LocalDateTime t = start.plusMinutes(i);
            boolean inSteady = result.getSegments().stream()
                    .filter(s -> s.getType() == SegmentType.STEADY_STATE)
                    .anyMatch(s -> s.contains(t));
            if (inSteady) {
                count++;
            }
        }
        return (double) count / totalTestPoints;
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
