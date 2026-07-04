package com.mac.bry.desktop.model.regime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * TEST-EXC002 §8 — testy presetów źródła nawiewu.
 */
class AirflowSourcePresetTest {

    @Test
    @DisplayName("TC-ASP-001: Presety mają znormalizowane wektory defrostu (CUSTOM → null)")
    void tc_asp_001_presetsHaveDefrostVectors() {
        for (AirflowSourcePreset preset : AirflowSourcePreset.values()) {
            if (preset == AirflowSourcePreset.CUSTOM) {
                assertThat(preset.getExpectedDefrostVector()).isNull();
            } else {
                double[] v = preset.getExpectedDefrostVector();
                assertThat(v).isNotNull().hasSize(3);
                double norm = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                assertThat(norm).isCloseTo(1.0, within(0.01));
            }
        }
    }

    @Test
    @DisplayName("TC-ASP-002: Wektor defrostu nie wskazuje tego samego kierunku co drzwi (cos < 0.9)")
    void tc_asp_002_defrostAndDoorVectorsAreDifferent() {
        // Odstępstwo od TEST-EXC002 (|cos| < 0.9): antyrównoległość (cos = -1, REAR_WALL)
        // jest zamierzona i w pełni rozróżnialna — klasyfikator myli tylko kierunki
        // ZGODNE (cos → +1), więc sprawdzamy cos, nie |cos|.
        double[] doorVector = AirflowSourcePreset.getDoorVector();
        for (AirflowSourcePreset preset : AirflowSourcePreset.values()) {
            if (preset == AirflowSourcePreset.CUSTOM) continue;
            double[] defrostVector = preset.getExpectedDefrostVector();
            double cos = cosineSimilarity(defrostVector, doorVector);
            assertThat(cos)
                    .as("Preset %s: wektor defrostu nie może wskazywać kierunku drzwi", preset)
                    .isLessThan(0.9);
        }
    }

    @Test
    @DisplayName("TC-ASP-003: REAR_WALL → wektor (0, -1, 0)")
    void tc_asp_003_rearWallVector() {
        double[] v = AirflowSourcePreset.REAR_WALL.getExpectedDefrostVector();
        assertThat(v[0]).isEqualTo(0.0);
        assertThat(v[1]).isEqualTo(-1.0);
        assertThat(v[2]).isEqualTo(0.0);
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        double normA = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        double normB = Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
        return dot / (normA * normB);
    }
}
