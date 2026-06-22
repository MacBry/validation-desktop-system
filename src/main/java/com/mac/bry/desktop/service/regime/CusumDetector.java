package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.config.RegimeDetectionProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detektor trwałych zmian poziomu średniej metodą CUSUM (Cumulative Sum).
 * <p>
 * Stosowany do wykrywania:
 * <ul>
 *   <li>Zmiany nastawy termostatu (SETPOINT_CHANGE)</li>
 *   <li>Włączenia trybu fastcooling (SETPOINT_CHANGE DOWN)</li>
 *   <li>Powrotu do normalnej pracy po fastcooling (SETPOINT_CHANGE UP)</li>
 * </ul>
 * <p>
 * Algorytm (Page 1954, klasyczny CUSUM dwustronny):
 * <pre>
 *   C⁺ᵢ = max(0, C⁺ᵢ₋₁ + (xᵢ - μ₀ - k))   [detekcja wzrostu]
 *   C⁻ᵢ = max(0, C⁻ᵢ₋₁ - (xᵢ - μ₀ + k))   [detekcja spadku]
 *   Alert gdy C⁺ > h lub C⁻ > h
 *   gdzie: k = K * sigma (allowance), h = H * sigma (próg decyzji)
 * </pre>
 * <p>
 * Deterministyczny — brak losowości. Parametry K i H konfiguracyjne.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CusumDetector {

    private final RegimeDetectionProperties props;

    /**
     * Wynik detekcji pojedynczego punktu zmiany.
     */
    @Getter
    public static class ChangePoint {
        private final int index;         // Indeks w tablicy values
        private final Direction direction; // UP = wzrost średniej, DOWN = spadek

        public ChangePoint(int index, Direction direction) {
            this.index = index;
            this.direction = direction;
        }

        @Override
        public String toString() {
            return "ChangePoint{idx=" + index + ", dir=" + direction + "}";
        }
    }

    public enum Direction { UP, DOWN }

    /**
     * Wykrywa punkty trwałej zmiany poziomu średniej w serii wartości.
     *
     * @param values         Tablica wartości temperatury [°C], posortowana chronologicznie.
     * @param referenceSigma Odchylenie standardowe w fazie referencyjnej (bazowej).
     *                       Używane do skalowania parametrów k i h.
     *                       Jeśli null lub ≤ 0, szacowane z pierwszych {@code baselinePoints} wartości.
     * @return Lista wykrytych change points posortowana rosnąco po indeksie.
     */
    public List<ChangePoint> detect(double[] values, Double referenceSigma) {
        if (values == null || values.length < 10) {
            return List.of();
        }

        int n = values.length;
        int baselinePoints = Math.min(props.getCusumBaselinePoints(), n / 4);

        // Wyznaczenie sigma referencyjnej
        double sigma = (referenceSigma != null && referenceSigma > 0)
                ? referenceSigma
                : estimateSigma(values, 0, baselinePoints);

        if (sigma < 1e-10) {
            log.debug("CusumDetector: sigma ≈ 0 — pomijam detekcję (stały sygnał)");
            return List.of();
        }

        double k = props.getCusumK() * sigma; // allowance
        double h = props.getCusumH() * sigma; // próg decyzji

        // Bieżąca wartość docelowa (target) — inicjalizacja z baseline
        double target = mean(values, 0, baselinePoints);

        List<ChangePoint> changePoints = new ArrayList<>();
        double cuSumPos = 0.0;
        double cuSumNeg = 0.0;

        for (int i = baselinePoints; i < n; i++) {
            double xi = values[i];
            cuSumPos = Math.max(0.0, cuSumPos + xi - target - k);
            cuSumNeg = Math.max(0.0, cuSumNeg - xi + target - k);

            if (cuSumPos > h) {
                log.debug("CusumDetector: zmiana w GÓRĘ wykryta przy indeksie {}, C⁺={:.2f}", i, cuSumPos);
                changePoints.add(new ChangePoint(i, Direction.UP));
                cuSumPos = 0.0;
                // Aktualizacja target po zmianie (nowy baseline)
                int newBaseEnd = Math.min(i + baselinePoints, n);
                target = mean(values, i, newBaseEnd);
                sigma  = estimateSigma(values, i, newBaseEnd);
                if (sigma < 1e-10) sigma = props.getCusumK(); // fallback
                k = props.getCusumK() * sigma;
                h = props.getCusumH() * sigma;
                cuSumNeg = 0.0;
            }

            if (cuSumNeg > h) {
                log.debug("CusumDetector: zmiana w DÓŁ wykryta przy indeksie {}, C⁻={:.2f}", i, cuSumNeg);
                changePoints.add(new ChangePoint(i, Direction.DOWN));
                cuSumNeg = 0.0;
                int newBaseEnd = Math.min(i + baselinePoints, n);
                target = mean(values, i, newBaseEnd);
                sigma  = estimateSigma(values, i, newBaseEnd);
                if (sigma < 1e-10) sigma = props.getCusumK();
                k = props.getCusumK() * sigma;
                h = props.getCusumH() * sigma;
                cuSumPos = 0.0;
            }
        }

        log.debug("CusumDetector: wykryto {} change point(s)", changePoints.size());
        return changePoints;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Średnia arytmetyczna na przedziale [from, to). */
    private double mean(double[] values, int from, int to) {
        if (from >= to) return 0.0;
        double sum = 0.0;
        for (int i = from; i < to; i++) sum += values[i];
        return sum / (to - from);
    }

    /** Odchylenie standardowe próbkowe na przedziale [from, to). */
    private double estimateSigma(double[] values, int from, int to) {
        if (to - from < 2) return 0.0;
        double mu = mean(values, from, to);
        double sumSq = 0.0;
        for (int i = from; i < to; i++) {
            double d = values[i] - mu;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / (to - from - 1));
    }
}
