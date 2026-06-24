package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.config.RegimeDetectionProperties;
import com.mac.bry.desktop.dto.stats.ConditionalStatsDTO;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.RunMode;
import com.mac.bry.desktop.model.regime.SegmentType;
import com.mac.bry.desktop.service.MetrologicalStatsService;
import com.mac.bry.desktop.service.stats.SpcEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@ExtendWith(MockitoExtension.class)
class RegimeAwareStatsServiceTest {

    @InjectMocks
    private RegimeAwareStatsService service;

    @Mock
    private MetrologicalStatsService metrologicalStatsService;

    @Spy
    private RegimeDetectionProperties properties = new RegimeDetectionProperties();

    @BeforeEach
    void setUp() {
        properties.setMinSteadyPointsForStats(30);
    }

    @Test
    @DisplayName("TC-RAWS-001: Tylko punkty STEADY_STATE trafiają do obliczeń")
    void tc_raws_001_onlySteadyPointsPassToCalculations() {
        LocalDateTime t = LocalDateTime.parse("2026-06-21T08:00:00");
        ThermoMeasurementSeries series = buildSeriesWithPoints(t, 120);

        List<MeasurementSegment> segments = List.of(
                makeSegment(series, SegmentType.EQUILIBRATION, t, t.plusMinutes(60)),
                makeSegment(series, SegmentType.STEADY_STATE,  t.plusMinutes(60), t.plusMinutes(120))
        );

        ConditionalStatsDTO result = service.calculateConditionalStatistics(
                series, segments, RunMode.QUALIFICATION, 1.8, 8.0);

        assertThat(result.isHasSteadyStateData()).isTrue();
        assertThat(result.getSteadyStatePointCount())
                .as("Dokładnie 60 punktów STEADY_STATE")
                .isEqualTo(60);
        assertThat(result.getSteadyStateCoveragePercent())
                .as("50% pokrycia przez STEADY_STATE")
                .isCloseTo(50.0, within(1.0));
    }

    @Test
    @DisplayName("TC-RAWS-002: Za mało punktów STEADY_STATE → hasSteadyStateData = false")
    void tc_raws_002_tooFewSteadyPoints_returnsNoCoverage() {
        LocalDateTime t = LocalDateTime.parse("2026-06-21T08:00:00");
        ThermoMeasurementSeries series = buildSeriesWithPoints(t, 100);

        List<MeasurementSegment> segments = List.of(
                makeSegment(series, SegmentType.STEADY_STATE, t, t.plusMinutes(20))
        );

        ConditionalStatsDTO result = service.calculateConditionalStatistics(
                series, segments, RunMode.QUALIFICATION, 1.8, 8.0);

        assertThat(result.isHasSteadyStateData()).isFalse();
        assertThat(result.getSteadyStatePointCount()).isLessThan(30);
    }

    @Test
    @DisplayName("TC-RAWS-003: Odrzucony segment (accepted=false) nie wpływa na statystyki")
    void tc_raws_003_rejectedSegment_isIgnored() {
        LocalDateTime t = LocalDateTime.parse("2026-06-21T08:00:00");
        ThermoMeasurementSeries series = buildSeriesWithPoints(t, 120);

        MeasurementSegment rejected = makeSegment(series, SegmentType.STEADY_STATE, t, t.plusMinutes(60));
        rejected.setAccepted(false);

        MeasurementSegment accepted = makeSegment(series, SegmentType.STEADY_STATE,
                t.plusMinutes(60), t.plusMinutes(120));

        ConditionalStatsDTO result = service.calculateConditionalStatistics(
                series, List.of(rejected, accepted), RunMode.QUALIFICATION, 1.8, 8.0);

        assertThat(result.getSteadyStatePointCount())
                .as("Tylko zaakceptowane segmenty STEADY_STATE")
                .isEqualTo(60);
    }

    @Test
    @DisplayName("TC-RAWS-004: Cpk w STEADY_STATE > Cpk na całym przebiegu")
    void tc_raws_004_conditionalCpkBetterThanFull() {
        LocalDateTime t = LocalDateTime.parse("2026-06-21T08:00:00");
        List<ThermoMeasurementPoint> allPoints = new ArrayList<>();

        for (int i = 0; i < 60; i++) {
            allPoints.add(makePoint(5.0 - 0.183 * i, t.plusMinutes(i)));
        }
        for (int i = 60; i < 120; i++) {
            double v = 4.5 + (i % 3 == 0 ? 0.2 : -0.2);
            allPoints.add(makePoint(v, t.plusMinutes(i)));
        }

        ThermoMeasurementSeries series = buildSeriesFromPoints(allPoints);
        List<MeasurementSegment> segments = List.of(
                makeSegment(series, SegmentType.EQUILIBRATION, t, t.plusMinutes(60)),
                makeSegment(series, SegmentType.STEADY_STATE, t.plusMinutes(60), t.plusMinutes(120))
        );

        ConditionalStatsDTO conditional = service.calculateConditionalStatistics(
                series, segments, RunMode.QUALIFICATION, 1.8, 8.0);

        double[] allRaw = allPoints.stream().mapToDouble(ThermoMeasurementPoint::getRawCelsius).toArray();
        double cpkFull = SpcEngine.calculateCapability(allRaw, 1.8, 8.0).getCpk();

        assertThat(conditional.getCpkSteady())
                .as("Cpk w fazie STEADY_STATE musi być lepszy niż na całym przebiegu z transientem")
                .isGreaterThan(cpkFull);
    }

    private ThermoMeasurementSeries buildSeriesWithPoints(LocalDateTime start, int count) {
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(ThermoMeasurementPoint.builder()
                    .measurementIndex(i + 1)
                    .rawCelsius(5.0)
                    .timestampLocal(start.plusMinutes(i))
                    .build());
        }
        return buildSeriesFromPoints(points);
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

    private ThermoMeasurementPoint makePoint(double temp, LocalDateTime time) {
        return ThermoMeasurementPoint.builder()
                .rawCelsius(temp)
                .timestampLocal(time)
                .build();
    }

    private MeasurementSegment makeSegment(ThermoMeasurementSeries series, SegmentType type, LocalDateTime from, LocalDateTime to) {
        return MeasurementSegment.builder()
                .series(series)
                .fromTimestamp(from)
                .toTimestamp(to)
                .type(type)
                .confidence(0.9)
                .accepted(true)
                .build();
    }
}
