package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpatialStatsResult {
    private double meanSpatialSpread;
    private double maxSpatialSpread;
    private Map<LocalDateTime, Double> spatialSpreadsOverTime;
}
