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
*   **Cel testu:** Weryfikacja obliczeń asymetrii i spłaszczenia rozkładu przy użyciu próbkowego wzoru Fisher-Pearson.
*   **Dane wejściowe (Dataset B):**
    `double[] skewedData = { 2.0, 2.0, 2.0, 3.0, 8.0 };`
*   **Oczekiwane wyniki (Wartości Referencyjne):**
    *   Średnia ($\bar{x}$) = `3.4`
    *   Odchylenie standardowe ($s$) = `2.6076809620810595`
    *   Skośność ($g_1$) $\approx$ `2.0922` (rozkład silnie skośny prawostronnie ze względu na wartość odstającą `8.0`).
    *   Kurtoza ($g_2$) $\approx$ `4.4161` (rozkład leptokurtyczny, excess kurtosis > 0).
*   **Kryterium akceptacji:** Skośność i kurtoza obliczone przez `SensorStatsEngine` są zgodne z wartościami referencyjnymi z dokładnością do $\pm 10^{-4}$ (oraz prawidłowo wykazany kierunek skośności $g_1 > 0$ i spłaszczenia $g_2 > 0$).

### UT-03: Obsługa Przypadków Brzegowych (Edge Cases)
*   **Cel testu:** Stabilność systemu przy nietypowych danych oraz matematycznie niezdefiniowanych rozmiarach próby.
*   **Scenariusz A (pusta tablica):** Przekazanie `double[] {}` -> Oczekiwany wynik: Rzucenie `IllegalArgumentException`.
*   **Scenariusz B (stała linia):** Przekazanie `{ 5.0, 5.0, 5.0 }` -> Oczekiwany wynik: $\sigma = 0.0$, $RSD = 0.0$ (brak błędu dzielenia przez zero).
*   **Scenariusz C (zbyt mała próba dla skośności):** Przekazanie tablicy o długości $N < 3$ (np. `{ 1.0, 2.0 }`) do `calculateSkewness` -> Oczekiwany wynik: `Double.NaN` i zalogowanie ostrzeżenia (warning).
*   **Scenariusz D (zbyt mała próba dla kurtozy):** Przekazanie tablicy o długości $N < 4$ (np. `{ 1.0, 2.0, 3.0 }`) do `calculateKurtosis` -> Oczekiwany wynik: `Double.NaN` i zalogowanie ostrzeżenia (warning).

---

## 🔗 2. Testy Integracyjne (Integration Tests)

### IT-01: Wyznaczanie Rozstępu Przestrzennego w czasie (Spatial Range)
*   **Cel testu:** Weryfikacja synchronizacji czasowej sensorów i poprawnego wyliczania $\Delta T_t$ dla każdego kroku czasowego.
*   **Środowisko testowe:** Baza danych z załadowaną serią pomiarową zawierającą 3 czujniki z 10 odczytami każdy.
*   **Przebieg testu:**
    1. Uruchomienie metody `SpatialStatsService.calculateSpatialStats()` dla wybranej serii.
    2. Pobranie wyników dla kroku $t_5$ (gdzie czujnik 1 = $2.5^\circ\text{C}$, czujnik 2 = $4.0^\circ\text{C}$, czujnik 3 = $3.2^\circ\text{C}$).
*   **Oczekiwany wynik:**
    *   Maksymalna temperatura w kroku $t_5$ = $4.0^\circ\text{C}$
    *   Minimalna temperatura w kroku $t_5$ = $2.5^\circ\text{C}$
    *   Rozstęp ($\Delta T$) = $1.5^\circ\text{C}$
*   **Kryterium akceptacji:** Poprawne wyliczenie rozstępu przestrzennego dla każdego kroku w bazie danych (wynik zapisany jako `SpatialStatsResult`).

---

## 🔒 3. Scenariusz Kwalifikacji GxP (CSV - Computer System Validation)

### CSV-01: Test Dokładności Numerycznej (NIST Verification)
*   **Cel testu:** Formalne wykazanie przed audytorem zgodności obliczeń z certyfikowanym zbiorem danych referencyjnych NIST (zbiór danych **NumAcc1-4** ze standardowych zestawów referencyjnych NIST dla statystyk opisowych).
*   **Procedura:**
    1. Wczytanie oficjalnego zbioru danych testowych NIST *NumAcc1-4* zawierającego certyfikowane wartości średniej, odchylenia standardowego i wariancji.
    2. Uruchomienie silnika statystycznego `SensorStatsEngine` na tych danych.
    3. Automatyczne porównanie wyliczonych parametrów z wartościami certyfikowanymi NIST.
*   **Kryterium akceptacji:** Różnica w wartości odchylenia standardowego i średniej nie może przekraczać tolerancji $\pm 10^{-9}$ (pełna zgodność numeryczna w precyzji podwójnej).
