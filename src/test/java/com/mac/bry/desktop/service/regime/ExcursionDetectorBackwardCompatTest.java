package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.config.RegimeDetectionProperties;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.model.regime.AirflowSourcePreset;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TEST-EXC002 §5 i §7 — kompatybilność wsteczna nowej klasyfikacji przestrzennej
 * oraz regresja: REAR_WALL + propagationAware=true daje te same typy segmentów
 * co dotychczasowa logika isFrontPosition().
 */
class ExcursionDetectorBackwardCompatTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 6, 25, 8, 0, 0);

    @Test
    @DisplayName("TC-BC-001: Feature flag OFF → stara logika isFrontPosition()")
    void tc_bc_001_featureFlagOff_usesLegacyLogic() {
        RegimeDetectionProperties props = createProps(false);
        ExcursionDetector detector = new ExcursionDetector(props, null /* propagation nieużyte */);

        // Front reaguje pierwszy (minuta 30), tył 2 minuty później
        Map<GridPosition, ThermoMeasurementSeries> channels = new HashMap<>();
        channels.put(GridPosition.TOP_FRONT_LEFT, buildSeriesWithSpike(30));
        channels.put(GridPosition.TOP_BACK_LEFT, buildSeriesWithSpike(32));

        Map<GridPosition, List<MeasurementSegment>> result = detector.detectAll(channels);

        List<MeasurementSegment> frontSegments = result.get(GridPosition.TOP_FRONT_LEFT);
        assertThat(frontSegments).isNotEmpty();
        assertThat(frontSegments.get(0).getType()).isEqualTo(SegmentType.DOOR_EVENT);
    }

    @Test
    @DisplayName("TC-BC-002: REAR_WALL preset → kompatybilny wynik z nową logiką")
    void tc_bc_002_rearWallPreset_sameResultAsLegacy() {
        RegimeDetectionProperties props = createProps(true);
        PropagationVectorClassifier classifier = new PropagationVectorClassifier(props);
        ExcursionDetector detector = new ExcursionDetector(props, classifier);

        // Defrost od tyłu: 2 kanały tylne reagują pierwsze, 2 przednie z lagiem
        Map<GridPosition, ThermoMeasurementSeries> channels = createDefrostFromBack();

        Map<GridPosition, List<MeasurementSegment>> result =
                detector.detectAll(channels, AirflowSourcePreset.REAR_WALL, null);

        for (List<MeasurementSegment> segments : result.values()) {
            for (MeasurementSegment seg : segments) {
                if (seg.getType() == SegmentType.DEFROST || seg.getType() == SegmentType.DOOR_EVENT) {
                    assertThat(seg.getType()).isEqualTo(SegmentType.DEFROST);
                }
            }
        }
    }

    @Test
    @DisplayName("TC-BC-003: detectAll() bez parametrów → domyślny REAR_WALL, bez wyjątku")
    void tc_bc_003_overloadWithoutParams() {
        RegimeDetectionProperties props = createProps(false);
        ExcursionDetector detector = new ExcursionDetector(props, null);

        Map<GridPosition, ThermoMeasurementSeries> channels = new HashMap<>();
        channels.put(GridPosition.TOP_FRONT_LEFT, buildFlatSeries());

        assertThatCode(() -> detector.detectAll(channels)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TC-REG-001: Regresja — REAR_WALL nowa vs stara logika: identyczne typy segmentów")
    void tc_reg_001_regressionRearWallIdenticalTypes() {
        Map<GridPosition, ThermoMeasurementSeries> channels = createDefrostFromBack();

        // Stara logika (flaga off)
        RegimeDetectionProperties propsOld = createProps(false);
        ExcursionDetector detectorOld = new ExcursionDetector(propsOld, null);
        Map<GridPosition, List<MeasurementSegment>> oldResult = detectorOld.detectAll(channels);

        // Nowa logika z REAR_WALL (flaga on) — na świeżych seriach, bo segmenty są mutowane
        Map<GridPosition, ThermoMeasurementSeries> channelsFresh = createDefrostFromBack();
        RegimeDetectionProperties propsNew = createProps(true);
        PropagationVectorClassifier classifier = new PropagationVectorClassifier(propsNew);
        ExcursionDetector detectorNew = new ExcursionDetector(propsNew, classifier);
        Map<GridPosition, List<MeasurementSegment>> newResult =
                detectorNew.detectAll(channelsFresh, AirflowSourcePreset.REAR_WALL, null);

        for (GridPosition pos : GridPosition.values()) {
            List<SegmentType> oldTypes = extractTypes(oldResult.getOrDefault(pos, List.of()));
            List<SegmentType> newTypes = extractTypes(newResult.getOrDefault(pos, List.of()));
            assertThat(newTypes)
                    .as("Pozycja %s: typy segmentów powinny być identyczne", pos)
                    .isEqualTo(oldTypes);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private RegimeDetectionProperties createProps(boolean propagationAware) {
        RegimeDetectionProperties props = new RegimeDetectionProperties();
        props.setPropagationAware(propagationAware);
        props.setPropagationCosineSimilarityThreshold(0.7);
        props.setPropagationAmbiguityMargin(0.1);
        props.setPropagationMinSensorsForVector(3);
        props.setExcursionGradientThreshold(0.5);
        props.setExcursionReturnWindowMinutes(60);
        return props;
    }

    /** Defrost od tyłu: tylne kanały reagują w min. 30, przednie w min. 32. */
    private Map<GridPosition, ThermoMeasurementSeries> createDefrostFromBack() {
        Map<GridPosition, ThermoMeasurementSeries> channels = new HashMap<>();
        channels.put(GridPosition.TOP_BACK_LEFT, buildSeriesWithSpike(30));
        channels.put(GridPosition.TOP_BACK_RIGHT, buildSeriesWithSpike(30));
        channels.put(GridPosition.TOP_FRONT_LEFT, buildSeriesWithSpike(32));
        channels.put(GridPosition.TOP_FRONT_RIGHT, buildSeriesWithSpike(32));
        return channels;
    }

    /**
     * Seria 120 punktów co 1 min, baseline 4.0°C, z pojedynczą szpilką
     * startującą w podanej minucie (gradient 4°C/min, powrót po ~7 min).
     */
    private ThermoMeasurementSeries buildSeriesWithSpike(int spikeStartMinute) {
        double[] spikeShape = {8.0, 9.0, 8.0, 7.0, 6.0, 5.5, 5.0};
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            double temp = 4.0;
            int offset = i - (spikeStartMinute + 1);
            if (offset >= 0 && offset < spikeShape.length) {
                temp = spikeShape[offset];
            }
            points.add(ThermoMeasurementPoint.builder()
                    .measurementIndex(i + 1)
                    .rawCelsius(temp)
                    .timestampLocal(T0.plusMinutes(i))
                    .build());
        }
        return buildSeriesFromPoints(points);
    }

    private ThermoMeasurementSeries buildFlatSeries() {
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            points.add(ThermoMeasurementPoint.builder()
                    .measurementIndex(i + 1)
                    .rawCelsius(4.0)
                    .timestampLocal(T0.plusMinutes(i))
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

    private List<SegmentType> extractTypes(List<MeasurementSegment> segments) {
        return segments.stream().map(MeasurementSegment::getType).toList();
    }
}
