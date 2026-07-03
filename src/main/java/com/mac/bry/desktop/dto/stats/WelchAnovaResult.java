package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WelchAnovaResult {
    private boolean significantDifference;
    private double pValue;
    private double fValue;
    private double df1;
    private double df2;
}
