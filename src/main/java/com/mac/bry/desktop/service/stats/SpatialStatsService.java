package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.SpatialStatsResult;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class SpatialStatsService {

    public SpatialStatsResult calculateSpatialStats(Collection<ThermoMeasurementSeries> seriesList) {
        if (seriesList == null || seriesList.isEmpty()) {
            return new SpatialStatsResult(0.0, 0.0, Collections.emptyMap());
        }

        // Grupuj temperatury wg timestamp
        Map<LocalDateTime, List<Double>> tempsByTimestamp = new TreeMap<>();
        for (ThermoMeasurementSeries series : seriesList) {
            if (series.getMeasurements() != null) {
                for (ThermoMeasurementPoint point : series.getMeasurements()) {
                    tempsByTimestamp
                        .computeIfAbsent(point.getTimestampLocal(), k -> new ArrayList<>())
                        .add(point.getRawCelsius());
                }
            }
        }

        if (tempsByTimestamp.isEmpty()) {
            return new SpatialStatsResult(0.0, 0.0, Collections.emptyMap());
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

                if (range > maxRange) {
                    maxRange = range;
                }
                sumRange += range;
                count++;
            }
        }

        double meanRange = count > 0 ? sumRange / count : 0.0;
        if (maxRange == Double.NEGATIVE_INFINITY) {
            maxRange = 0.0;
        }

        return new SpatialStatsResult(meanRange, maxRange, rangesOverTime);
    }
}
