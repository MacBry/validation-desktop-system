package com.mac.bry.desktop.model.regime;

import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TEST-EXC002 §2 — testy mapowania GridPosition → współrzędne 3D.
 */
class GridPositionCoordinatesTest {

    @Test
    @DisplayName("TC-GPC-001: Każda GridPosition ma zdefiniowane współrzędne [0,1]^3")
    void tc_gpc_001_allPositionsHaveCoordinates() {
        for (GridPosition pos : GridPosition.values()) {
            double[] coords = GridPositionCoordinates.getCoordinates(pos);
            assertThat(coords).isNotNull().hasSize(3);
            for (double c : coords) {
                assertThat(c).isBetween(0.0, 1.0);
            }
        }
    }

    @Test
    @DisplayName("TC-GPC-002: Warstwa TOP → z=1.0, BOTTOM → z=0.0")
    void tc_gpc_002_topBottomLayers() {
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.TOP_FRONT_LEFT)[2]).isEqualTo(1.0);
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.BOTTOM_FRONT_LEFT)[2]).isEqualTo(0.0);
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.TOP_BACK_RIGHT)[2]).isEqualTo(1.0);
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.BOTTOM_BACK_RIGHT)[2]).isEqualTo(0.0);
    }

    @Test
    @DisplayName("TC-GPC-003: FRONT → y=0.0, BACK → y=1.0")
    void tc_gpc_003_frontBackAxis() {
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.TOP_FRONT_LEFT)[1]).isEqualTo(0.0);
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.TOP_BACK_LEFT)[1]).isEqualTo(1.0);
    }

    @Test
    @DisplayName("TC-GPC-004: LEFT → x=0.0, RIGHT → x=1.0")
    void tc_gpc_004_leftRightAxis() {
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.TOP_FRONT_LEFT)[0]).isEqualTo(0.0);
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.TOP_FRONT_RIGHT)[0]).isEqualTo(1.0);
    }
}
