# Scenariusze Testowe - SPC i Analiza Trendów

Dokumentacja scenariuszy testowych (od jednostkowych po integracyjne i kwalifikacyjne CSV) dla modułu Statystycznej Kontroli Procesu (SPC), kart kontrolnych Shewharta, reguł Nelsona (Nelson Rules) oraz analizy trendów.

---

## 🧪 1. Testy Jednostkowe (Unit Tests)

### UT-01: Wyznaczanie Wskaźnika Zdolności $C_p$ (Proces Idealny)
*   **Cel testu:** Weryfikacja obliczania potencjalnej zdolności procesu ($C_p$).
*   **Dane wejściowe:**
    *   Wartości: `double[] values = { 5.0, 5.0, 5.0, 4.0, 6.0 };` ($\mu = 5.0$, $s = 0.70710678$)
    *   Specyfikacja: $LSL = 2.0$, $USL = 8.0$
*   **Oczekiwany wynik:**
    *   $C_p = \frac{8.0 - 2.0}{6 \times 0.70710678} = 1.414$
    *   $C_{pk} = C_p = 1.414$
*   **Kryterium akceptacji:** $C_p$ oraz $C_{pk}$ równe $1.414 \pm 0.005$.

### UT-02: Wyznaczanie Wskaźnika $C_{pk}$ (Proces Przesunięty)
*   **Cel testu:** Weryfikacja pogorszenia $C_{pk}$ przy przesunięciu średniej procesu.
*   **Dane wejściowe:**
    *   Wartości: `double[] values = { 7.0, 7.0, 7.0, 6.0, 8.0 };` ($\mu = 7.0$, $s = 0.70710678$)
    *   Specyfikacja: $LSL = 2.0$, $USL = 8.0$
*   **Oczekiwane wyniki:**
    *   $C_p = 1.414$
    *   $C_{pk} = \frac{8.0 - 7.0}{3 \times 0.70710678} = 0.471$
*   **Kryterium akceptacji:** $C_{pk}$ wynosi $0.471 \pm 0.005$.

### UT-03: Wykrywanie Trendu Regresji Liniowej
*   **Cel testu:** Weryfikacja wyznaczania nachylenia trendu.
*   **Dane wejściowe:** Stały wzrost temperatury w czasie (np. `{ 2.0, 2.5, 3.0, 3.5, 4.0 }`).
*   **Oczekiwany wynik:** Współczynnik regresji $a > 0$, $R^2 = 1.0$ (idealne dopasowanie liniowe).
*   **Kryterium akceptacji:** Prawidłowe wyznaczenie współczynników trendu.

