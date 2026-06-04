package com.mac.bry.desktop.service.hotspot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsensusDetectionService {

    private static final List<String> METHOD_PRIORITY = List.of(
            "TOL_HI_HOTSPOT", "TOL_LO_COLDSPOT",
            "ABS_MAX_HOTSPOT", "ABS_MIN_COLDSPOT",
            "MKT_HOTSPOT",
            "P99_HOTSPOT", "P01_COLDSPOT",
            "MEAN_HOTSPOT", "MEAN_COLDSPOT"
    );

    private final List<ExtremeDetectionStrategy> hotspotStrategies;
    private final List<ExtremeDetectionStrategy> coldspotStrategies;

    public ConsensusDetectionService(List<ExtremeDetectionStrategy> strategies) {
        this.hotspotStrategies = strategies.stream()
                .filter(ExtremeDetectionStrategy::isHotspot).toList();
        this.coldspotStrategies = strategies.stream()
                .filter(s -> !s.isHotspot()).toList();
    }

    public ConsensusReport detectHotspot(List<SensorStats> stats) {
        return detect(stats, hotspotStrategies, true);
    }

    public ConsensusReport detectColdspot(List<SensorStats> stats) {
        return detect(stats, coldspotStrategies, false);
    }

    private ConsensusReport detect(
            List<SensorStats> stats,
            List<ExtremeDetectionStrategy> strategies,
            boolean isHotspot) {

        var verdicts = strategies.stream()
                .map(s -> s.apply(stats))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        if (verdicts.isEmpty()) {
            return new ConsensusReport(isHotspot, null, 0.0, Map.of(), List.of());
        }

        var votes = new HashMap<String, Integer>();
        for (var v : verdicts) {
            votes.merge(v.winnerSensorId(), 1, Integer::sum);
        }

        int maxVotes = votes.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        var candidates = votes.entrySet().stream()
                .filter(e -> e.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .toList();

        String winner = candidates.size() == 1
                ? candidates.get(0)
                : tieBreakByMethodPriority(candidates, verdicts);

        double strength = (double) maxVotes / verdicts.size();

        return new ConsensusReport(
                isHotspot,
                winner,
                strength,
                Map.copyOf(votes),
                verdicts
        );
    }

    private static String tieBreakByMethodPriority(
            List<String> candidates,
            List<ExtremeDetectionStrategy.Verdict> verdicts) {

        for (String method : METHOD_PRIORITY) {
            var pick = verdicts.stream()
                    .filter(v -> v.methodCode().equals(method))
                    .filter(v -> candidates.contains(v.winnerSensorId()))
                    .findFirst();
            if (pick.isPresent()) {
                return pick.get().winnerSensorId();
            }
        }
        return candidates.get(0);
    }

    public record ConsensusReport(
            boolean isHotspot,
            String consensusSensorId,
            double consensusStrength,
            Map<String, Integer> votesByCandidate,
            List<ExtremeDetectionStrategy.Verdict> allVerdicts
    ) {
        public boolean hasDetection() { return consensusSensorId != null; }
        public boolean isUnanimous()  { return consensusStrength == 1.0; }
        public boolean isWeak()       { return consensusStrength < 0.5; }
    }
}
