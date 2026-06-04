package com.mac.bry.desktop.service.hotspot;

import java.util.List;
import java.util.Optional;

public interface ExtremeDetectionStrategy {

    String methodCode();
    String displayName();
    boolean isHotspot();
    SensorStats.StatField field();

    default Optional<Verdict> apply(List<SensorStats> stats) {
        if (stats.isEmpty()) {
            throw new IllegalArgumentException("Cannot run detection on empty stats");
        }

        var meaningful = stats.stream()
                .filter(s -> !isDegenerate(s))
                .toList();

        if (meaningful.isEmpty()) {
            return Optional.empty();
        }

        var f = field();
        var winner = isHotspot()
                ? meaningful.stream().max((a, b) -> Double.compare(a.get(f), b.get(f)))
                : meaningful.stream().min((a, b) -> Double.compare(a.get(f), b.get(f)));

        var w = winner.orElseThrow();
        return Optional.of(new Verdict(methodCode(), displayName(),
                w.sensorId(), w.get(f), isHotspot()));
    }

    default boolean isDegenerate(SensorStats s) {
        return false;
    }

    record Verdict(
            String methodCode,
            String methodName,
            String winnerSensorId,
            double value,
            boolean isHotspot
    ) {}
}
