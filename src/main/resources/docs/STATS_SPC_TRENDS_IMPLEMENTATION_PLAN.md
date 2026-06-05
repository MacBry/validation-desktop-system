# Plan Implementacji - Statystyczna Kontrola Procesu (SPC) i Analiza Trendów

Plan techniczny wdrożenia silnika obliczania wskaźników zdolności procesu oraz generowania danych dla kart kontrolnych Shewharta.

## 1. Architektura Obliczeń
Wszystkie algorytmy SPC zostaną zaimplementowane w dedykowanej klasie narzędziowej. Dane wejściowe to surowy ciąg odczytów temperatury wraz z limitami specyfikacji (LSL, USL).

## 2. Klasy i Logika Biznesowa

### [NEW] [SpcEngine.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/stats/SpcEngine.java)
Klasa wykonująca obliczenia wskaźników zdolności:

```java
package com.mac.bry.desktop.service.stats;

public class SpcEngine {

    public static CapabilityIndexes calculateCapability(double[] values, double lsl, double usl) {
        if (values == null || values.length < 2) {
            return new CapabilityIndexes(0.0, 0.0);
        }

        // Obliczenie średniej
        double sum = 0.0;
        for (double val : values) {
            sum += val;
        }
        double mean = sum / values.length;

        // Obliczenie odchylenia standardowego (próba)
        double sumSqDiff = 0.0;
        for (double val : values) {
            sumSqDiff += Math.pow(val - mean, 2);
        }
        double stdDev = Math.sqrt(sumSqDiff / (values.length - 1));

        if (stdDev == 0.0) {
            return new CapabilityIndexes(99.9, 99.9); // Uniknięcie dzielenia przez 0 przy stałej linii
        }

        // Obliczenie Cp
        double cp = (usl - lsl) / (6 * stdDev);

        // Obliczenie Cpk
        double cpu = (usl - mean) / (3 * stdDev);
        double cpl = (mean - lsl) / (3 * stdDev);
        double cpk = Math.min(cpu, cpl);

        return new CapabilityIndexes(cp, cpk);
    }
}
```

### [NEW] [CapabilityIndexes.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/dto/stats/CapabilityIndexes.java)
Rekord lub klasa DTO:
```java
public record CapabilityIndexes(double cp, double cpk) {
    public boolean isHighlyCapable() {
        return cpk >= 1.33;
    }
    
    public boolean isAcceptable() {
        return cpk >= 1.0;
    }
}
```

### [NEW] [ControlChartCalculator.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/stats/ControlChartCalculator.java)
Dzieli dane pomiarowe na podgrupy (np. 12-godzinne) i wylicza średnią, odchylenie standardowe oraz limity LCL i UCL dla każdej z nich w celu narysowania wykresów JavaFX LineChart.

## 3. Integracja z UI (JavaFX)
W widoku raportu z rewalidacji dodana zostanie nowa zakładka **"Analiza SPC"** zawierająca:
*   Wykres typu `LineChart` prezentujący rozkład średnich podgrup wraz z liniami UCL ($+3\sigma$) oraz LCL ($-3\sigma$).
*   Klocki KPI z wartościami wskaźników $C_p$ i $C_{pk}$ wyróżnione kolorem zielonym ($C_{pk} \ge 1.33$), żółtym ($1.0 - 1.33$) lub czerwonym ($< 1.0$).

## 4. Plan Testów (Verification Plan)
*   **Testy jednostkowe (`SpcEngineTest`):**
    *   Weryfikacja dla idealnie wycentrowanego procesu (oczekiwane $C_p = C_{pk}$).
    *   Weryfikacja przesunięcia procesu w stronę górnej granicy (oczekiwane spadek $C_{pk}$ przy zachowaniu stałego $C_p$).
    *   Weryfikacja poprawnego rzucania wyjątków / kodów błędów przy błędnych limitach (np. $LSL \ge USL$).
