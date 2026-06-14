package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.SpatialStatsResult;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class SpatialStatsService {

    public SpatialStatsResult calculateSpatialStats(Collection<ThermoMeasurementSeries> seriesList) {
        if (seriesList == null || seriesList.isEmpty()) {
            return emptyResult();
        }

        // Podział serii na poziomy fizyczne komory: GÓRA (TOP_*) i DÓŁ (BOTTOM_*)
        List<ThermoMeasurementSeries> topSeries = new ArrayList<>();
        List<ThermoMeasurementSeries> bottomSeries = new ArrayList<>();

        for (ThermoMeasurementSeries series : seriesList) {
            RevalidationSession.GridPosition pos = series.getGridPosition();
            if (pos == null) continue;
            if (pos.name().startsWith("TOP_")) {
                topSeries.add(series);
            } else if (pos.name().startsWith("BOTTOM_")) {
                bottomSeries.add(series);
            }
        }

        // Obliczenia globalne (wszystkie czujniki razem)
        LevelStats global = computeLevelStats(seriesList);

        // Obliczenia per poziom
        LevelStats top    = computeLevelStats(topSeries);
        LevelStats bottom = computeLevelStats(bottomSeries);

        // Gradient pionowy: Avg(TOP) − Avg(BOTTOM) w każdym timestamp
        Map<LocalDateTime, Double> verticalGradientOverTime = computeVerticalGradient(
                buildAvgByTimestamp(topSeries),
                buildAvgByTimestamp(bottomSeries));

        double meanVertical = 0.0;
        double maxVertical  = 0.0;
        if (!verticalGradientOverTime.isEmpty()) {
            double sumV = 0.0;
            double maxV = Double.NEGATIVE_INFINITY;
            for (double v : verticalGradientOverTime.values()) {
                double absV = Math.abs(v);
                sumV += absV;
                if (absV > maxV) maxV = absV;
            }
            meanVertical = sumV / verticalGradientOverTime.size();
            maxVertical  = maxV == Double.NEGATIVE_INFINITY ? 0.0 : maxV;
        }

        return SpatialStatsResult.builder()
                // Global
                .meanSpatialRange(global.mean)
                .maxSpatialRange(global.max)
                .spatialRangesOverTime(global.rangesOverTime)
                // TOP
                .meanRangeTop(top.mean)
                .maxRangeTop(top.max)
                .spatialRangesOverTimeTop(top.rangesOverTime)
                // BOTTOM
                .meanRangeBottom(bottom.mean)
                .maxRangeBottom(bottom.max)
                .spatialRangesOverTimeBottom(bottom.rangesOverTime)
                // Gradient pionowy
                .meanVerticalGradient(meanVertical)
                .maxVerticalGradient(maxVertical)
                .verticalGradientOverTime(verticalGradientOverTime)
                .build();
    }

    // -----------------------------------------------------------------------
    // Metody pomocnicze
    // -----------------------------------------------------------------------

    /**
     * Oblicza statystyki rozstępu przestrzennego dla podanej kolekcji serii
     * (identyczna logika co dawna metoda, wyodrębniona do wielokrotnego użycia).
     */
    private LevelStats computeLevelStats(Collection<ThermoMeasurementSeries> seriesList) {
        if (seriesList == null || seriesList.isEmpty()) {
            return new LevelStats(0.0, 0.0, Collections.emptyMap());
        }

        // Grupowanie temperatur wg timestamp
        Map<LocalDateTime, List<Double>> tempsByTimestamp = new TreeMap<>();
        for (ThermoMeasurementSeries series : seriesList) {
            if (series.getMeasurements() == null) continue;
            for (ThermoMeasurementPoint point : series.getMeasurements()) {
                tempsByTimestamp
                    .computeIfAbsent(point.getTimestampLocal(), k -> new ArrayList<>())
                    .add(point.getRawCelsius());
            }
        }

        if (tempsByTimestamp.isEmpty()) {
            return new LevelStats(0.0, 0.0, Collections.emptyMap());
        }

        Map<LocalDateTime, Double> rangesOverTime = new TreeMap<>();
        double maxRange = Double.NEGATIVE_INFINITY;
        double sumRange = 0.0;
        int count = 0;

        for (Map.Entry<LocalDateTime, List<Double>> entry : tempsByTimestamp.entrySet()) {
            List<Double> temps = entry.getValue();
            if (temps.size() >= 2) {
                double min = temps.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                double max = temps.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                double range = max - min;
                rangesOverTime.put(entry.getKey(), range);
                if (range > maxRange) maxRange = range;
                sumRange += range;
                count++;
            }
        }

        double meanRange = count > 0 ? sumRange / count : 0.0;
        if (maxRange == Double.NEGATIVE_INFINITY) maxRange = 0.0;

        return new LevelStats(meanRange, maxRange, rangesOverTime);
    }

    /**
     * Buduje mapę timestamp → średnia temperatura dla danej kolekcji serii.
     * Używana do obliczenia gradientu pionowego.
     */
    private Map<LocalDateTime, Double> buildAvgByTimestamp(Collection<ThermoMeasurementSeries> seriesList) {
        if (seriesList == null || seriesList.isEmpty()) return Collections.emptyMap();

        Map<LocalDateTime, List<Double>> raw = new TreeMap<>();
        for (ThermoMeasurementSeries series : seriesList) {
            if (series.getMeasurements() == null) continue;
            for (ThermoMeasurementPoint point : series.getMeasurements()) {
                raw.computeIfAbsent(point.getTimestampLocal(), k -> new ArrayList<>())
                   .add(point.getRawCelsius());
            }
        }

        Map<LocalDateTime, Double> avgMap = new TreeMap<>();
        for (Map.Entry<LocalDateTime, List<Double>> e : raw.entrySet()) {
            double avg = e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            avgMap.put(e.getKey(), avg);
        }
        return avgMap;
    }

    /**
     * Oblicza gradient pionowy: Avg(TOP) − Avg(BOTTOM) w każdym wspólnym timestamp.
     * Wynik może być ujemny (gdy dół jest cieplejszy niż góra).
     */
    private Map<LocalDateTime, Double> computeVerticalGradient(
            Map<LocalDateTime, Double> avgTop,
            Map<LocalDateTime, Double> avgBottom) {

        if (avgTop.isEmpty() || avgBottom.isEmpty()) return Collections.emptyMap();

        Map<LocalDateTime, Double> gradient = new TreeMap<>();
        for (LocalDateTime ts : avgTop.keySet()) {
            if (avgBottom.containsKey(ts)) {
                gradient.put(ts, avgTop.get(ts) - avgBottom.get(ts));
            }
        }
        return gradient;
    }

    private SpatialStatsResult emptyResult() {
        return SpatialStatsResult.builder()
                .spatialRangesOverTime(Collections.emptyMap())
                .spatialRangesOverTimeTop(Collections.emptyMap())
                .spatialRangesOverTimeBottom(Collections.emptyMap())
                .verticalGradientOverTime(Collections.emptyMap())
                .build();
    }

    /** Wewnętrzny kontener wyników dla jednego poziomu. */
    private record LevelStats(double mean, double max, Map<LocalDateTime, Double> rangesOverTime) {}
}

