# Plan Implementacji - Moduł Statystyki Opisowej i Przestrzennej

Plan wdrożenia biblioteki matematycznej oraz serwisu obliczającego zaawansowane wskaźniki statystyczne rozkładu temperatur.

## 1. Wybór Technologii
Decyzja architektoniczna: **implementacja natywna**, bez zewnętrznych bibliotek statystycznych.

Uzasadnienie:
*   Spring Boot 3.x nie zawiera Apache Commons Math jako zależności tranzytywnej. Dodanie jej wymagałoby rozszerzenia pliku `pom.xml` i nowej walidacji biblioteki.
*   Walidacja kodu komputerowego (Computer System Validation - CSV) jest znacznie prostsza dla kodu natywnego: pełna kontrola nad algorytmami, brak ryzyka regresji przy aktualizacjach bibliotek, oraz łatwa weryfikacja przez audytora krok po kroku.
*   Wymagane funkcje statystyczne to lekki i zwięzły kod natywny w Java, łatwy w utrzymaniu i weryfikacji.

## 2. Architektura i Klasy

### [MODIFY] [SensorStatsEngine.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/stats/SensorStatsEngine.java)
Natywna klasa wykonująca obliczenia statystyczne na tablicy wartości zmiennoprzecinkowych:
*   Średnia: $\bar{x} = \frac{1}{n} \sum x_i$
*   Wariancja: $s^2 = \frac{1}{n-1} \sum (x_i - \bar{x})^2$
*   Odchylenie standardowe: $s = \sqrt{s^2}$
*   Współczynnik zmienności: $RSD = (s / \bar{x}) \times 100\%$
*   Skośność (Skewness, próbka, Fisher-Pearson):
    $$g_1 = \frac{n}{(n-1)(n-2)} \sum \left(\frac{x_i - \bar{x}}{s}\right)^3$$
    *Wymóg minimalny:* $n \ge 3$. Dla prób o rozmiarze $n < 3$ skośność jest statystycznie niezdefiniowana; silnik zwraca `Double.NaN` i loguje ostrzeżenie.
*   Kurtoza (Excess Kurtosis, próbka, Fisher-Pearson):
    $$g_2 = \frac{n(n+1)}{(n-1)(n-2)(n-3)} \sum \left(\frac{x_i - \bar{x}}{s}\right)^4 - \frac{3(n-1)^2}{(n-2)(n-3)}$$
    *Wymóg minimalny:* $n \ge 4$. Dla prób o rozmiarze $n < 4$ kurtoza jest statystycznie niezdefiniowana; silnik zwraca `Double.NaN` i loguje ostrzeżenie.

*Uwaga:* Stosujemy wzory próbkowe z poprawką Fisher-Pearson, zgodne z MS Excel (funkcje SKEW, KURT), R (pakiet `e1071::skewness` i `kurtosis` type=2) oraz SciPy (`scipy.stats.skew` i `kurtosis` z parametrem `bias=False`).

### [MODIFY] [SpatialStatsService.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/stats/SpatialStatsService.java)
Klasa agregująca dane ze wszystkich czujników dla każdego kroku czasowego (timestamp):
```java
public class SpatialStatsService {
    
    public SpatialStatsResult calculateSpatialStats(Collection<ThermoMeasurementSeries> seriesList) {
        // Oblicza rozstęp przestrzenny (max - min) dla każdego timestampu
        // Zwraca obiekt SpatialStatsResult zawierający średni rozstęp, maksymalny rozstęp i rozkład w czasie.
    }
}
```

### [MODIFY] [SpatialStatsResult.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/dto/stats/SpatialStatsResult.java)
Obiekt DTO przechowujący obliczone wskaźniki rozstępu przestrzennego:
*   `meanSpatialRange` (DOUBLE) - średni rozstęp przestrzenny.
*   `maxSpatialRange` (DOUBLE) - maksymalny rozstęp przestrzenny.
*   `spatialRangesOverTime` (MAP) - rozkład rozstępów przestrzennych w czasie.

## 3. Integracja z Modelami
W bazie danych w tabeli `thermo_measurement_series` zapisywane są nowe wskaźniki serii pomiarowej:
*   `spatial_mean_range` (DOUBLE)
*   `max_spatial_range` (DOUBLE)
*   `overall_rsd` (DOUBLE)

## 4. Plan Testów (Verification Plan)
*   **Testy jednostkowe (`SensorStatsEngineTest`):**
    *   Weryfikacja obliczania wariancji i odchylenia standardowego na znanym zbiorze testowym (porównanie wyników z MS Excel lub R).
    *   Weryfikacja zachowania przy pustej tablicy danych lub danych jednoelementowych (obsługa wyjątków).
*   **Testy integracyjne:**
    *   Weryfikacja poprawności zapisu obliczonych statystyk przestrzennych do bazy danych po zakończeniu importu serii pomiarowej.
