# Scenariusze Testowe - Testowanie Hipotez Statystycznych

Dokumentacja scenariuszy testowych (od jednostkowych po integracyjne) dla modułu testowania hipotez statystycznych (ANOVA, TOST, F-Test).

---

## 🧪 1. Testy Jednostkowe (Unit Tests)

### UT-01: Test Równoważności TOST (Two One-Sided Tests)
*   **Cel testu:** Weryfikacja wykrywania równoważności temperatur dwóch czujników przy zadanej granicy tolerancji $\theta = 0.5^\circ\text{C}$.
*   **Scenariusz A (Równoważne):**
    *   `double[] sensor1 = { 5.0, 5.1, 4.9, 5.0, 5.0 };` ($\bar{x}_1 = 5.00$)
    *   `double[] sensor2 = { 5.1, 5.2, 5.0, 5.1, 5.1 };` ($\bar{x}_2 = 5.10$)
    *   Oczekiwany wynik: Różnica średnich ($0.10^\circ\text{C}$) mieści się w granicach $[-0.5, 0.5]$. Hipoteza o braku równoważności odrzucona ($p < 0.05$).
*   **Scenariusz B (Nierównoważne):**
    *   `double[] sensor1 = { 5.0, 5.1, 4.9, 5.0, 5.0 };` ($\bar{x}_1 = 5.00$)
    *   `double[] sensor2 = { 5.8, 5.9, 5.7, 5.8, 5.8 };` ($\bar{x}_2 = 5.80$)
    *   Oczekiwany wynik: Różnica średnich ($0.80^\circ\text{C}$) wykracza poza granice $[-0.5, 0.5]$.
*   **Kryterium akceptacji:** Scenariusz A zwraca `equivalent = true`, Scenariusz B zwraca `equivalent = false`.

### UT-02: Jednoczynnikowa ANOVA (Analiza Wariancji)
*   **Cel testu:** Weryfikacja poprawnego porównania średnich z 3 różnych czujników (grup).
*   **Dane wejściowe:**
    *   Próba 1: `{ 4.8, 5.0, 5.2 }`
    *   Próba 2: `{ 4.9, 5.1, 5.0 }`
    *   Próba 3: `{ 8.0, 8.2, 8.1 }` (grupa znacząco cieplejsza)
*   **Oczekiwany wynik:** $p\text{-value} < 0.05$ (istotna różnica między grupami).
*   **Kryterium akceptacji:** Prawidłowe zidentyfikowanie istotności różnic ($p < 0.05$).

### UT-03: Wybór Testu (ANOVA vs. Kruskal-Wallis)
*   **Cel testu:** Automatyczne przełączanie testu przy braku normalności rozkładu.
*   **Dane wejściowe:** Próba o skrajnie zaburzonym rozkładzie (np. asymetryczne piki).
*   **Oczekiwane zachowanie:** Test Shapiro-Wilka zwraca brak normalności ($p < 0.05$) -> system automatycznie wywołuje nieparametryczny test Kruskala-Wallisa zamiast ANOVA.

---

## 🔗 2. Testy Integracyjne (Integration Tests)

### IT-01: Przepływ Analizy Hipotez z GUI do Wyniku
*   **Cel testu:** Weryfikacja pobierania danych z bazy pomiarowej i przekazywania ich do analizatora.
*   **Przebieg testu:**
    1. Użytkownik wybiera z poziomu GUI "Test Równoważności" dla czujników `SEN-01` i `SEN-02`.
    2. System pobiera odczyty z bazy danych dla wybranej serii (1000 rekordów na czujnik).
    3. Wykonywane jest obliczenie TOST.
    4. Wynik wyświetla się na ekranie w kolorze zielonym (Równoważne) lub czerwonym (Nierównoważne).
*   **Kryterium akceptacji:** Brak błędów pamięciowych (OutOfMemory) przy dużych próbach, prawidłowe wyświetlenie stanu w UI.

---

## 🔒 3. Scenariusz Kwalifikacji GxP (CSV - Computer System Validation)

### CSV-01: Walidacja Testu Równoważności (TOST) z programem R/SAS
*   **Cel testu:** Udowodnienie audytorowi farmaceutycznemu, że implementacja algorytmu TOST w Java daje dokładnie takie same wyniki jak akredytowane pakiety statystyczne (R / SAS).
*   **Procedura:**
    1. Uruchomienie skryptu R (`tost_verification.R`) na wybranym zestawie danych.
    2. Uruchomienie tego samego testu w aplikacji.
    3. Porównanie wartości p-value obu testów jednostronnych ($p_{TOST1}$, $p_{TOST2}$).
*   **Kryterium akceptacji:** Wartości $p$-value wyznaczone przez aplikację różnią się od wyników z R o nie więcej niż $10^{-5}$.
