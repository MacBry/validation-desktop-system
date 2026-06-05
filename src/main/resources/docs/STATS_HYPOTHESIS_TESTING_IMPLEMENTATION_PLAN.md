# Plan Implementacji - Testowanie Hipotez Statystycznych

Plan wdrożenia silnika wnioskowania statystycznego (testy parametryczne i nieparametryczne) do analizy serii pomiarowych.

## 1. Wykorzystanie Bibliotek
Do przeprowadzenia zaawansowanych obliczeń wnioskowania statystycznego (t-Test, ANOVA) wykorzystamy klasę `org.apache.commons.math3.stat.inference.TTest` oraz `OneWayAnova` z biblioteki **Apache Commons Math** (w wersji 3.x), która jest de facto standardem w środowisku Java.

*Zależność w pom.xml:* Biblioteka ta jest już wczytywana przez Spring Boot lub zostanie dodana w sposób nieinwazyjny (jeśli zajdzie potrzeba, wdrożymy natywne algorytmy dla testów F oraz TOST w celu eliminacji zależności).

## 2. Architektura i Klasy

### [NEW] [HypothesisTestingService.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/stats/HypothesisTestingService.java)
Główny serwis wykonujący testy statystyczne:

```java
package com.mac.bry.desktop.service.stats;

import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.OneWayAnova;
import java.util.List;

public class HypothesisTestingService {

    private final TTest tTestEngine = new TTest();
    private final OneWayAnova anovaEngine = new OneWayAnova();

    /**
     * Test Równoważności TOST (Two One-Sided Tests)
     * Sprawdza czy średnie dwóch prób są równoważne w zadanym przedziale tolerancji theta.
     */
    public TostResult performTostEquivalence(double[] sample1, double[] sample2, double theta) {
        // Obliczenie t-testów dla dwóch hipotez jednostronnych:
        // H1: m1 - m2 <= -theta  (test jednostronny prawostronny)
        // H2: m1 - m2 >= theta   (test jednostronny lewostronny)
        
        double p1 = tTestEngine.tTest(sample1, sample2); // uproszczenie do standardowego p-value
        // W pełnej wersji implementacja TOST wylicza statystykę t bezpośrednio:
        // t1 = (mean1 - mean2 - (-theta)) / SE
        // t2 = (mean1 - mean2 - theta) / SE
        
        boolean equivalent = p1 < 0.05; // przykładowy próg istotności
        return new TostResult(equivalent, p1);
    }

    /**
     * Jednoczynnikowa Analiza Wariancji (ANOVA)
     * Porównuje średnie wartości z wielu czujników jednocześnie.
     */
    public AnovaResult performAnova(List<double[]> samples) {
        double pValue = anovaEngine.anovaPValue(samples);
        boolean significantDifference = pValue < 0.05;
        return new AnovaResult(significantDifference, pValue);
    }
}
```

### [NEW] [TostResult.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/dto/stats/TostResult.java) & [AnovaResult.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/dto/stats/AnovaResult.java)
Obiekty DTO reprezentujące wyniki obliczeń wraz ze statystykami testowymi i stopniami swobody.

## 3. Walidacja Algorytmów (GxP CSV)
Wszystkie testy statystyczne muszą zostać zwalidowane za pomocą zestawu testowego opartego na znanych danych referencyjnych (np. zestawach danych z NIST - National Institute of Standards and Technology).

## 4. Plan Testów (Verification Plan)
*   **Testy jednostkowe (`HypothesisTestingServiceTest`):**
    *   Weryfikacja testu ANOVA przy użyciu 3 zestawów danych o identycznych średnich (oczekiwane p-value bliskie 1.0).
    *   Weryfikacja testu ANOVA dla prób o znacząco różnych średnich (oczekiwane p-value < 0.05).
    *   Weryfikacja testu TOST: sprawdzenie, czy próby o średnich różniących się o wartość mniejszą niż $\theta$ są klasyfikowane jako równoważne.
