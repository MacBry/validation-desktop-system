package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.Calibration;
import com.mac.bry.desktop.model.CalibrationPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Serwis korekcji wartości pomiarowych o błąd systematyczny wynikający ze świadectwa wzorcowania.
 *
 * <p>Stosuje <b>interpolację liniową</b> między punktami wzorcowania (CalibrationPoint):
 * <pre>
 *   δ(T) = δ_i + (δ_{i+1} - δ_i) * (T - T_i) / (T_{i+1} - T_i)
 *   T_corrected = T_raw + δ(T_raw)
 * </pre>
 *
 * <p>Dla wartości poza zakresem punktów wzorcowania stosuje <b>ekstrapolację płaską</b>
 * (skrajny błąd systematyczny — bez przedłużania trendu regresji), co jest podejściem
 * konserwatywnym, zgodnym z GUM §4.3 i praktyką metrologiczną.
 *
 * <p>Jeśli świadectwo wzorcowania jest {@code null} lub nie zawiera punktów —
 * wartości są zwracane bez modyfikacji (pass-through) z logiem ostrzegawczym.
 *
 * @see com.mac.bry.desktop.model.CalibrationPoint
 */
@Service
@Slf4j
public class CalibrationCorrectionService {

    /**
     * Koryguje pojedynczą wartość temperatury o interpolowany błąd systematyczny wzorcowania.
     *
     * @param rawCelsius  Surowa wartość temperatury z rejestratora [°C]
     * @param calibration Świadectwo wzorcowania z listą punktów (może być {@code null})
     * @return Wartość skorygowana: {@code rawCelsius + δ(rawCelsius)} [°C]
     */
    public double correctValue(double rawCelsius, Calibration calibration) {
        if (calibration == null) {
            log.debug("correctValue: calibration is null — returning raw value {}", rawCelsius);
            return rawCelsius;
        }

        List<CalibrationPoint> points = calibration.getPoints();
        if (points == null || points.isEmpty()) {
            log.warn("correctValue: calibration {} has no CalibrationPoints — returning raw value",
                    calibration.getCertificateNumber());
            return rawCelsius;
        }

        // Posortuj punkty rosnąco wg temperatureValue
        List<CalibrationPoint> sorted = points.stream()
                .sorted(Comparator.comparingDouble(p -> p.getTemperatureValue().doubleValue()))
                .toList();

        double error = interpolateError(rawCelsius, sorted);
        return rawCelsius + error;
    }

    /**
     * Wektorowa korekcja całej tablicy pomiarów.
     *
     * @param rawValues   Tablica surowych wartości temperatury [°C]
     * @param calibration Świadectwo wzorcowania
     * @return Nowa tablica skorygowanych wartości (tej samej długości)
     */
    public double[] correctValues(double[] rawValues, Calibration calibration) {
        if (rawValues == null) return new double[0];

        double[] corrected = new double[rawValues.length];
        for (int i = 0; i < rawValues.length; i++) {
            corrected[i] = correctValue(rawValues[i], calibration);
        }
        return corrected;
    }

    /**
     * Sprawdza czy kalibracja zawiera wystarczające dane do korekcji.
     *
     * @param calibration Świadectwo wzorcowania
     * @return true jeśli kalibracja ma co najmniej 1 punkt wzorcowania
     */
    public boolean hasCalibrationData(Calibration calibration) {
        return calibration != null
                && calibration.getPoints() != null
                && !calibration.getPoints().isEmpty();
    }

    // --- Private ---

    /**
     * Interpoluje błąd systematyczny δ(T) dla podanej temperatury T.
     * Punkty muszą być posortowane rosnąco wg temperatureValue.
     *
     * @param temp   Temperatura, dla której obliczamy błąd [°C]
     * @param sorted Posortowana lista punktów wzorcowania
     * @return Interpolowany błąd systematyczny δ [°C]
     */
    private double interpolateError(double temp, List<CalibrationPoint> sorted) {
        int n = sorted.size();

        // Tylko jeden punkt — stały offset
        if (n == 1) {
            return sorted.get(0).getSystematicError().doubleValue();
        }

        double t0 = sorted.get(0).getTemperatureValue().doubleValue();
        double tN = sorted.get(n - 1).getTemperatureValue().doubleValue();

        // Ekstrapolacja płaska poniżej zakresu
        if (temp <= t0) {
            return sorted.get(0).getSystematicError().doubleValue();
        }

        // Ekstrapolacja płaska powyżej zakresu
        if (temp >= tN) {
            return sorted.get(n - 1).getSystematicError().doubleValue();
        }

        // Szukaj przedziału [T_i, T_{i+1}] zawierającego temp
        for (int i = 0; i < n - 1; i++) {
            double ti    = sorted.get(i).getTemperatureValue().doubleValue();
            double tiP1  = sorted.get(i + 1).getTemperatureValue().doubleValue();
            double di    = sorted.get(i).getSystematicError().doubleValue();
            double diP1  = sorted.get(i + 1).getSystematicError().doubleValue();

            if (temp >= ti && temp <= tiP1) {
                if (Math.abs(tiP1 - ti) < 1e-12) {
                    // Dwa punkty o identycznej temperaturze — uśrednij błąd
                    return (di + diP1) / 2.0;
                }
                // Interpolacja liniowa: δ(T) = δ_i + (δ_{i+1} - δ_i) * (T - T_i) / (T_{i+1} - T_i)
                return di + (diP1 - di) * (temp - ti) / (tiP1 - ti);
            }
        }

        // Fallback (nie powinien wystąpić po sprawdzeniach zakresu)
        log.warn("interpolateError: unexpected fallback for temp={}", temp);
        return 0.0;
    }
}
