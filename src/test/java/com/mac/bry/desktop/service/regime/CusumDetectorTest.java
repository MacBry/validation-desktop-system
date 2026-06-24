package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.config.RegimeDetectionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class CusumDetectorTest {

    private CusumDetector detector;
    private RegimeDetectionProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RegimeDetectionProperties();
        properties.setCusumK(0.5);
        properties.setCusumH(5.0);
        properties.setCusumBaselinePoints(30);
        detector = new CusumDetector(properties);
    }

    @Test
    @DisplayName("TC-CUSUM-001: Trwałe przesunięcie +5°C → detekcja UP change point")
    void tc_cusum_001_sustainedUpwardShift_shouldDetectChangePoint() {
        double[] values = new double[120];
        Arrays.fill(values, 0, 60, 5.0);
        Arrays.fill(values, 60, 120, 10.0);

        List<CusumDetector.ChangePoint> changes = detector.detect(values, 0.3);

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getDirection()).isEqualTo(CusumDetector.Direction.UP);
        assertThat(changes.get(0).getIndex())
                .as("Change point musi być w oknie 55–70 (actual: 60)")
                .isBetween(55, 70);
    }

    @Test
    @DisplayName("TC-CUSUM-002: Stabilny sygnał → brak change points")
    void tc_cusum_002_stableSignal_noFalsePositives() {
        double[] values = new double[200];
        Random random = new Random(42);
        for (int i = 0; i < 200; i++) {
            values[i] = 5.0 + (random.nextDouble() - 0.5) * 0.6;
        }

        List<CusumDetector.ChangePoint> changes = detector.detect(values, 0.3);

        assertThat(changes).isEmpty();
    }

    @Test
    @DisplayName("TC-CUSUM-003: FastCooling — zjazd −7°C")
    void tc_cusum_003_fastCooling_shouldDetectDownwardChange() {
        double[] values = new double[720];
        Arrays.fill(values, 0, 240, 5.0);
        Arrays.fill(values, 240, 720, -2.5);

        List<CusumDetector.ChangePoint> changes = detector.detect(values, 1.5);

        assertThat(changes).isNotEmpty();
        assertThat(changes.stream().anyMatch(c -> c.getDirection() == CusumDetector.Direction.DOWN))
                .as("Musi wykryć zmianę w dół (fastcooling)")
                .isTrue();
    }

    @Test
    @DisplayName("TC-CUSUM-004: Dwa kolejne shifty")
    void tc_cusum_004_twoShifts_shouldDetectBoth() {
        double[] values = new double[300];
        Arrays.fill(values, 0, 100, 5.0);
        Arrays.fill(values, 100, 200, -2.5);
        Arrays.fill(values, 200, 300, 5.0);

        List<CusumDetector.ChangePoint> changes = detector.detect(values, 1.0);

        assertThat(changes).as("Muszą być wykryte dwa change points: DOWN i UP").hasSize(2);
    }
}
