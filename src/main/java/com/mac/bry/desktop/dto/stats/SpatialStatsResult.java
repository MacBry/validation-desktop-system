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
    private double meanSpatialRange;
    private double maxSpatialRange;
    private Map<LocalDateTime, Double> spatialRangesOverTime;
}
