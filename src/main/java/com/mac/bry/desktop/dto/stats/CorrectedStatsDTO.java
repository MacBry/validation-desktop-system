package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO zawierające statystyki obliczone na wartościach skorygowanych przez świadectwo wzorcowania.
 * Korekta stosuje interpolację liniową błędu systematycznego między punktami CalibrationPoint (GUM §4.3).
 *
 * <p>Wartości tego DTO są obliczane on-the-fly podczas generowania raportu PDF.
 * Model danych (ThermoMeasurementPoint) pozostaje niezmieniony — rawCelsius nigdy nie jest nadpisywane.</p>
 *
 * <p>Jeśli {@code hasCalibrationData == false} (brak świadectwa lub punktów wzorcowania),
 * wszystkie pola liczbowe mają wartość 0.0 i nie powinny być prezentowane w raporcie.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrectedStatsDTO {

    /** Oznaczenie pozycji (np. "Góra - Przód-Lewy") */
    private String positionName;

    /** Numer seryjny rejestratora */
    private String recorderSerialNumber;

    /**
     * Czy dostępne są dane wzorcowania (świadectwo z punktami CalibrationPoint).
     * Jeśli false — pozostałe pola są niezainicjowane (0.0) i nie należy ich wyświetlać.
     */
    private boolean hasCalibrationData;

    // --- Statystyki opisowe (na corrected[]) ---

    /** Minimalna wartość temperatury po korekcji [°C] */
    private double minCorrected;

    /** Maksymalna wartość temperatury po korekcji [°C] */
    private double maxCorrected;

    /** Średnia arytmetyczna po korekcji [°C] */
    private double avgCorrected;

    /** Mediana po korekcji [°C] */
    private double medianCorrected;

    /** Odchylenie standardowe próbkowe (n-1, Bessel) po korekcji [°C] */
    private double stdDevCorrected;

    // --- SPC (na corrected[] vs LSL/USL komory) ---

    /** Wskaźnik potencjalnej zdolności procesu Cp (null jeśli brak LSL/USL) */
    private Double cpCorrected;

    /** Wskaźnik rzeczywistej zdolności procesu Cpk (null jeśli brak LSL/USL) */
    private Double cpkCorrected;

    // --- Budżet niepewności (uA z stdDevCorrected, uB1+uB2 jak w surowej) ---

    /** Rozszerzona niepewność pomiarowa U* = 2 * sqrt(uA*² + uB1² + uB2²) [°C] */
    private double expandedUncertaintyCorrected;

    /**
     * Przesunięcie systematyczne: avg_corrected - avg_raw [°C].
     * Wartość dodatnia oznacza, że korekcja podnosi średnią.
     */
    private double correctionBias;
}
