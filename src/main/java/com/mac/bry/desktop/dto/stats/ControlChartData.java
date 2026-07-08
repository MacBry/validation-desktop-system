package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * DTO zawierające komplet danych dla analizy SPC dwoma modelami:
 * 1. Klasyczną kartą Shewharta X-bar & S (dla podgrup n = 5).
 * 2. Kartą I-MR (Individual-Moving Range) dla pomiarów indywidualnych.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ControlChartData {
    // === Karta Shewharta X-bar & S (podgrupy n = 5) ===
    private List<Double> subgroupMeans;
    private List<Double> subgroupStdDevs;
    private double xBarCentralLine;
    private double xBarUcl;
    private double xBarLcl;
    private double sCentralLine;
    private double sUcl;
    private double sLcl;

    // === Karta I-MR (pomiary indywidualne) ===
    private List<Double> individualValues;
    private double iCentralLine;
    private double iUcl;
    private double iLcl;
    private List<Double> movingRanges;
    private double mrCentralLine;
    private double mrUcl;
    private double mrLcl;
}
