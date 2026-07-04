package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.regime.GridPositionCoordinates;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * TEST-EXC002 §3.1 — helper generujący syntetyczne szpilki symulujące
 * propagację ciepła od pozycji źródłowych do pozostałych z lagiem
 * proporcjonalnym do odległości euklidesowej.
 */
final class PropagationTestHelper {

    static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 6, 25, 10, 0, 0);

    private PropagationTestHelper() {
    }

    /**
     * Tworzy listę SpikeEvent symulujących propagację od podanych pozycji źródłowych
     * do pozostałych z rosnącym lagiem.
     */
    static List<PropagationVectorClassifier.SpikeEvent> createPropagation(
            Set<GridPosition> sourcePositions, int lagBetweenLayersSeconds) {

        List<PropagationVectorClassifier.SpikeEvent> events = new ArrayList<>();

        for (GridPosition pos : GridPosition.values()) {
            long lagSeconds;
            if (sourcePositions.contains(pos)) {
                lagSeconds = 0;
            } else {
                double[] sourceCoord = computeCentroid(sourcePositions);
                double[] posCoord = GridPositionCoordinates.getCoordinates(pos);
                double distance = euclideanDistance(sourceCoord, posCoord);
                lagSeconds = (long) (distance * lagBetweenLayersSeconds);
            }
            events.add(new PropagationVectorClassifier.SpikeEvent(
                    pos, BASE_TIME.plusSeconds(lagSeconds)));
        }
        return events;
    }

    private static double[] computeCentroid(Set<GridPosition> positions) {
        double[] sum = new double[3];
        for (GridPosition pos : positions) {
            double[] c = GridPositionCoordinates.getCoordinates(pos);
            sum[0] += c[0];
            sum[1] += c[1];
            sum[2] += c[2];
        }
        int n = positions.size();
        return new double[]{sum[0] / n, sum[1] / n, sum[2] / n};
    }

    private static double euclideanDistance(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
