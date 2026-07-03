package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DunnResult {
    private int groupA;
    private int groupB;
    private double meanRankDifference;
    private double zValue;
    private double pValue;
    private double adjustedPValue;
    private boolean significant;
}
