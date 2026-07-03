package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamesHowellResult {
    private int groupA;
    private int groupB;
    private double meanDifference;
    private double standardError;
    private double df;
    private double tValue;
    private double qValue;
    private double pValue;
    private boolean significant;
}
