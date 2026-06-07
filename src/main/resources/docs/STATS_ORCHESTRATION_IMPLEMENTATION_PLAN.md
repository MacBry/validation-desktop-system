# Plan Implementacji - Orkiestracja Statystyk (Rekomendacja 3)

Plan techniczny wdrożenia klasy transferowej `StatsReportDTO` oraz serwisu orkiestrującego `StatisticsAggregationService` w aplikacji `validation-desktop`.

---

## 1. Struktura Klas i Zależności

Nowe klasy zostaną umieszczone w strukturze pakietów:
*   `com.mac.bry.desktop.dto.stats.StatsReportDTO`
*   `com.mac.bry.desktop.service.stats.StatisticsAggregationService`

### Zależności Spring DI:
`StatisticsAggregationService` będzie wstrzykiwał następujące serwisy:
1.  `MetrologicalStatsService` — do wyliczenia podstawowych statystyk i niepewności rozszerzonej GUM (zapisywanych w encji).
2.  `HypothesisTestingService` — do przeprowadzenia testu normalności rozkładu Jarque-Bera.

---

## 2. Projekt Techniczny Klas

### 2.1. `StatsReportDTO.java`
Klasa będzie reprezentowała pełny obraz analityczny pojedynczej serii pomiarowej.

```java
package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsReportDTO {
    private String positionName;
    private String recorderSerialNumber;

    // 1. Statystyka Opisowa
    private double minTemp;
    private double maxTemp;
    private double avgTemp;
    private double medianTemp;
    private double stdDev;
    private double variance;
    private double cvPercentage;
    private double percentile5;
    private double percentile95;
    private double mktTemp;
    private double expandedUncertainty;

    // 2. Testy Hipotez
    private double jbStatistic;
    private double jbPValue;
    private boolean isNormallyDistributed;

    // 3. Statystyczne Sterowanie Procesem (SPC)
    private Double lsl;
    private Double usl;
    private Double cp;
    private Double cpk;
    private boolean isCapable;      // Cpk >= 1.33
    private boolean isAcceptable;    // Cpk >= 1.0

    // 4. Szeregi Czasowe i FFT
    private List<DefrostCycle> defrostCycles;
    private int defrostCyclesCount;
    private double maxDefrostAmplitude;
    private double avgDefrostDurationMinutes;
    private double dominantFrequency;      // w cyklach na minutę
    private double dominantPeriodMinutes;   // okres dominujący w minutach
    private double[] fftSpectrum;
}
```

### 2.2. Obliczanie Dominującego Okresu w `StatisticsAggregationService`
Na podstawie widma zwróconego przez `FftCalculator.calculateFftSpectrum(double[] input)` wyznaczamy okres dominujący w minutach:
1.  Przeszukujemy tablicę `fftSpectrum` od indeksu `1` (pomijamy indeks `0` — składową stałą DC, która odpowiada średniej temperaturze).
2.  Znajdujemy indeks `maxIdx` o najwyższej amplitudzie.
3.  Wyliczamy okres w minutach:
    $$\text{periodMinutes} = \frac{M \cdot \Delta t}{\text{maxIdx}}$$
    gdzie $M$ to długość tablicy wejściowej FFT po dopełnieniu zerami (potęga 2), a $\Delta t$ to interwał rejestracji (`loggingIntervalMinutes`).
4.  Wyliczamy częstotliwość dominującą:
    $$\text{frequency} = \frac{1}{\text{periodMinutes}}$$

---

## 3. Scenariusze Testowe (Verification Plan)

### TST-ORCH-01: Weryfikacja Agregacji Danych w DTO
*   **Cel:** Sprawdzenie, czy serwis poprawnie agreguje wyniki wszystkich analiz do jednego DTO.
*   **Dane wejściowe:** Encja `ThermoMeasurementSeries` z załadowanymi 32 punktami pomiarowymi (sinusoida 5°C ± 1.5°C z jednym sztucznym cyklem defrostu trwającym 30 min, interwał = 15 minut, limity komory = 2.0°C - 8.0°C).
*   **Procedura:**
    1. Wywołanie `StatisticsAggregationService.aggregate(series)`.
    2. Odczytanie obiektu `StatsReportDTO`.
*   **Oczekiwane wyniki:**
    *   Wskaźniki SPC ($C_p$, $C_{pk}$) są poprawnie obliczone i nie są puste.
    *   Liczba wykrytych cykli defrostu = 1.
    *   Dominujący okres wahań temperatury w minutach jest zbliżony do okresu wejściowej sinusoidy.
    *   Wartość p-value dla testu Jarque-Bera jest poprawna.

### TST-ORCH-02: Obsługa braku limitów komory chłodniczej
*   **Cel:** Bezpieczne zachowanie serwisu, gdy komora nie ma określonych limitów temperatur ($USL = \text{null}$, $LSL = \text{null}$).
*   **Procedura:**
    1. Wyzerowanie limitów w obiekcie `CoolingChamber` powiązanym z serią.
    2. Wywołanie agregacji.
*   **Oczekiwane wyniki:**
    *   Obiekt `StatsReportDTO` zostaje poprawnie wygenerowany.
    *   Pola `cp` i `cpk` mają wartość `null`.
    *   Flagi `isCapable` i `isAcceptable` mają wartość `false`.
