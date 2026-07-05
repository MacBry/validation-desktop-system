package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testy generatora hipotez przyczynowych (DP-001 §4.6, Faza 5).
 * Kontrakt: zdania sterowane cechami sygnału — czas, amplituda, pozycje,
 * czas powrotu, wzorzec — deterministycznie z danych.
 */
class CausalHypothesisGeneratorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 6, 21, 20, 32);

    private final CausalHypothesisGenerator generator = new CausalHypothesisGenerator();

    @Test
    @DisplayName("TC-CHG-001: Otwarcie drzwi → zdanie z czasem, amplitudą, pozycjami i wzorcem")
    void tc_chg_001_doorEventSentence() {
        // Szpilka +14°C w 6 min, powrót do baseline w 40 min, dwa czujniki górne tylne
        MeasurementSegment segTP = doorSpike(GridPosition.TOP_BACK_RIGHT);
        MeasurementSegment segTL = doorSpike(GridPosition.TOP_BACK_LEFT);

        List<String> hypotheses = generator.generateHypotheses(List.of(segTP, segTL));

        assertThat(hypotheses).hasSize(1);
        String sentence = hypotheses.get(0);
        assertThat(sentence)
                .contains("20:32")
                .contains("+14")
                .contains("G-TP")
                .contains("G-TL")
                .contains("powrót")
                .containsIgnoringCase("otwarciem drzwi")
                .contains("0,92");
    }

    @Test
    @DisplayName("TC-CHG-002: Nakładające się zdarzenia tego samego typu → jedna hipoteza")
    void tc_chg_002_overlappingSameTypeGrouped() {
        List<String> hypotheses = generator.generateHypotheses(List.of(
                doorSpike(GridPosition.TOP_BACK_RIGHT),
                doorSpike(GridPosition.TOP_BACK_LEFT),
                doorSpike(GridPosition.BOTTOM_BACK_LEFT)));

        assertThat(hypotheses).hasSize(1);
    }

    @Test
    @DisplayName("TC-CHG-003: Zdarzenia rozłączne czasowo → osobne hipotezy chronologicznie")
    void tc_chg_003_disjointEventsSeparateHypotheses() {
        MeasurementSegment first = doorSpike(GridPosition.TOP_BACK_RIGHT);
        MeasurementSegment later = segment(SegmentType.DEFROST, GridPosition.TOP_BACK_LEFT,
                T0.plusHours(5), T0.plusHours(5).plusMinutes(30), 0.88,
                spikeSeries(GridPosition.TOP_BACK_LEFT, T0.plusHours(5), 30, -20.0, -12.0));

        List<String> hypotheses = generator.generateHypotheses(List.of(later, first));

        assertThat(hypotheses).hasSize(2);
        assertThat(hypotheses.get(0)).contains("20:32");
        assertThat(hypotheses.get(1)).containsIgnoringCase("odszraniania").isNotEmpty();
    }

    @Test
    @DisplayName("TC-CHG-004: EXCURSION → hipoteza wymaga wyjaśnienia przez operatora")
    void tc_chg_004_excursionRequiresOperator() {
        MeasurementSegment seg = segment(SegmentType.EXCURSION, GridPosition.BOTTOM_FRONT_LEFT,
                T0, T0.plusMinutes(90), 0.5,
                spikeSeries(GridPosition.BOTTOM_FRONT_LEFT, T0, 90, 4.0, 12.0));

        List<String> hypotheses = generator.generateHypotheses(List.of(seg));

        assertThat(hypotheses).hasSize(1);
        assertThat(hypotheses.get(0))
                .containsIgnoringCase("nie odpowiada znanym zdarzeniom")
                .containsIgnoringCase("operatora");
    }

    @Test
    @DisplayName("TC-CHG-005: Status weryfikacji operatora w zdaniu (Faza 4 → Faza 5)")
    void tc_chg_005_verificationStatusIncluded() {
        MeasurementSegment accepted = doorSpike(GridPosition.TOP_BACK_RIGHT);
        accepted.setConfirmedBy("jkowalski");
        accepted.setAccepted(true);

        MeasurementSegment rejected = segment(SegmentType.DEFROST, GridPosition.TOP_BACK_LEFT,
                T0.plusHours(6), T0.plusHours(6).plusMinutes(30), 0.7,
                spikeSeries(GridPosition.TOP_BACK_LEFT, T0.plusHours(6), 30, -20.0, -14.0));
        rejected.setConfirmedBy("anowak");
        rejected.setAccepted(false);

        List<String> hypotheses = generator.generateHypotheses(List.of(accepted, rejected));

        assertThat(hypotheses.get(0)).contains("[potwierdzone: jkowalski]");
        assertThat(hypotheses.get(1)).contains("[ODRZUCONE przez: anowak]");
    }

    @Test
    @DisplayName("TC-CHG-006: Determinizm — te same dane dają identyczne zdania")
    void tc_chg_006_deterministic() {
        List<MeasurementSegment> events = List.of(
                doorSpike(GridPosition.TOP_BACK_RIGHT), doorSpike(GridPosition.TOP_BACK_LEFT));

        assertThat(generator.generateHypotheses(events))
                .isEqualTo(generator.generateHypotheses(events));
    }

    @Test
    @DisplayName("TC-CHG-007: Brak zdarzeń → pusta lista")
    void tc_chg_007_noEventsEmptyList() {
        assertThat(generator.generateHypotheses(List.of())).isEmpty();
        assertThat(generator.generateHypotheses(null)).isEmpty();
    }

    @Test
    @DisplayName("TC-CHG-008: Spadek temperatury raportowany jako spadek z ujemną amplitudą")
    void tc_chg_008_negativeDeltaReportedAsDrop() {
        MeasurementSegment seg = segment(SegmentType.SETPOINT_CHANGE, GridPosition.TOP_FRONT_LEFT,
                T0, T0.plusHours(2), 0.85,
                rampSeries(GridPosition.TOP_FRONT_LEFT, T0, 120, 2.0, -6.5));

        List<String> hypotheses = generator.generateHypotheses(List.of(seg));

        assertThat(hypotheses.get(0))
                .contains("spadek")
                .contains("-8,5")
                .containsIgnoringCase("zmianą nastawy");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Szpilka drzwiowa: baseline 4°C → szczyt 18°C po 6 min → powrót do 4,5°C po 40 min. */
    private MeasurementSegment doorSpike(GridPosition pos) {
        ThermoMeasurementSeries series = spikeSeries(pos, T0, 40, 4.0, 18.0);
        return segment(SegmentType.DOOR_EVENT, pos, T0, T0.plusMinutes(40), 0.92, series);
    }

    /**
     * Seria ze szpilką: start w baseline, szczyt (peak) w 6. minucie,
     * liniowy powrót do baseline+0.5 na końcu okna.
     */
    private ThermoMeasurementSeries spikeSeries(GridPosition pos, LocalDateTime start,
                                                int durationMin, double baseline, double peak) {
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        for (int i = 0; i <= durationMin; i++) {
            double temp;
            if (i < 6) {
                temp = baseline + (peak - baseline) * i / 6.0;
            } else {
                double progress = (double) (i - 6) / Math.max(durationMin - 6, 1);
                temp = peak - (peak - baseline - 0.5) * progress;
            }
            points.add(point(temp, start.plusMinutes(i)));
        }
        return series(pos, points);
    }

    /** Rampa liniowa od baseline do wartości końcowej — bez powrotu (setpoint change). */
    private ThermoMeasurementSeries rampSeries(GridPosition pos, LocalDateTime start,
                                               int durationMin, double from, double to) {
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        for (int i = 0; i <= durationMin; i++) {
            double temp = from + (to - from) * i / durationMin;
            points.add(point(temp, start.plusMinutes(i)));
        }
        return series(pos, points);
    }

    private ThermoMeasurementSeries series(GridPosition pos, List<ThermoMeasurementPoint> points) {
        ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                .gridPosition(pos)
                .measurements(points)
                .build();
        points.forEach(p -> p.setSeries(series));
        return series;
    }

    private ThermoMeasurementPoint point(double temp, LocalDateTime time) {
        return ThermoMeasurementPoint.builder()
                .rawCelsius(temp)
                .timestampLocal(time)
                .build();
    }

    private MeasurementSegment segment(SegmentType type, GridPosition pos,
                                       LocalDateTime from, LocalDateTime to,
                                       double confidence, ThermoMeasurementSeries series) {
        return MeasurementSegment.builder()
                .type(type)
                .fromTimestamp(from)
                .toTimestamp(to)
                .confidence(confidence)
                .accepted(true)
                .series(series)
                .build();
    }
}
