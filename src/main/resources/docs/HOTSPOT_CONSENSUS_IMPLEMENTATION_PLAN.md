# Plan Implementacji - Hybrydowa Detekcja Hotspot/Coldspot (Konsensus Metodyczny)

## 1. Cel i zakres zmian
Celem zmian jest zastąpienie dotychczasowej, uproszczonej i podatnej na błędy (remisy/kolizje) analizy hotspot/coldspot w [MappingValidator.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/helper/MappingValidator.java) na zaawansowany silnik detekcji opartej o **konsensus metodyczny (głosowanie większościowe)**. 

Wdrożenie to zintegruje 5 strategii analitycznych:
1.  **Maksimum/Minimum Absolutne (AbsMax/AbsMin)** - worst-case.
2.  **Średnia Arytmetyczna (Mean)** - steady-state.
3.  **Średnia Temperatura Kinetyczna (MKT)** - kinetyka degradacji (tylko hotspot).
4.  **Percentyle (P99/P01)** - odrzucanie 1% szumów i krótkich otwarć drzwi.
5.  **Czas przekroczenia limitu (Time-Over-Limit - TOL)** - całka stopniodni/stopniominut ($^\circ\text{C}\cdot\text{min}$).

---

## 2. Nowa Architektura Detekcji

### 2.1. Struktura klas
Wszystkie klasy detekcji zostaną umieszczone w nowej paczce: `com.mac.bry.desktop.service.hotspot`.

```
com.mac.bry.desktop.service.hotspot
│
├── SensorStats.java                 (DTO z zagregowanymi danymi czujnika)
├── ExtremeDetectionStrategy.java    (sealed interface - definicja kontraktu)
├── AbsMaxStrategy.java              (strategia hotspotu absolutnego)
├── AbsMinStrategy.java              (strategia coldspotu absolutnego)
├── MeanStrategy.java                (strategia średniej temperatury)
├── MktStrategy.java                 (strategia MKT)
├── PercentileStrategy.java          (strategia percentyli P99/P01)
├── TimeOverLimitStrategy.java       (strategia całkowa TOL)
└── ConsensusDetectionService.java   (silnik agregacji głosów i rozstrzygania remisów)
```

### 2.2. Rozstrzyganie remisów (Tie-breaking)
Jeśli dwie metody wskażą różne czujniki (np. remis 2:2), system nie rzuca błędu, lecz rozstrzyga go na podstawie zdefiniowanego **priorytetu metodycznego**:
`Time-Over-Limit (TOL) > AbsMax/AbsMin > MKT > Percentile > Mean`.

Jeśli konsensus jest słaby (poniżej 50% zgodności, czyli np. 3 czujniki otrzymały po 1-2 głosy), system zwraca wynik, ale oznacza go flagą `isWeak = true` (Słaby Konsensus). Pozwala to na wyświetlenie żółtego ostrzeżenia dla QA w UI zamiast blokowania kreatora.

---

## 3. Szczegółowy wykaz zmian w plikach

### 3.1. Klasy Serwisowe Detekcji (Nowy Pakiet)

#### [NEW] `SensorStats.java`
Immutable record reprezentujący statystyki pojedynczej pozycji pomiarowej:
```java
package com.mac.bry.desktop.service.hotspot;

public record SensorStats(
        String sensorId,
        double absMax,
        double absMin,
        double mean,
        double p99,
        double p01,
        double mkt,
        double tolHi, // stopniodni/minut powyżej limitu max
        double tolLo  // stopniodni/minut poniżej limitu min
) {
    public double get(StatField field) {
        return switch (field) {
            case ABS_MAX -> absMax;
            case ABS_MIN -> absMin;
            case MEAN    -> mean;
            case P99     -> p99;
            case P01     -> p01;
            case MKT     -> mkt;
            case TOL_HI  -> tolHi;
            case TOL_LO  -> tolLo;
        };
    }
    public enum StatField { ABS_MAX, ABS_MIN, MEAN, P99, P01, MKT, TOL_HI, TOL_LO }
}
```

#### [NEW] `ExtremeDetectionStrategy.java`
Definicja sealed interface z domyślną implementacją szukania zwycięzcy i ignorowania zdegenerowanych przypadków (np. TOL = 0.0):
```java
package com.mac.bry.desktop.service.hotspot;

import java.util.List;
import java.util.Optional;

public sealed interface ExtremeDetectionStrategy
        permits AbsMaxStrategy, AbsMinStrategy, MeanStrategy,
                MktStrategy, PercentileStrategy, TimeOverLimitStrategy {

    String methodCode();
    String displayName();
    boolean isHotspot();
    SensorStats.StatField field();

    default Optional<Verdict> apply(List<SensorStats> stats) {
        var meaningful = stats.stream()
                .filter(s -> !isDegenerate(s))
                .toList();
        if (meaningful.isEmpty()) return Optional.empty();

        var f = field();
        var winner = isHotspot()
                ? meaningful.stream().max((a, b) -> Double.compare(a.get(f), b.get(f)))
                : meaningful.stream().min((a, b) -> Double.compare(a.get(f), b.get(f)));

        var w = winner.orElseThrow();
        return Optional.of(new Verdict(methodCode(), displayName(), w.sensorId(), w.get(f), isHotspot()));
    }

    default boolean isDegenerate(SensorStats s) { return false; }

    record Verdict(String methodCode, String methodName, String winnerSensorId, double value, boolean isHotspot) {}
}
```

