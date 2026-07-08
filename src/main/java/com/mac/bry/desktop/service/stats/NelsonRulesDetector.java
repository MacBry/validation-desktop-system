package com.mac.bry.desktop.service.stats;

import com.mac.bry.desktop.dto.stats.ControlChartData;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * Detektor naruszeń stabilności procesu na podstawie reguł Nelsona (Nelson Rules)
 * dla kart kontrolnych I-MR (Individual-Moving Range).
 */
public class NelsonRulesDetector {

    @Value
    public static class Violation {
        int subgroupIndex; // Indeks punktu pomiarowego (1-indexed)
        int ruleNumber;    // Numer reguły (1, 2, 3 lub 4)
        String description; // Szczegółowy opis naruszenia
        boolean isSChart;  // Czy dotyczy karty MR (true) czy I (false)
    }

    /**
     * Analizuje kartę I (wartości indywidualne) pod kątem 4 podstawowych reguł Nelsona.
     */
    public static List<Violation> detectXBarViolations(ControlChartData data) {
        List<Violation> violations = new ArrayList<>();
        List<Double> values = data.getIndividualValues();
        if (values == null || values.isEmpty()) {
            return violations;
        }

        double cl = data.getICentralLine();
        double ucl = data.getIUcl();
        double lcl = data.getILcl();

        for (int i = 0; i < values.size(); i++) {
            double val = values.get(i);

            // Reguła 1: Jeden punkt poza granicami 3-sigma (LCL/UCL)
            if (val > ucl || val < lcl) {
                violations.add(new Violation(
                        i + 1,
                        1,
                        String.format("Reguła 1: Punkt poza granicami 3-sigma: %.3f°C (UCL=%.3f, LCL=%.3f)", val, ucl, lcl),
                        false
                ));
            }

            // Reguła 2: Dziewięć (lub więcej) kolejnych punktów po tej samej stronie linii centralnej
            if (i >= 8) {
                boolean allAbove = true;
                boolean allBelow = true;
                for (int j = i - 8; j <= i; j++) {
                    double v = values.get(j);
                    if (v <= cl) {
                        allAbove = false;
                    }
                    if (v >= cl) {
                        allBelow = false;
                    }
                }
                if (allAbove || allBelow) {
                    violations.add(new Violation(
                            i + 1,
                            2,
                            String.format("Reguła 2: 9 kolejnych punktów z rzędu po tej samej stronie linii centralnej (CL=%.3f°C)", cl),
                            false
                    ));
                }
            }

            // Reguła 3: Sześć (lub więcej) kolejnych punktów stale rosnących lub stale malejących
            if (i >= 5) {
                boolean increasing = true;
                boolean decreasing = true;
                for (int j = i - 4; j <= i; j++) {
                    double curr = values.get(j);
                    double prev = values.get(j - 1);
                    if (curr <= prev) {
                        increasing = false;
                    }
                    if (curr >= prev) {
                        decreasing = false;
                    }
                }
                if (increasing || decreasing) {
                    violations.add(new Violation(
                            i + 1,
                            3,
                            String.format("Reguła 3: 6 kolejnych punktów w stałym trendzie %s", increasing ? "rosnącym" : "malejącym"),
                            false
                    ));
                }
            }

            // Reguła 4: Czternaście kolejnych punktów naprzemiennie rosnących i malejących (oscylacja)
            if (i >= 13) {
                boolean alternating = true;
                for (int j = i - 11; j <= i; j++) {
                    double diffPrev = values.get(j - 1) - values.get(j - 2);
                    double diffCurr = values.get(j) - values.get(j - 1);
                    if (diffPrev * diffCurr >= 0) {
                        alternating = false;
                        break;
                    }
                }
                if (alternating) {
                    violations.add(new Violation(
                            i + 1,
                            4,
                            "Reguła 4: 14 kolejnych punktów naprzemiennie rosnących i malejących (niestabilność / oscylacja)",
                            false
                    ));
                }
            }
        }

        return violations;
    }

    /**
     * Analizuje kartę MR (ruchomego rozstępu) pod kątem przekroczeń granic kontrolnych.
     */
    public static List<Violation> detectSViolations(ControlChartData data) {
        List<Violation> violations = new ArrayList<>();
        List<Double> mrValues = data.getMovingRanges();
        if (mrValues == null || mrValues.isEmpty()) {
            return violations;
        }

        double ucl = data.getMrUcl();
        double lcl = data.getMrLcl();

        for (int i = 0; i < mrValues.size(); i++) {
            double val = mrValues.get(i);
            if (val > ucl || val < lcl) {
                violations.add(new Violation(
                        i + 2, // Indeks pierwszego pomiaru z pary dla MR to i + 2 (1-indexed, MR_1 wyliczane z punktu 1 i 2)
                        1,
                        String.format("Reguła 1 (Karta MR): Ruchomy rozstęp %.3f°C poza granicami kontrolnymi (UCL=%.3f, LCL=%.3f)", val, ucl, lcl),
                        true
                ));
            }
        }

        return violations;
    }
}
