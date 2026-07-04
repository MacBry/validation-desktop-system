package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.config.RegimeDetectionProperties;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.regime.AirflowSourcePreset;
import com.mac.bry.desktop.model.regime.SegmentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TEST-EXC002 §3.2 — testy klasyfikacji per konfiguracja nawiewu.
 */
class PropagationVectorClassifierTest {

    private PropagationVectorClassifier classifier;

    @BeforeEach
    void setUp() {
        RegimeDetectionProperties props = new RegimeDetectionProperties();
        props.setPropagationAware(true);
        props.setPropagationCosineSimilarityThreshold(0.7);
        props.setPropagationAmbiguityMargin(0.1);
        props.setPropagationMinSensorsForVector(3);
        classifier = new PropagationVectorClassifier(props);
    }

    @Test
    @DisplayName("TC-PVC-001: REAR_WALL defrost → DEFROST")
    void tc_pvc_001_rearWall_defrost() {
        Set<GridPosition> source = EnumSet.of(
                GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT,
                GridPosition.BOTTOM_BACK_LEFT, GridPosition.BOTTOM_BACK_RIGHT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
                PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_WALL, null);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.85);
        assertThat(result.cosineDefrost()).isGreaterThan(result.cosineDoor());
    }

    @Test
    @DisplayName("TC-PVC-002: REAR_WALL door opening → DOOR_EVENT")
    void tc_pvc_002_rearWall_doorEvent() {
        Set<GridPosition> source = EnumSet.of(
                GridPosition.TOP_FRONT_LEFT, GridPosition.TOP_FRONT_RIGHT,
                GridPosition.BOTTOM_FRONT_LEFT, GridPosition.BOTTOM_FRONT_RIGHT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
                PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_WALL, null);

        assertThat(result.type()).isEqualTo(SegmentType.DOOR_EVENT);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.85);
        assertThat(result.cosineDoor()).isGreaterThan(result.cosineDefrost());
    }

    @Test
    @DisplayName("TC-PVC-003: CEILING defrost → DEFROST (naprawa błędnej klasyfikacji)")
    void tc_pvc_003_ceiling_defrost() {
        Set<GridPosition> source = EnumSet.of(
                GridPosition.TOP_FRONT_LEFT, GridPosition.TOP_FRONT_RIGHT,
                GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
                PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.CEILING, null);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    @DisplayName("TC-PVC-004: CEILING door opening → DOOR_EVENT")
    void tc_pvc_004_ceiling_doorEvent() {
        Set<GridPosition> source = EnumSet.of(
                GridPosition.TOP_FRONT_LEFT, GridPosition.TOP_FRONT_RIGHT,
                GridPosition.BOTTOM_FRONT_LEFT, GridPosition.BOTTOM_FRONT_RIGHT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
                PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.CEILING, null);

        assertThat(result.type()).isEqualTo(SegmentType.DOOR_EVENT);
    }

    @Test
    @DisplayName("TC-PVC-005: RIGHT_WALL defrost → DEFROST")
    void tc_pvc_005_rightWall_defrost() {
        Set<GridPosition> source = EnumSet.of(
                GridPosition.TOP_FRONT_RIGHT, GridPosition.TOP_BACK_RIGHT,
                GridPosition.BOTTOM_FRONT_RIGHT, GridPosition.BOTTOM_BACK_RIGHT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
                PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.RIGHT_WALL, null);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    @DisplayName("TC-PVC-006: LEFT_WALL defrost → DEFROST")
    void tc_pvc_006_leftWall_defrost() {
        Set<GridPosition> source = EnumSet.of(
                GridPosition.TOP_FRONT_LEFT, GridPosition.TOP_BACK_LEFT,
                GridPosition.BOTTOM_FRONT_LEFT, GridPosition.BOTTOM_BACK_LEFT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
                PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.LEFT_WALL, null);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
    }

    @Test
    @DisplayName("TC-PVC-007: FLOOR defrost → DEFROST")
    void tc_pvc_007_floor_defrost() {
        Set<GridPosition> source = EnumSet.of(
                GridPosition.BOTTOM_FRONT_LEFT, GridPosition.BOTTOM_FRONT_RIGHT,
                GridPosition.BOTTOM_BACK_LEFT, GridPosition.BOTTOM_BACK_RIGHT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
                PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.FLOOR, null);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
    }

    @Test
    @DisplayName("TC-PVC-008: REAR_AND_LEFT dual defrost → DEFROST")
    void tc_pvc_008_rearAndLeft_defrost() {
        Set<GridPosition> source = EnumSet.of(
                GridPosition.TOP_BACK_LEFT, GridPosition.BOTTOM_BACK_LEFT,
                GridPosition.TOP_BACK_RIGHT, GridPosition.BOTTOM_BACK_RIGHT,
                GridPosition.BOTTOM_FRONT_LEFT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
                PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_AND_LEFT, null);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
    }

    @Test
    @DisplayName("TC-PVC-009: CUSTOM mode → DEFROST z ręcznie zdefiniowanymi pozycjami")
    void tc_pvc_009_custom_defrost() {
        Set<GridPosition> customSource = EnumSet.of(
                GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT);

        Set<GridPosition> spikeSource = EnumSet.of(
                GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
                PropagationTestHelper.createPropagation(spikeSource, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.CUSTOM, customSource);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
    }
}
