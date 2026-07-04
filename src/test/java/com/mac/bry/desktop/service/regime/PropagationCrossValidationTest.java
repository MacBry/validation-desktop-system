package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.config.RegimeDetectionProperties;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.regime.AirflowSourcePreset;
import com.mac.bry.desktop.model.regime.SegmentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TEST-EXC002 §4 — testy walidacji krzyżowej (wektor vs deklaracja).
 */
class PropagationCrossValidationTest {

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
    @DisplayName("TC-CV-001: Wektor zgodny z deklaracją → confidence boost")
    void tc_cv_001_vectorMatchesDeclaration() {
        // Asymetryczne źródło (3 pozycje) → cos_defrost < 1.0, więc boost +0.1
        // jest widoczny (przy pełnej symetrii cos=1.0 i cap na 1.0 maskuje boost)
        Set<GridPosition> source = EnumSet.of(
                GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT,
                GridPosition.BOTTOM_BACK_LEFT);

        var spikes = PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_WALL, null);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
        assertThat(result.confidence()).isGreaterThan(result.cosineDefrost());
    }

    @Test
    @DisplayName("TC-CV-002: Wektor sprzeczny z deklaracją → confidence penalty + warning")
    void tc_cv_002_vectorContradictsDeclaration() {
        // Propagacja od tyłu (wektor: tył→przód = defrost),
        // ale deklaracja mówi CEILING (oczekiwany defrost: góra→dół)
        Set<GridPosition> source = EnumSet.of(
                GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT,
                GridPosition.BOTTOM_BACK_LEFT, GridPosition.BOTTOM_BACK_RIGHT);

        var spikes = PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.CEILING, null);

        assertThat(result.confidence()).isLessThanOrEqualTo(0.7);
        assertThat(result.note()).containsIgnoringCase("niezgodny");
    }

    @Test
    @DisplayName("TC-CV-003: Tylko 2 czujniki z lagiem → fallback na deklarację")
    void tc_cv_003_tooFewSensors_fallbackToDeclaration() {
        List<PropagationVectorClassifier.SpikeEvent> spikes = List.of(
                new PropagationVectorClassifier.SpikeEvent(
                        GridPosition.TOP_BACK_LEFT,
                        PropagationTestHelper.BASE_TIME),
                new PropagationVectorClassifier.SpikeEvent(
                        GridPosition.TOP_FRONT_LEFT,
                        PropagationTestHelper.BASE_TIME.plusSeconds(120))
        );

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_WALL, null);

        assertThat(result.type()).isIn(SegmentType.DEFROST, SegmentType.EXCURSION);
        assertThat(result.confidence()).isLessThan(0.85);
    }

    @Test
    @DisplayName("TC-CV-004: Jednoczesna reakcja wszystkich → EXCURSION")
    void tc_cv_004_simultaneousReaction_inconclusive() {
        List<PropagationVectorClassifier.SpikeEvent> spikes = new ArrayList<>();
        for (GridPosition pos : GridPosition.values()) {
            spikes.add(new PropagationVectorClassifier.SpikeEvent(
                    pos, PropagationTestHelper.BASE_TIME));
        }

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_WALL, null);

        assertThat(result.type()).isEqualTo(SegmentType.EXCURSION);
        assertThat(result.note()).containsIgnoringCase("jednocześnie");
    }

    @Test
    @DisplayName("TC-CV-005: Ukośna propagacja → niska pewność klasyfikacji")
    void tc_cv_005_diagonalPropagation_ambiguous() {
        // Propagacja od TOP_BACK_RIGHT do BOTTOM_FRONT_LEFT — ukośna,
        // nie pasuje ani do defrostu (tył→przód), ani do drzwi (przód→tył)
        List<PropagationVectorClassifier.SpikeEvent> spikes = List.of(
                new PropagationVectorClassifier.SpikeEvent(
                        GridPosition.TOP_BACK_RIGHT, PropagationTestHelper.BASE_TIME),
                new PropagationVectorClassifier.SpikeEvent(
                        GridPosition.TOP_BACK_LEFT, PropagationTestHelper.BASE_TIME.plusSeconds(30)),
                new PropagationVectorClassifier.SpikeEvent(
                        GridPosition.BOTTOM_BACK_RIGHT, PropagationTestHelper.BASE_TIME.plusSeconds(30)),
                new PropagationVectorClassifier.SpikeEvent(
                        GridPosition.BOTTOM_FRONT_LEFT, PropagationTestHelper.BASE_TIME.plusSeconds(180)),
                new PropagationVectorClassifier.SpikeEvent(
                        GridPosition.TOP_FRONT_LEFT, PropagationTestHelper.BASE_TIME.plusSeconds(90)),
                new PropagationVectorClassifier.SpikeEvent(
                        GridPosition.BOTTOM_FRONT_RIGHT, PropagationTestHelper.BASE_TIME.plusSeconds(150))
        );

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_WALL, null);

        assertThat(result.confidence()).isLessThan(0.9);
    }

    @Test
    @DisplayName("TC-CV-006: 1 czujnik → EXCURSION (za mało danych)")
    void tc_cv_006_singleSensor() {
        List<PropagationVectorClassifier.SpikeEvent> spikes = List.of(
                new PropagationVectorClassifier.SpikeEvent(
                        GridPosition.TOP_FRONT_LEFT, PropagationTestHelper.BASE_TIME)
        );

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_WALL, null);

        assertThat(result.type()).isEqualTo(SegmentType.EXCURSION);
        assertThat(result.confidence()).isLessThanOrEqualTo(0.5);
    }
}
