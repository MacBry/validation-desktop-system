package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.ControlChartData;
import java.util.ArrayList;
import java.util.List;

public class ControlChartCalculator {

    // Współczynniki dla kart kontrolnych Shewharta (dla podgrup o stałym rozmiarze n = 5)
    private static final double A3 = 1.427;
    private static final double B3 = 0.0;
    private static final double B4 = 2.089;
    private static final int SUBGROUP_SIZE = 5;

    public static ControlChartData calculateShewhartLimits(double[] values) {
        if (values == null || values.length < SUBGROUP_SIZE) {
            return new ControlChartData(
                    List.of(), List.of(),
                    0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0
            );
        }

        int n = values.length;
        int m = n / SUBGROUP_SIZE; // Liczba kompletnych podgrup

        List<Double> subgroupMeans = new ArrayList<>();
        List<Double> subgroupStdDevs = new ArrayList<>();

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

        double xBarCL = sumGrandMean / m;
        double sCL = sumGrandStdDev / m;

        // Limity dla karty X-bar (średnich)
        double xBarUcl = xBarCL + A3 * sCL;
        double xBarLcl = xBarCL - A3 * sCL;

        // Limity dla karty S (odchyleń standardowych)
        double sUcl = B4 * sCL;
        double sLcl = B3 * sCL;

        return new ControlChartData(
                subgroupMeans, subgroupStdDevs,
                xBarCL, xBarUcl, xBarLcl,
                sCL, sUcl, sLcl
        );
    }
}