#### [NEW] Klasy Strategii (AbsMax, AbsMin, Mean, Mkt, Percentile, TimeOverLimit)
Implementują interfejs `ExtremeDetectionStrategy`. W klasie `TimeOverLimitStrategy` nadpisujemy `isDegenerate()`:
```java
@Override
public boolean isDegenerate(SensorStats s) {
    return field() == SensorStats.StatField.TOL_HI ? s.tolHi() == 0.0 : s.tolLo() == 0.0;
}
```

#### [NEW] `ConsensusDetectionService.java`
Główny serwis agregujący i liczący głosy oraz realizujący tie-break.

### 3.2. Integracja z walidatorem: `MappingValidator.java`
*   **Modyfikacja:** [MODIFY] `c:\Users\macie\Desktop\VCC Desktop APP\validation-desktop\src\main\java\com\mac\bry\desktop\service\helper\MappingValidator.java`
*   Klasa `MappingResult` zostanie rozszerzona o pola:
    *   `boolean weakConsensus;`
    *   `double hotspotStrength;`
    *   `double coldspotStrength;`
*   W metodzie `validate()`:
    1.  Wyliczamy dla każdego czujnika komplet parametrów do `SensorStats`.
    2.  Wyliczamy całki przekroczeń `TOL` dla każdego punktu pomiarowego względem limitów komory:
        *   $\text{TOL}_{HI} = \sum (T - T_{maxLimit}) \cdot \Delta t$ (dla $T > T_{maxLimit}$)
        *   $\text{TOL}_{LO} = \sum (T_{minLimit} - T) \cdot \Delta t$ (dla $T < T_{minLimit}$)
    3.  Wyliczamy percentyle P99 i P01 za pomocą sortowania próbek.
    4.  Tworzymy listę 8 obiektów `SensorStats` i przekazujemy do `ConsensusDetectionService`.
    5.  Zwracamy wynik z wyznaczonym hotspotem, coldspotem oraz siłą konsensusu.

### 3.3. Integracja z UI: `TestoRevalidationController.java`
*   **Modyfikacja:** [MODIFY] `c:\Users\macie\Desktop\VCC Desktop APP\validation-desktop\src\main\java\com\mac\bry\desktop\controller\TestoRevalidationController.java`
*   Metoda wyświetlająca podsumowanie mapowania (`showMappingSummary` / `showStep3`) będzie odczytywać pola `weakConsensus` oraz siłę konsensusu.
*   Jeśli `weakConsensus == true`, system wyświetli żółte ostrzeżenie (np. w komponencie `Label` o kolorze `-color-warning-fg`):
    *   *„Zidentyfikowano słabą zgodność metod detekcji (Hotspot: X%, Coldspot: Y%). Zgłoszenie wymaga weryfikacji QA w raporcie końcowym.”*
*   Jeżeli konsensus jest silny, wyświetli zielone potwierdzenie sukcesu.

---

## 4. Plan Weryfikacji (Testy i Walidacja)

### 4.1. Testy Jednostkowe (Unit Tests)
*   **TST-HS-01:** Test weryfikujący poprawność obliczeń MKT w klasie kalkulatora.
*   **TST-HS-02:** Test sprawdzający zachowanie przy remisie – upewnienie się, że `ConsensusDetectionService` poprawnie wybiera czujnik o wyższym priorytecie (TOL > AbsMax > Mean).
*   **TST-HS-03:** Test zdegenerowanych pomiarów – upewnienie się, że gdy wszystkie całki przekroczeń wynoszą 0.0, strategia `TimeOverLimitStrategy` zwraca `Optional.empty()` i nie wypacza wyniku końcowego.

### 4.2. Walidacja Operacyjna (OQ - Manual)
1.  **Test kompletnych danych komory:**
    *   Wgraj 8 serii pomiarowych z pliku testowego.
    *   Zweryfikuj w kroku podsumowania, czy wyznaczony Hotspot i Coldspot pokrywają się z wyliczeniami analitycznymi.
2.  **Test symulacji remisu:**
    *   Zmodyfikuj ręcznie plik pomiarów tak, aby czujnik T2 i T3 osiągnęły to samo maksimum absolutne (np. 8.50°C), ale T3 miał dłuższą ekspozycję (wyższy TOL).
    *   Wgraj dane i zweryfikuj, czy system wskazał T3 jako hotspot, poprawnie rozstrzygając remis bez wyrzucenia błędu aplikacji.
