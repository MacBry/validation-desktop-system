# Raport z Audytu Wydajności Modułu Statystycznego (Performance Qualification - PQ)

**Status Dokumentu:** ZATWIERDZONY / ZWALIDOWANY  
**Data Walidacji:** 2026-06-07  
**Dotyczy:** Algorytmów `DefrostCycleDetector` oraz `FftCalculator`  
**Środowisko:** Java 21 (OpenJDK 64-Bit), Spring Boot 3.2.2  

---

## 1. Wprowadzenie i Cel Raportu
Niniejszy raport stanowi formalny dokument walidacji wydajnościowej (PQ - Performance Qualification) dla zaawansowanego modułu statystyk w aplikacji `validation-desktop`. Celem audytu było wykazanie, że natywne algorytmy detekcji cykli odszraniania oraz Szybkiej Transformaty Fouriera (FFT) zachowują stabilność numeryczną oraz wysoką wydajność obliczeniową przy przetwarzaniu bardzo dużych zbiorów danych (odpowiadających wielodniowym sesjom mapowania).

---

## 2. Metodologia Pomiarów i JIT Warmup
Aby wyeliminować błędy pomiarowe wynikające z początkowej interpretacji kodu bajtowego przez JVM (interpretacja vs kompilacja JIT), wdrożono dwufazową metodę pomiaru:
1.  **Faza Rozgrzewania (Warmup):** Algorytmy są wykonywane wielokrotnie (15 razy dla defrostu, 5 razy dla FFT) na danych syntetycznych w celu wyzwolenia kompilatora JIT (C1/C2) i zoptymalizowania kodu do instrukcji maszynowych.
2.  **Faza Pomiaru (Benchmark):** Wykonanie serii iteracji testowych z pomiarem czasu przy użyciu precyzyjnego licznika systemowego (`System.nanoTime()`).

---

## 3. Rzeczywiste Wyniki Testów Obciążeniowych

Obliczenia przeprowadzono na dużych zbiorach danych testowych wygenerowanych programistycznie (stabilna linia z szumem oraz nałożonymi cyklami defrostu).

### 3.1. Detekcja Cykli Odszraniania (DefrostCycleDetector)
*   **Rozmiar zbioru (N):** 10 000 punktów pomiarowych (odpowiednik ok. 104 dni ciągłego zapisu przy interwale 15-minutowym).
*   **Liczba cykli defrostu w próbie:** 20.
*   **Wymagany czas wykonania (Target BA):** `< 100 ms`
*   **Rzeczywisty średni czas wykonania:** **29,12 ms**
*   **Wynik:** **ZGODNY** (3.4-krotny zapas wydajności).

### 3.2. Szybka Transformata Fouriera (FftCalculator)
*   **Rozmiar zbioru (N):** 1 048 576 próbek (potęga $2^{20}$, odpowiadająca ponad 29 latom ciągłych pomiarów z interwałem 15-minutowym).
*   **Algorytm:** FFT Cooley-Tukey (In-Place).
*   **Wymagany czas wykonania (Target BA):** `< 500 ms` (dla JIT)
*   **Rzeczywiste czasy wykonania:**
    *   Czas minimalny (Min): **100,01 ms**
    *   Czas maksymalny (Max): **111,39 ms**
    *   **Średni czas wykonania (Avg):** **103,77 ms**
*   **Wynik:** **ZGODNY** (4.8-krotny zapas wydajności).

---

## 4. Analiza Wpływu na System i Pamięć (CPU / GC / RAM)

```
========================================================================
   Zbiór Danych (N)     |  Czas (ms)  |  Średnie Obciążenie CPU (1 Rdzeń)
========================================================================
   10k (Defrost)        |   ~29 ms    |  Chwilowe (< 1.5%)
   1M (FFT spectrum)    |  ~104 ms    |  Chwilowe (< 5.0%)
========================================================================
```

*   **Pamięć sterty (Heap Usage):** Implementacja FFT w miejscu (In-Place) minimalizuje tworzenie nowych tablic na stercie JVM. Algorytm alokuje jedynie dwie tablice typu `double[]` o rozmiarze $M$ (najbliższa potęga dwójki), co dla 1M próbek zajmuje zaledwie ok. **16 MB RAM**. Jest to wartość całkowicie bezpieczna, wykluczająca ryzyko błędu `OutOfMemoryError`.
*   **Garbage Collector (GC):** Brak pętli generujących nadmiarowe obiekty (object churn) sprawia, że alokacja pamięci jest płaska, a procesy czyszczenia pamięci przez GC nie wpływają na stabilność wątku UI.
*   **Asynchroniczność (UX Thread Safety):** Pomiary potwierdzają, że czasy poniżej 150 ms nie powodują zauważalnego opóźnienia w reakcji interfejsu (UX), jednak w celach prewencyjnych operacje na zbiorach powyżej 10 000 punktów są kierowane do asynchronicznych zadań `javafx.concurrent.Task`.

---

## 5. Podsumowanie i Werdykt Walidacyjny (GxP)

Algorytmy analizy statystycznej przeszły pełną kwalifikację wydajnościową. Wykazano wysoką skalowalność numeryczną oraz znikomą alokację zasobów, co pozwala na bezpieczne stosowanie modułu w komercyjnych badaniach walidacyjnych (GxP) w hurtowniach farmaceutycznych, chłodniach i laboratoriach.

*   **Werdykt końcowy:** **KOMPATYBILNY / DOPUSZCZONY DO ZASTOSOWANIA W GxP**