### UT-04: Detekcja Reguły 1 Nelsona (X-bar)
*   **Cel testu:** Weryfikacja wykrywania punktu poza granicami $3\sigma$ ($UCL$/$LCL$).
*   **Klasa testowa:** [NelsonRulesDetectorTest.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/test/java/com/mac/bry/desktop/service/stats/NelsonRulesDetectorTest.java#L14-L29)
*   **Dane wejściowe:** Średnia podgrupy przekraczająca górną granicę UCL (np. wartość 6.3 przy UCL = 6.0, CL = 5.0).
*   **Oczekiwany wynik:** Wykrycie 1 naruszenia o numerze reguły 1 na właściwym indeksie podgrupy (indeks 3).

### UT-05: Detekcja Reguły 2 Nelsona (X-bar)
*   **Cel testu:** Weryfikacja wykrywania 9 kolejnych punktów po tej samej stronie linii centralnej ($CL$).
*   **Klasa testowa:** [NelsonRulesDetectorTest.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/test/java/com/mac/bry/desktop/service/stats/NelsonRulesDetectorTest.java#L31-L45)
*   **Dane wejściowe:** 9 kolejnych wartości średnich podgrup powyżej $CL$ (np. `5.1, 5.2, 5.1, 5.3, 5.2, 5.4, 5.1, 5.3, 5.2`).
*   **Oczekiwany wynik:** Wykrycie naruszenia reguły 2 dla 9. podgrupy.

### UT-06: Detekcja Reguły 3 Nelsona (X-bar)
*   **Cel testu:** Weryfikacja wykrywania stałego trendu rosnącego lub malejącego (6 kolejnych punktów).
*   **Klasa testowa:** [NelsonRulesDetectorTest.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/test/java/com/mac/bry/desktop/service/stats/NelsonRulesDetectorTest.java#L47-L61)
*   **Dane wejściowe:** 6 kolejnych stale rosnących wartości (np. `4.8, 4.9, 5.0, 5.1, 5.2, 5.3`).
*   **Oczekiwany wynik:** Wykrycie 1 naruszenia reguły 3 na 6. podgrupie.

### UT-07: Detekcja Reguły 4 Nelsona (X-bar - Poprawka Indeksowania)
*   **Cel testu:** Weryfikacja poprawności matematycznej i indeksowania dla reguły oscylacji (14 kolejnych punktów naprzemiennie rosnących i malejących).
*   **Klasa testowa:** [NelsonRulesDetectorTest.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/test/java/com/mac/bry/desktop/service/stats/NelsonRulesDetectorTest.java#L63-L75)
*   **Dane wejściowe:** Sekwencja 14 naprzemiennych wartości (np. `5.0, 4.8, 5.2, 4.8, ...`).
*   **Oczekiwany wynik:** Detekcja reguły 4 na 14. podgrupie bez wystąpienia błędu indeksacji `ArrayIndexOutOfBoundsException` (dzięki poprawnej pętli rozpoczynającej badanie od `i - 11`).

### UT-08: Detekcja Reguły 1 Nelsona na Karcie S
*   **Cel testu:** Weryfikacja wykrywania przekroczeń granic kontrolnych odchylenia standardowego ($UCL_S$).
*   **Klasa testowa:** [NelsonRulesDetectorTest.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/test/java/com/mac/bry/desktop/service/stats/NelsonRulesDetectorTest.java#L77-L93)
*   **Dane wejściowe:** Odchylenie standardowe podgrupy równe 0.6 przy górnej granicy $UCL_S = 0.5$.
*   **Oczekiwany wynik:** Wykrycie naruszenia oznaczonego jako karta S (`isSChart = true`) na 2. podgrupie.

---

## 🔗 2. Testy Integracyjne i Interfejsu (Integration & UI Tests)

### IT-01: Generowanie Karty Kontrolnej Shewharta
*   **Cel testu:** Weryfikacja wyznaczania granic UCL i LCL dla podgrup oraz przekazywania tych punktów do wykresu JavaFX.
*   **Kryterium akceptacji:** Wykres JavaFX renderuje się poprawnie, linie UCL/LCL są właściwie wyliczone i naniesione na osi Y.

### IT-02: Kontrast i Prezentacja UI w Różnych Motywach
*   **Cel testu:** Weryfikacja czytelności komunikatów o naruszeniach reguł stabilności w jasnym motywie graficznym (`PrimerLight`).
*   **Procedura:**
    1. Uruchomienie aplikacji i przejście do szczegółów diagnostyki SPC sensorów.
    2. Zweryfikowanie czy napisy naruszeń w `lstNelsonViolations` posiadają kontrastowy, ciemnoczerwony kolor (`#b91c1c`) oraz pogrubioną czcionkę.
    3. Zweryfikowanie czy w przypadku braku naruszeń tekst w placeholderze ma wyraźny zielony kolor (`#16a34a`).
*   **Kryterium akceptacji:** Tekst komunikatów o naruszeniach jest w pełni czytelny i wyraźnie kontrastuje z jasnym tłem listy (brak efektu "niewidzialnego tekstu").

### IT-03: Integracja z Raportem PDF GxP
*   **Cel testu:** Weryfikacja automatycznego zamieszczania statystyk i wykazu naruszeń w generowanym dokumencie rewalidacji PDF.
*   **Procedura:**
    1. Uruchomienie generowania raportu PDF z sesji zawierającej naruszenia reguł stabilności.
    2. Weryfikacja **Sekcji 4.2**: wstawiony blok podsumowujący (punkt 5) powinien zawierać pogrubione ostrzeżenie o wykrytych naruszeniach.
    3. Weryfikacja **Sekcji 4.3**: tabela powinna zawierać kolumny z granicami $CL$, $LCL/UCL$ dla kart $X\text{-bar}$ i $S$, a pozycje z naruszeniami muszą mieć jasnoczerwone tło komórek (`#fef2f2`) z wypisanymi kodami reguł i podgrup.
*   **Kryterium akceptacji:** Układ tabeli jest poprawny, dane są zgodne z wyliczeniami, a tło komórek z naruszeniami wyróżnia się kolorystycznie.

---

## 🔒 3. Scenariusz Kwalifikacji GxP (CSV - Computer System Validation)

### CSV-01: Kwalifikacja Obliczeń SPC i Wykrywania Naruszeń
*   **Cel testu:** Formalny dowód, że algorytm oceny zdolności i stabilności komór chłodniczych działa poprawnie i zgodnie z oczekiwaniami walidacyjnymi.
*   **Procedura:**
    1. Import referencyjnego zbioru danych temperatury.
    2. Wygenerowanie raportu PDF oraz wyeksportowanie wyników SPC z aplikacji.
    3. Porównanie wartości $C_p$, $C_{pk}$, granic $UCL/LCL$ oraz indeksów podgrup naruszających reguły Nelsona z obliczeniami wykonanymi w certyfikowanym oprogramowaniu statystycznym Minitab.
*   **Kryterium akceptacji:** Wskaźniki $C_p$ i $C_{pk}$ zgadzają się z dokładnością do $0.001$, a wyznaczone podgrupy z naruszeniami reguł Nelsona pokrywają się w 100% z raportem z programu Minitab.
