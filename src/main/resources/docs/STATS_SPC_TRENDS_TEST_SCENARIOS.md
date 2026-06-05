# Scenariusze Testowe - SPC i Analiza Trendów

Dokumentacja scenariuszy testowych (od jednostkowych po integracyjne) dla modułu Statystycznej Kontroli Procesu (SPC) oraz wykrywania trendów (wskaźniki $C_p$, $C_{pk}$ oraz regresja liniowa).

---

## 🧪 1. Testy Jednostkowe (Unit Tests)

### UT-01: Wyznaczanie Wskaźnika Zdolności $C_p$ (Proces Idealny)
*   **Cel testu:** Weryfikacja obliczania potencjalnej zdolności procesu.
*   **Dane wejściowe:**
    *   Wartości: `double[] values = { 5.0, 5.0, 5.0, 4.0, 6.0 };` ($\mu = 5.0$, $s = 0.70710678$)
    *   Specyfikacja: $LSL = 2.0$, $USL = 8.0$
*   **Oczekiwany wynik:**
    *   Proces jest idealnie wycentrowany ($\mu = \frac{USL+LSL}{2} = 5.0$).
    *   $C_p = \frac{8.0 - 2.0}{6 \times 0.70710678} = 1.414$
    *   $C_{pk} = C_p = 1.414$
*   **Kryterium akceptacji:** $C_p$ oraz $C_{pk}$ równe $1.414 \pm 0.005$.

### UT-02: Wyznaczanie Wskaźnika $C_{pk}$ (Proces Przesunięty)
*   **Cel testu:** Weryfikacja pogorszenia $C_{pk}$ przy przesunięciu średniej procesu w kierunku limitu specyfikacji.
*   **Dane wejściowe:**
    *   Wartości: `double[] values = { 7.0, 7.0, 7.0, 6.0, 8.0 };` ($\mu = 7.0$, $s = 0.70710678$)
    *   Specyfikacja: $LSL = 2.0$, $USL = 8.0$ (średnia blisko górnego limitu $USL$)
*   **Oczekiwane wyniki:**
    *   $C_p = 1.414$ (nie ulega zmianie, bo rozrzut jest taki sam)
    *   $C_{pk} = \frac{8.0 - 7.0}{3 \times 0.70710678} = 0.471$
*   **Kryterium akceptacji:** $C_{pk}$ wynosi $0.471 \pm 0.005$.

### UT-03: Wykrywanie Trendu Regresji Liniowej
*   **Cel testu:** Weryfikacja obliczania współczynnika kierunkowego ($a$) i współczynnika determinacji ($R^2$).
*   **Dane wejściowe:** Stały wzrost temperatury w czasie (np. `{ 2.0, 2.5, 3.0, 3.5, 4.0 }`).
*   **Oczekiwany wynik:** Współczynnik regresji $a > 0$, $R^2 = 1.0$ (idealne dopasowanie liniowe).
*   **Kryterium akceptacji:** Prawidłowe wyznaczenie nachylenia trendu i współczynnika dopasowania.

---

## 🔗 2. Testy Integracyjne (Integration Tests)

### IT-01: Generowanie Karty Kontrolnej Shewharta
*   **Cel testu:** Weryfikacja wyznaczania granic UCL i LCL dla podgrup oraz przekazywania tych punktów do wykresu JavaFX.
*   **Przebieg testu:**
    1. Załadowanie do bazy danych 100 odczytów.
    2. Wywołanie klasy `ControlChartCalculator` z podziałem na podgrupy o rozmiarze 5.
    3. Pobranie tablicy punktów dla wykresu.
*   **Oczekiwany wynik:**
    *   Wyznaczenie 20 punktów na wykresie.
    *   Poprawne wyliczenie Linii Centralnej (CL) oraz UCL/LCL.
*   **Kryterium akceptacji:** Wykres JavaFX renderuje się bez wyjątków NullPointerException, linie UCL/LCL są poprawnie narysowane w osi Y.

---

## 🔒 3. Scenariusz Kwalifikacji GxP (CSV - Computer System Validation)

### CSV-01: Kwalifikacja Wskaźników Zdolności $C_{pk}$
*   **Cel testu:** Formalny dowód, że algorytm oceny zdolności komór chłodniczych działa poprawnie przed wdrożeniem produkcyjnym systemu.
*   **Procedura:**
    1. Import danych o temperaturze z komory chłodniczej $2-8^\circ\text{C}$ (zbiór walidacyjny zawierający przekroczenia górnej granicy).
    2. Porównanie wartości $C_{pk}$ obliczonej przez aplikację z wartością referencyjną wyliczoną w programie Minitab.
*   **Kryterium akceptacji:** Różnica $C_{pk}$ wyliczonego przez system i program Minitab wynosi mniej niż $0.001$.
