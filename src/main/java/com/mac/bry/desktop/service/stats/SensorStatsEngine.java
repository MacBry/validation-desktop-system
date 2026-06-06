package com.mac.bry.desktop.service.stats;

import java.util.Arrays;

public class SensorStatsEngine {

    public static double calculateMean(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Array cannot be empty");
        }
        double sum = 0.0;
        for (double val : values) {
            sum += val;
        }
        return sum / values.length;
    }

    public static double calculateMedian(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Array cannot be empty");
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        if (n % 2 == 0) {
            return (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
        } else {
            return sorted[n / 2];
        }
    }

    public static double calculateVariance(double[] values) {
        if (values == null || values.length < 2) {
            throw new IllegalArgumentException("Variance requires at least two points");
        }
        double mean = calculateMean(values);
        double temp = 0;
        for (double a : values) {
            temp += (a - mean) * (a - mean);
        }
        return temp / (values.length - 1);
    }

    public static double calculateStdDev(double[] values) {
        return Math.sqrt(calculateVariance(values));
    }

    public static double calculateRsd(double[] values) {
        double mean = calculateMean(values);
        if (mean == 0.0) {
            return 0.0; // avoid division by zero
        }
        double stdDev = calculateStdDev(values);
        return (stdDev / mean) * 100.0;
    }

    public static double calculateSkewness(double[] values) {
        if (values == null || values.length < 3) {
            return 0.0; // requires at least 3 values
        }
        double mean = calculateMean(values);
        double stdDev = calculateStdDev(values);
        if (stdDev == 0.0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double val : values) {
            sum += Math.pow((val - mean) / stdDev, 3);
        }
        int n = values.length;
        // Adjusted Fisher-Pearson standardized moment coefficient
        return (double) n / ((n - 1) * (n - 2)) * sum;
    }

    public static double calculateKurtosis(double[] values) {
        if (values == null || values.length < 4) {
            return 0.0; // requires at least 4 values
        }
        double mean = calculateMean(values);
        double stdDev = calculateStdDev(values);
        if (stdDev == 0.0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double val : values) {
            sum += Math.pow((val - mean) / stdDev, 4);
        }
        int n = values.length;
        // Excess Kurtosis (unbiased estimator)
        double term1 = ((double) n * (n + 1)) / ((n - 1) * (n - 2) * (n - 3)) * sum;
        double term2 = (3.0 * Math.pow(n - 1, 2)) / ((n - 2) * (n - 3));
        return term1 - term2;
    }
}
