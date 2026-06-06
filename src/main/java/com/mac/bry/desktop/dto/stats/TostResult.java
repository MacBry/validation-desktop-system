package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TostResult {
    private boolean equivalent;
    private double pValue1;
    private double pValue2;
    private double theta;
    private double meanDifference;
}
