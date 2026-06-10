# Plan Implementacji - Statystyczna Kontrola Procesu (SPC) i Analiza Trendów

Plan techniczny wdrożenia silnika obliczania wskaźników zdolności procesu, kart kontrolnych Shewharta (X-bar / S) oraz automatycznej detekcji naruszeń stabilności na podstawie reguł Nelsona (Nelson Rules).

## 1. Architektura Obliczeń
Wszystkie algorytmy SPC są zaimplementowane w dedykowanych klasach narzędziowych i pomocniczych w pakiecie `com.mac.bry.desktop.service.stats`. Dane wejściowe to surowy ciąg odczytów temperatury wraz z limitami specyfikacji (LSL, USL).

## 2. Klasy i Logika Biznesowa

### [MODIFY] [SpcEngine.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/stats/SpcEngine.java)
Klasa odpowiedzialna za obliczanie wskaźników zdolności procesu ($C_p$, $C_{pk}$) na podstawie surowych danych i limitów specyfikacji. Zapobiega dzieleniu przez zero przy stałej linii pomiarów.

### [MODIFY] [ControlChartCalculator.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/stats/ControlChartCalculator.java)
Dzieli dane pomiarowe na podgrupy (o rozmiarze $n = 5$) i wylicza:
- Średnie podgrup ($\overline{X}$) oraz odchylenia standardowe podgrup ($S$).
- Granice kontrolne dla karty $X\text{-bar}$: $CL = \overline{\overline{X}}$, $UCL = \overline{\overline{X}} + A_3 \bar{S}$, $LCL = \overline{\overline{X}} - A_3 \bar{S}$ ($A_3 = 1.427$).
- Granice kontrolne dla karty $S$: $CL = \bar{S}$, $UCL = B_4 \bar{S}$, $LCL = B_3 \bar{S}$ ($B_4 = 2.089$, $B_3 = 0.0$).

### [NEW] [NelsonRulesDetector.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/stats/NelsonRulesDetector.java)
Klasa realizująca detekcję naruszeń stabilności procesu na podstawie reguł Nelsona. Posiada wewnętrzny rekord/klasę DTO `Violation`:
```java
public static class Violation {
    int subgroupIndex; // Indeks podgrupy (1-indexed)
    int ruleNumber;    // Numer reguły (1, 2, 3 lub 4)
    String description; // Szczegółowy opis naruszenia
    boolean isSChart;  // Czy dotyczy karty S (true) czy X-bar (false)
}
```
Metody detekcji:
- `detectXBarViolations(ControlChartData data)`: Wykrywa naruszenia reguł 1, 2, 3 oraz 4 na karcie $X\text{-bar}$.
  - **Reguła 1:** Jeden punkt poza granicami $\pm 3\sigma$ ($UCL$/$LCL$).
  - **Reguła 2:** Dziewięć kolejnych punktów po tej samej stronie linii centralnej ($CL$).
  - **Reguła 3:** Sześć kolejnych punktów stale rosnących lub stale malejących.
  - **Reguła 4:** Czternaście kolejnych punktów naprzemiennie rosnących i malejących (zapobiega `ArrayIndexOutOfBoundsException` przez poprawne indeksowanie pętli od `i - 11` do `i`).
- `detectSViolations(ControlChartData data)`: Wykrywa naruszenia reguły 1 (punkt poza granicami UCL/LCL) na karcie $S$.

## 3. Integracja z UI (JavaFX)

### [MODIFY] [stats_diagnostics_dialog.fxml](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/resources/ui/stats_diagnostics_dialog.fxml)
Dodano kontrolkę `ListView<String> lstNelsonViolations` pod wykresami Shewharta. FXML zawiera element `<placeholder>` z komunikatem o braku naruszeń, ostylowanym w kolorze zielonym (`#16a34a`) dla zachowania wysokiej czytelności.

### [MODIFY] [StatsDiagnosticsDialogController.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/controller/StatsDiagnosticsDialogController.java)
- Pobiera dane pomiarowe, wyznacza granice Shewharta przy użyciu `ControlChartCalculator`.
- Wywołuje `NelsonRulesDetector` w celu pobrania listy naruszeń dla obu kart.
- Definiuje niestandardowy `ListCell` cell factory dla `lstNelsonViolations`, który wymusza kontrastowy ciemnoczerwony kolor tekstu (`#b91c1c`) oraz pogrubienie dla każdego wykrytego naruszenia, zapobiegając nieczytelności (np. biały tekst na jasnym tle w motywach systemowych/skórkach `PrimerLight`).

## 4. Generator Raportu PDF (GxP)

### [MODIFY] [RevalidationReportPdfRenderer.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/pdf/RevalidationReportPdfRenderer.java)
- **Sekcja 4.2 (Wnioski Statystyczne):** Dynamiczne dodanie podsekcji "5. Weryfikacja Stabilności Procesu (Karty Shewharta & Nelson Rules)". Oblicza całkowitą liczbę naruszeń. W przypadku braku naruszeń generuje zapis o stochastycznej stabilności. W przypadku wykrycia naruszeń wstawia ostrzeżenie z zaleceniem wdrożenia procedur korygujących (CAPA) i odsyła do szczegółów.
- **Sekcja 4.3 (Weryfikacja Stabilności Procesu):** Nowa sekcja na osobnej stronie PDF. Zawiera tabelę z 7 kolumnami (`Pozycja`, `X-bar CL`, `X-bar LCL/UCL`, `S CL`, `S UCL`, `Naruszenia Nelsona (X-Bar)`, `Naruszenia S`). Jeśli dla danej pozycji wykryto naruszenia, odpowiednia komórka otrzymuje jasnoczerwone tło (`#fef2f2`) i szczegółową listę naruszeń.

## 5. Plan Testów (Verification Plan)
Wdrożono testy jednostkowe w klasie `NelsonRulesDetectorTest.java` pokrywające:
- Naruszenia reguły 1, 2, 3, 4 na karcie $X\text{-bar}$.
- Naruszenia reguły 1 na karcie $S$.
Weryfikacja integracyjna obejmuje automatyczne testy kompilacji oraz manualne testy renderowania dialogu diagnostycznego UI (kontrast kolorów) i poprawności tabeli w wygenerowanym raporcie rewalidacji PDF.
