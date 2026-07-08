package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.ControlChartData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Kalkulator kart kontrolnych obsługujący oba modele SPC:
 * 1. Klasyczne karty Shewharta X-bar & S (dla podgrup n = 5).
 * 2. Karty I-MR (Individual-Moving Range) dla pomiarów indywidualnych.
 */
public class ControlChartCalculator {

    // Czynniki dla kart X-bar & S (n = 5)
    private static final double A3 = 1.427;
    private static final double B3 = 0.0;
    private static final double B4 = 2.089;
    private static final int SUBGROUP_SIZE = 5;

    // Czynniki dla kart I-MR (n = 2)
    private static final double D2 = 1.128;
    private static final double D4 = 3.268;

    public static ControlChartData calculateShewhartLimits(double[] values) {
        if (values == null || values.length < 2) {
            return new ControlChartData(
                    List.of(), List.of(), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    List.of(), 0.0, 0.0, 0.0, List.of(), 0.0, 0.0, 0.0
            );
        }

        // ==========================================
        // 1. Obliczenia dla karty Shewharta X-bar & S
        // ==========================================
        List<Double> subgroupMeans = new ArrayList<>();
        List<Double> subgroupStdDevs = new ArrayList<>();
        double xBarCL = 0.0;
        double sCL = 0.0;
        double xBarUcl = 0.0;
        double xBarLcl = 0.0;
        double sUcl = 0.0;
        double sLcl = 0.0;

        int n = values.length;
        int m = n / SUBGROUP_SIZE; // liczba kompletnych podgrup

        if (m >= 1) {
            double sumGrandMean = 0.0;
            double sumGrandStdDev = 0.0;

            for (int i = 0; i < m; i++) {
                double[] subgroup = new double[SUBGROUP_SIZE];
                System.arraycopy(values, i * SUBGROUP_SIZE, subgroup, 0, SUBGROUP_SIZE);

                double mean = SensorStatsEngine.calculateMean(subgroup);
                double stdDev = SensorStatsEngine.calculateStdDev(subgroup);

                subgroupMeans.add(mean);
                subgroupStdDevs.add(stdDev);

                sumGrandMean += mean;
                sumGrandStdDev += stdDev;
            }

            xBarCL = sumGrandMean / m;
            sCL = sumGrandStdDev / m;

            xBarUcl = xBarCL + A3 * sCL;
            xBarLcl = xBarCL - A3 * sCL;
            sUcl = B4 * sCL;
            sLcl = B3 * sCL;
        }

        // ==========================================
        // 2. Obliczenia dla karty I-MR
        // ==========================================
        List<Double> individualValues = Arrays.stream(values).boxed().toList();
        List<Double> movingRanges = new ArrayList<>();
        double sumMR = 0.0;
        for (int i = 1; i < n; i++) {
            double mr = Math.abs(values[i] - values[i - 1]);
            movingRanges.add(mr);
            sumMR += mr;
        }

        double sumVal = Arrays.stream(values).sum();
        double iCentralLine = sumVal / n;
        double mrCentralLine = sumMR / (n - 1);

        double sigmaEst = mrCentralLine / D2;
        double iUcl = iCentralLine + 3.0 * sigmaEst;
        double iLcl = iCentralLine - 3.0 * sigmaEst;
        double mrUcl = D4 * mrCentralLine;
        double mrLcl = 0.0;

        return new ControlChartData(
                subgroupMeans, subgroupStdDevs, xBarCL, xBarUcl, xBarLcl, sCL, sUcl, sLcl,
                individualValues, iCentralLine, iUcl, iLcl, movingRanges, mrCentralLine, mrUcl, mrLcl
        );
    }
}
