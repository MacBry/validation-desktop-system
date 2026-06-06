package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnovaResult {
    private boolean significantDifference;
    private double pValue;
    private double fValue;
    private int dfBetween;
    private int dfWithin;
}
