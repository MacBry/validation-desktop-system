# Analiza Biznesowa (BA) - Audyt Wydajności Modułu Statystycznego

## 1. Cel Biznesowy
Zapewnienie niezawodności, responsywności i stabilności aplikacji `validation-desktop` podczas importu i przetwarzania bardzo dużych serii danych temperaturowych. W miarę wydłużania czasu trwania rewalidacji (np. mapowanie 72-godzinne lub 7-dniowe) lub zagęszczania interwału rejestracji (np. pomiar co 1 minutę), liczba rekordów drastycznie rośnie. Audyt wydajności i optymalizacja algorytmów gwarantują, że aplikacja nie ulegnie zawieszeniu (UI freeze) i wygeneruje wyniki w czasie akceptowalnym dla użytkownika.

## 2. Kontekst Walidacyjny i Regulacyjny (GxP / GAMP 5)
W farmacji i logistyce medycznej, integralność danych (Data Integrity) oraz dostępność systemu (System Availability) są krytycznymi czynnikami:
*   **Wielodniowe badania (Continuous Monitoring):** Rejestratory Testo 174T oraz Testo 184 mają pamięć pozwalającą na zapis odpowiednio 16 000 i 40 000 odczytów. Przetwarzanie takich zbiorów bez optymalizacji pamięciowej może doprowadzić do przekroczenia sterty JVM (OutOfMemoryError), co skutkuje utratą integralności sesji w krytycznym momencie.
*   **Czas odpowiedzi systemu (Responsiveness):** Zgodnie z wytycznymi ergonomii oprogramowania GAMP 5, czas generowania raportów i wykonywania obliczeń statystycznych w obecności użytkownika nie powinien przekraczać kilku sekund, aby zapobiec przerwaniu pracy.
*   **Złożoność obliczeniowa:** Szybka Transformata Fouriera (FFT) charakteryzuje się złożonością $O(N \log N)$, podczas gdy detekcja cykli odszraniania w najgorszym scenariuszu (Nested Loops) może dążyć do $O(N^2)$. Audyt ma na celu potwierdzenie stabilności tych algorytmów.

## 3. Wymagania Funkcjonalne i Wydajnościowe

### REQ-PERF-01: Wydajność Detekcji Cykli (DefrostCycleDetector)
*   System musi przetworzyć serię pomiarową o długości co najmniej 10 000 punktów w czasie krótszym niż **100 ms**.
*   Algorytm nie może tworzyć nadmiarowych obiektów w pętli w celu uniknięcia presji na Garbage Collector (GC).

### REQ-PERF-02: Skalowalność Transformaty Fouriera (FftCalculator)
*   Aplikacja musi wykonać analizę widmową FFT dla zbioru o rozmiarze $10^6$ (1 048 576 próbki) w czasie poniżej **500 ms** (po rozgrzaniu JIT).
*   Wykorzystany algorytm musi radzić sobie z dowolnymi rozmiarami danych wejściowych bez rzucania wyjątków przepełnienia stosu (StackOverflowError).

### REQ-PERF-03: Asynchroniczność i Brak Blokowania UI
*   Wszelkie operacje obliczeniowe trwające powyżej **200 ms** muszą być realizowane poza wątkiem JavaFX Application Thread (za pomocą `Task<V>` lub `CompletableFuture`).

## 4. Kryteria Akceptacji (GxP Performance Verification)
*   **AC-PERF-01:** Testy obciążeniowe (Load Tests) na syntetycznych i realnych zbiorach danych potwierdzą, że czasy wykonania algorytmów rosną liniowo-logarytmicznie, a nie wykładniczo.
*   **AC-PERF-02:** Pomiar zużycia pamięci wykaże brak wycieków pamięci (Memory Leaks) przy wielokrotnym uruchamianiu analizy dla dużych plików pomiarowych.
