package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * DTO zawierające dane dla karty kontrolnej I-MR (Individual-Moving Range)
 * służącej do oceny stabilności autokorelowanych pomiarów indywidualnych.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ControlChartData {
    // Karta I (Wartości Indywidualne)
    private List<Double> individualValues;
    private double iCentralLine;
    private double iUcl;
    private double iLcl;
    
    // Karta MR (Ruchomy Rozstęp)
    private List<Double> movingRanges;
    private double mrCentralLine;
    private double mrUcl;
    private double mrLcl;
}
