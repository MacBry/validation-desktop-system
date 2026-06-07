# Plan Implementacji - Audyt Wydajności (Rekomendacja 2)

Ten dokument opisuje techniczne szczegóły audytu wydajnościowego, strategii profilowania oraz automatycznych scenariuszy testów obciążeniowych dla silników statystycznych `DefrostCycleDetector` i `FftCalculator`.

---

## 1. Architektura i Koncepcja Wydajnościowa

W celu zapewnienia najwyższej wydajności bez wprowadzania zewnętrznych, skomplikowanych zależności bibliotecznych (np. JMH, które wymaga generowania kodu w fazie kompilacji i komplikuje budowanie aplikacji przez Maven w środowiskach CI/CD), wdrażamy natywny mikro-benchmark JUnit z mechanizmem **JIT Warmup** (rozgrzanie kompilatora Just-In-Time).

### JIT Warmup (Rozgrzanie JVM)
Maszyna wirtualna Java (JVM) na początku interpretuje kod bajtowy. Dopiero po wielokrotnym wykonaniu danej metody kompilator JIT (C1/C2) kompiluje ją do kodu maszynowego. Mikro-benchmark musi najpierw wykonać pętlę rozgrzewającą (np. 10-20 iteracji wykonania na dużych danych bez mierzenia czasu), a dopiero potem przeprowadzić właściwe pomiary czasowe.

---

## 2. Plany i Metody Profilowania

### 2.1. Profilowanie `DefrostCycleDetector` (N = 10 000+)
*   **Dane testowe:** Generowany programistycznie zbiór 10 000 punktów pomiarowych reprezentujących stabilną temperaturę z 20 nałożonymi sztucznie pikami defrostu (gwałtowny wzrost o 4.0°C trwający 30 minut, a następnie powolny spadek do bazy).
*   **Metoda:** Uruchomienie analizy w pętli.
*   **Optymalizacja (Stream Processing):** W analizie dużych serii, zamiast konwertować encje bazodanowe do obiektów typu `List<ThermoMeasurementPoint>` i wykonywać operacje na stercie, algorytm operuje bezpośrednio na prymitywach lub tablicach jednowymiarowych, co zmniejsza narzut pamięciowy.

### 2.2. Benchmark `FftCalculator` (N = 1 048 576)
*   **Dane testowe:** Tablica o długości $2^{20} = 1\ 048\ 576$ elementów wypełniona sumą trzech sinusoid o znanych częstotliwościach oraz losowym szumem białym (Gaussian noise).
*   **Metoda:** Wykonanie transformaty FFT. Algorytm Cooley-Tukey in-place redukuje użycie dodatkowej pamięci do minimum $O(N)$.

---

## 3. Scenariusze Testowe (Performance Test Scenarios)

### PTS-01: Profilowanie Detektora Cykli Odszraniania (10k punktów)
*   **Dane wejściowe:** Lista 10 000 punktów `ThermoMeasurementPoint` wygenerowana syntetycznie.
*   **Procedura:**
    1. Rozgrzanie (JIT Warmup): 10 wywołań na osobnym zestawie danych.
    2. Pomiar właściwy: 50 kolejnych wywołań.
    3. Wyznaczenie średniego czasu wykonania.
*   **Kryterium akceptacji:** Średni czas pojedynczej detekcji defrostu dla 10k punktów wynosi **< 15 ms**.

### PTS-02: Benchmark Szybkiej Transformaty Fouriera (1M punktów)
*   **Dane wejściowe:** Tablica `double[]` o rozmiarze 1 048 576 elementów.
*   **Procedura:**
    1. Rozgrzanie: 5 uruchomień transformaty FFT.
    2. Pomiar właściwy: 5 kolejnych uruchomień.
    3. Wyznaczenie minimalnego, maksymalnego i średniego czasu wykonania.
*   **Kryterium akceptacji:** Średni czas wykonania transformaty FFT dla 1M próbek wynosi **< 350 ms**.

---

## 4. Raport z Profilowania (Dokumentacja Techniczna)
Wyniki z uruchomienia testów wydajnościowych są wypisywane na konsolę w postaci ustrukturyzowanego logu metrologicznego, co pozwala na łatwy eksport wyników do systemów monitorowania wydajności (CI Pipeline).
