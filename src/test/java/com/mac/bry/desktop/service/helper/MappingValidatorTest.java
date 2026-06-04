package com.mac.bry.desktop.service.helper;

import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MappingValidatorTest {

    private RevalidationSession createSessionWithTemps(Map<RevalidationSession.GridPosition, Double[]> tempMap) {
        RevalidationSession session = new RevalidationSession();
        Map<RevalidationSession.GridPosition, RevalidationSession.PositionData> assigned = new HashMap<>();

        LocalDateTime baseTime = LocalDateTime.now();

        for (RevalidationSession.GridPosition pos : RevalidationSession.GridPosition.values()) {
            Double[] temps = tempMap.get(pos);
            if (temps != null) {
                ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                        .gridPosition(pos)
                        .build();

                for (int i = 0; i < temps.length; i++) {
                    series.addMeasurement(ThermoMeasurementPoint.builder()
                            .measurementIndex(i + 1)
                            .timestampLocal(baseTime.plusHours(i))
                            .rawCelsius(temps[i])
                            .build());
                }

                RevalidationSession.PositionData data = RevalidationSession.PositionData.builder()
                        .serialNumber("SN-" + pos.name())
                        .series(series)
                        .build();

                assigned.put(pos, data);
            }
        }

        session.setAssignedPositions(assigned);
        return session;
    }

    @Test
    @DisplayName("Should successfully validate mapping and identify unique Hotspot and Coldspot")
    void shouldSuccessfullyValidateUniqueExtremes() {
        Map<RevalidationSession.GridPosition, Double[]> tempMap = new HashMap<>();
        // Base temp is 5.0
        for (RevalidationSession.GridPosition pos : RevalidationSession.GridPosition.values()) {
            tempMap.put(pos, new Double[]{5.0, 5.0, 5.0});
        }
        // Unique Hotspot at TOP_FRONT_LEFT (8.5)
        tempMap.put(RevalidationSession.GridPosition.TOP_FRONT_LEFT, new Double[]{5.0, 8.5, 5.0});
        // Unique Coldspot at BOTTOM_BACK_RIGHT (1.2)
        tempMap.put(RevalidationSession.GridPosition.BOTTOM_BACK_RIGHT, new Double[]{5.0, 1.2, 5.0});

        RevalidationSession session = createSessionWithTemps(tempMap);
        MappingValidator.MappingResult result = MappingValidator.validate(session);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getHotspot()).isEqualTo(RevalidationSession.GridPosition.TOP_FRONT_LEFT);
        assertThat(result.getColdspot()).isEqualTo(RevalidationSession.GridPosition.BOTTOM_BACK_RIGHT);
        assertThat(result.getMaxTemperature()).isEqualTo(8.5);
        assertThat(result.getMinTemperature()).isEqualTo(1.2);
    }

    @Test
    @DisplayName("Should resolve Hotspot ambiguity when multiple sensors reach the global maximum temperature")
    void shouldResolveHotspotAmbiguityUsingConsensus() {
        Map<RevalidationSession.GridPosition, Double[]> tempMap = new HashMap<>();
        for (RevalidationSession.GridPosition pos : RevalidationSession.GridPosition.values()) {
            tempMap.put(pos, new Double[]{5.0, 5.0, 5.0});
        }
        // Two positions reach global max (8.5)
        // BUT TOP_FRONT_LEFT has a higher mean (5.0, 8.5, 6.0) -> mean = 6.5
        // while TOP_FRONT_RIGHT has a lower mean (5.0, 8.5, 5.0) -> mean = 6.17
        tempMap.put(RevalidationSession.GridPosition.TOP_FRONT_LEFT, new Double[]{5.0, 8.5, 6.0});
        tempMap.put(RevalidationSession.GridPosition.TOP_FRONT_RIGHT, new Double[]{5.0, 8.5, 5.0});
        // Unique Coldspot (1.2)
        tempMap.put(RevalidationSession.GridPosition.BOTTOM_BACK_RIGHT, new Double[]{5.0, 1.2, 5.0});

        RevalidationSession session = createSessionWithTemps(tempMap);
        MappingValidator.MappingResult result = MappingValidator.validate(session);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getHotspot()).isEqualTo(RevalidationSession.GridPosition.TOP_FRONT_LEFT); // Resolved to the one with higher mean/MKT
        assertThat(result.getColdspot()).isEqualTo(RevalidationSession.GridPosition.BOTTOM_BACK_RIGHT);
    }

    @Test
    @DisplayName("Should resolve Coldspot ambiguity when multiple sensors reach the global minimum temperature")
    void shouldResolveColdspotAmbiguityUsingConsensus() {
        Map<RevalidationSession.GridPosition, Double[]> tempMap = new HashMap<>();
        for (RevalidationSession.GridPosition pos : RevalidationSession.GridPosition.values()) {
            tempMap.put(pos, new Double[]{5.0, 5.0, 5.0});
        }
        // Unique Hotspot (8.5)
        tempMap.put(RevalidationSession.GridPosition.TOP_FRONT_LEFT, new Double[]{5.0, 8.5, 5.0});
        // Two positions reach global min (1.2)
        // BUT BOTTOM_BACK_LEFT has a lower mean (5.0, 1.2, 4.0) -> mean = 3.4
        // while BOTTOM_BACK_RIGHT has a higher mean (5.0, 1.2, 5.0) -> mean = 3.73
        tempMap.put(RevalidationSession.GridPosition.BOTTOM_BACK_LEFT, new Double[]{5.0, 1.2, 4.0});
        tempMap.put(RevalidationSession.GridPosition.BOTTOM_BACK_RIGHT, new Double[]{5.0, 1.2, 5.0});

        RevalidationSession session = createSessionWithTemps(tempMap);
        MappingValidator.MappingResult result = MappingValidator.validate(session);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getColdspot()).isEqualTo(RevalidationSession.GridPosition.BOTTOM_BACK_LEFT); // Resolved to the one with lower mean
        assertThat(result.getHotspot()).isEqualTo(RevalidationSession.GridPosition.TOP_FRONT_LEFT);
    }

    @Test
    @DisplayName("Should fail when Hotspot and Coldspot fall on the same sensor/position")
    void shouldFailWhenHotspotAndColdspotOverlap() {
        Map<RevalidationSession.GridPosition, Double[]> tempMap = new HashMap<>();
        for (RevalidationSession.GridPosition pos : RevalidationSession.GridPosition.values()) {
            tempMap.put(pos, new Double[]{5.0, 5.0, 5.0});
        }
        // One position TOP_FRONT_LEFT contains both max (8.5) and min (1.2)
        tempMap.put(RevalidationSession.GridPosition.TOP_FRONT_LEFT, new Double[]{1.2, 5.0, 8.5});

        RevalidationSession session = createSessionWithTemps(tempMap);
        MappingValidator.MappingResult result = MappingValidator.validate(session);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Kolizja punktów");
    }

    @Test
    @DisplayName("Should fail when not all 8 sensors have been uploaded")
    void shouldFailWhenMissingSensors() {
        Map<RevalidationSession.GridPosition, Double[]> tempMap = new HashMap<>();
        // Put only 7 positions
        for (RevalidationSession.GridPosition pos : RevalidationSession.GridPosition.values()) {
            if (pos != RevalidationSession.GridPosition.BOTTOM_BACK_RIGHT) {
                tempMap.put(pos, new Double[]{5.0, 5.0});
            }
        }

        RevalidationSession session = createSessionWithTemps(tempMap);
        MappingValidator.MappingResult result = MappingValidator.validate(session);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Brak wgranej serii pomiarowej dla pozycji");
    }
}
