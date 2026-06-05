# Scenariusze Testowe - Statystyka Opisowa i Przestrzenna

Dokumentacja scenariuszy testowych (od jednostkowych po integracyjne) dla modułu statystyki opisowej i przestrzennej.

---

## 🧪 1. Testy Jednostkowe (Unit Tests)

### UT-01: Obliczanie Podstawowych Statystyk Opisowych (SensorStatsEngine)
*   **Cel testu:** Weryfikacja poprawności obliczeń podstawowych parametrów rozkładu (średnia, odchylenie standardowe, RSD, mediana).
*   **Dane wejściowe (Dataset A):**
    `double[] temperatures = { 2.0, 3.0, 4.0, 5.0, 6.0 };`
*   **Oczekiwane wyniki (Wartości Referencyjne):**
    *   Średnia ($\mu$) = `4.0`
    *   Mediana = `4.0`
    *   Wariancja ($s^2$) = `2.5`
    *   Odchylenie Standardowe ($s$) = `1.5811388300841898`
    *   Współczynnik zmienności (RSD) = `39.53%`
*   **Kryterium akceptacji:** Wyniki obliczeń są zgodne z wartościami referencyjnymi z dokładnością do $10^{-6}$.

### UT-02: Zaawansowane Statystyki Rozkładu (Skośność i Kurtoza)
*   **Cel testu:** Weryfikacja obliczeń asymetrii i spłaszczenia rozkładu.
*   **Dane wejściowe (Dataset B):**
    `double[] skewedData = { 2.0, 2.0, 2.0, 3.0, 8.0 };`
*   **Oczekiwane wyniki:**
    *   Skośność (Skewness) > 0 (rozkład skośny prawostronnie ze względu na wartość odstającą `8.0`).
*   **Kryterium akceptacji:** Prawidłowe zidentyfikowanie kierunku skośności.

### UT-03: Obsługa Przypadków Brzegowych (Edge Cases)
*   **Cel testu:** Stabilność systemu przy nietypowych danych.
*   **Scenariusz A (pusta tablica):** Przekazanie `double[] {}` -> Oczekiwany wynik: Rzucenie `IllegalArgumentException`.
*   **Scenariusz B (stała linia):** Przekazanie `{ 5.0, 5.0, 5.0 }` -> Oczekiwany wynik: $\sigma = 0.0$, $RSD = 0.0$ (brak błędu dzielenia przez zero).

---

## 🔗 2. Testy Integracyjne (Integration Tests)

### IT-01: Wyznaczanie Rozstępu Przestrzennego w czasie (Spatial Spread)
*   **Cel testu:** Weryfikacja synchronizacji czasowej sensorów i poprawnego wyliczania $\Delta T_t$ dla każdego kroku czasowego.
*   **Środowisko testowe:** Baza danych z załadowaną serią pomiarową zawierającą 3 czujniki z 10 odczytami każdy.
*   **Przebieg testu:**
    1. Uruchomienie metody `SpatialStatsService.calculateSpatialSpread()` dla wybranej serii.
    2. Pobranie wyników dla kroku $t_5$ (gdzie czujnik 1 = $2.5^\circ\text{C}$, czujnik 2 = $4.0^\circ\text{C}$, czujnik 3 = $3.2^\circ\text{C}$).
*   **Oczekiwany wynik:**
    *   Maksymalna temperatura w kroku $t_5$ = $4.0^\circ\text{C}$
    *   Minimalna temperatura w kroku $t_5$ = $2.5^\circ\text{C}$
    *   Rozstęp ($\Delta T$) = $1.5^\circ\text{C}$
*   **Kryterium akceptacji:** Poprawne wyliczenie rozstępu przestrzennego dla każdego kroku w bazie danych.

---

## 🔒 3. Scenariusz Kwalifikacji GxP (CSV - Computer System Validation)

### CSV-01: Test Dokładności Numerycznej (NIST Verification)
*   **Cel testu:** Formalne wykazanie przed audytorem zgodności obliczeń z certyfikowanym zbiorem danych referencyjnych NIST (np. zbiór danych *Michelson* lub *NumAcc4*).
*   **Procedura:**
    1. Wczytanie oficjalnego zbioru danych testowych NIST.
    2. Uruchomienie silnika statystycznego `SensorStatsEngine`.
    3. Automatyczne porównanie z oficjalnym plikiem raportu NIST.
*   **Kryterium akceptacji:** Różnica w wartości odchylenia standardowego i średniej nie może przekraczać tolerancji $\pm 10^{-9}$ (wymóg precyzji podwójnej).
