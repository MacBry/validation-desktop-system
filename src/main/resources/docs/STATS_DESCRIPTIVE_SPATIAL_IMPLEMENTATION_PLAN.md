# Plan Implementacji - Moduł Statystyki Opisowej i Przestrzennej

Plan wdrożenia biblioteki matematycznej oraz serwisu obliczającego zaawansowane wskaźniki statystyczne rozkładu temperatur.

## 1. Wybór Technologii
Zgodnie z wymaganiem niemodyfikowania pliku `pom.xml` o zewnętrzne biblioteki niesprawdzone pod kątem walidacji, wykorzystamy wbudowane narzędzia oraz **Apache Commons Math** (jeżeli jest obecne w zależnościach przejściowych Spring Boot) lub napiszemy lekki, natywny moduł matematyczny zoptymalizowany pod kątem szybkości i niskiego zużycia pamięci RAM (przetwarzanie w locie dużych zbiorów danych pomiarowych).

*Uwaga:* Napisanie natywnej klasy pozwala na łatwiejszą walidację kodu komputerowego (Computer System Validation - CSV), co jest kluczowe w systemach GxP.

## 2. Architektura i Klasy

### [NEW] [SensorStatsEngine.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/stats/SensorStatsEngine.java)
Natywna klasa wykonująca obliczenia statystyczne na tablicy wartości zmiennoprzecinkowych:
*   Średnia: $\bar{x} = \frac{1}{n} \sum x_i$
*   Wariancja: $s^2 = \frac{1}{n-1} \sum (x_i - \bar{x})^2$
*   Odchylenie standardowe: $s = \sqrt{s^2}$
*   Współczynnik zmienności: $RSD = (s / \bar{x}) \times 100\%$
*   Skośność: $\gamma = \frac{\frac{1}{n}\sum(x_i - \bar{x})^3}{s^3}$
*   Kurtoza: $\kappa = \frac{\frac{1}{n}\sum(x_i - \bar{x})^4}{s^4} - 3$

### [NEW] [SpatialStatsService.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/stats/SpatialStatsService.java)
Klasa agregująca dane ze wszystkich czujników dla danego kroku czasowego (timestamp):
```java
public class SpatialStatsService {
    
    public SpatialSpreadResult calculateSpatialSpread(List<SensorDataPoint> points) {
        double max = points.stream().mapToDouble(SensorDataPoint::getValue).max().orElse(0.0);
        double min = points.stream().mapToDouble(SensorDataPoint::getValue).min().orElse(0.0);
        double mean = points.stream().mapToDouble(SensorDataPoint::getValue).average().orElse(0.0);
        
        double spread = max - min;
        return new SpatialSpreadResult(max, min, mean, spread);
    }
}
```

### [NEW] [SpatialSpreadResult.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/dto/stats/SpatialSpreadResult.java)
Obiekt DTO przechowujący obliczone wskaźniki rozstępu przestrzennego.

## 3. Integracja z Modelami
W bazie danych w tabeli `thermo_measurement_series` dodane zostaną nowe kolumny przechowujące zagregowane statystyki serii pomiarowej:
*   `spatial_mean_spread` (DOUBLE)
*   `max_spatial_spread` (DOUBLE)
*   `overall_rsd` (DOUBLE)

## 4. Plan Testów (Verification Plan)
*   **Testy jednostkowe (`SensorStatsEngineTest`):**
    *   Weryfikacja obliczania wariancji i odchylenia standardowego na znanym zbiorze testowym (porównanie wyników z MS Excel lub R).
    *   Weryfikacja zachowania przy pustej tablicy danych lub danych jednoelementowych (obsługa wyjątków).
*   **Testy integracyjne:**
    *   Weryfikacja poprawności zapisu obliczonych statystyk przestrzennych do bazy danych po zakończeniu importu serii pomiarowej.
