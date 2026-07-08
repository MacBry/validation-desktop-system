package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.ControlChartData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Kalkulator kart kontrolnych I-MR (Individual-Moving Range)
 * służących do analizy stabilności procesów temperaturowych
 * bez zniekształceń autokorelacyjnych.
 */
public class ControlChartCalculator {

    private static final double D2 = 1.128; // d2 dla n = 2 (sąsiednie pomiary)
    private static final double D4 = 3.268; // D4 dla n = 2 (granica UCL ruchomego rozstępu)

    public static ControlChartData calculateShewhartLimits(double[] values) {
        if (values == null || values.length < 2) {
            return new ControlChartData(
                    List.of(), 0.0, 0.0, 0.0,
                    List.of(), 0.0, 0.0, 0.0
            );
        }

        int n = values.length;
        List<Double> individualValues = Arrays.stream(values).boxed().toList();

        // 1. Obliczenie wartości ruchomego rozstępu (Moving Range)
        List<Double> movingRanges = new ArrayList<>();
        double sumMR = 0.0;
        for (int i = 1; i < n; i++) {
            double mr = Math.abs(values[i] - values[i - 1]);
            movingRanges.add(mr);
            sumMR += mr;
        }

        // 2. Obliczenie linii centralnych (Central Line)
        double sumVal = Arrays.stream(values).sum();
        double iCentralLine = sumVal / n;
        double mrCentralLine = sumMR / (n - 1);

        // 3. Estymacja odchylenia standardowego i granic kontrolnych
        // sigma_est = average_MR / d2
        double sigmaEst = mrCentralLine / D2;

        // Granice dla karty wartości indywidualnych (I)
        double iUcl = iCentralLine + 3.0 * sigmaEst;
        double iLcl = iCentralLine - 3.0 * sigmaEst;

        // Granice dla karty ruchomego rozstępu (MR)
        double mrUcl = D4 * mrCentralLine;
        double mrLcl = 0.0; // D3 dla n = 2 wynosi 0.0

        return new ControlChartData(
                individualValues, iCentralLine, iUcl, iLcl,
                movingRanges, mrCentralLine, mrUcl, mrLcl
        );
    }
}
