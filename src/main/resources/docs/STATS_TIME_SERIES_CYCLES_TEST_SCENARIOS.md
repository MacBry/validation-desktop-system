# Scenariusze Testowe - Analiza Szeregów Czasowych i Cykli

Dokumentacja scenariuszy testowych (od jednostkowych po integracyjne) dla modułu analizy szeregów czasowych, detekcji cykli defrostu oraz transformaty FFT.

---

## 🧪 1. Testy Jednostkowe (Unit Tests)

### UT-01: Weryfikacja Spektrum FFT (Szybka Transformata Fouriera)
*   **Cel testu:** Potwierdzenie, że autorski algorytm FFT (Cooley-Tukey) prawidłowo identyfikuje dominującą częstotliwość (okres drgań).
*   **Dane wejściowe:** Wygenerowana cyfrowo sinusoida o znanym okresie 60 minut, nałożona na stałą temperaturę $5.0^\circ\text{C}$, próbkowana co 1 minutę (długość zbioru = 128 punktów - potęga 2):
    $$T(t) = 5.0 + 1.5 \sin\left(\frac{2\pi t}{60}\right)$$
*   **Oczekiwany wynik:**
    *   Wykrycie piku amplitudy w widmie FFT na indeksie odpowiadającym okresowi 60 minut.
*   **Kryterium akceptacji:** Indeks o najwyższej wartości w spektrum FFT po przekształceniu na okres w minutach zwraca wartość $60 \pm 2$ minuty.

### UT-02: Weryfikacja Zero-Paddingu w FFT
*   **Cel testu:** Sprawdzenie, czy algorytm radzi sobie z tablicą wejściową, której długość nie jest potęgą liczby 2.
*   **Dane wejściowe:** Zbiór o długości 100 punktów.
*   **Oczekiwane zachowanie:** Klasa `FftCalculator` automatycznie rozszerza tablicę do długości 128 (dopełniając brakujące 28 elementów zerami) przed wykonaniem algorytmu FFT.
*   **Kryterium akceptacji:** Brak wyjątków IndexOutOfBoundsException, pomyślne zwrócenie widma amplitudy.

### UT-03: Detekcja Cykli Odszraniania (DefrostCycleDetector)
*   **Cel testu:** Weryfikacja algorytmu wykrywania pików defrostu.
*   **Dane wejściowe:** Szereg czasowy o stałej temperaturze $5.0^\circ\text{C}$ z nałożonym nagłym pikiem w $t_{50}$ (temperatura rośnie w ciągu 5 minut do $12.0^\circ\text{C}$, po czym spada do $5.0^\circ\text{C}$).
*   **Oczekiwany wynik:**
    *   Wykrycie dokładnie 1 cyklu defrostu.
    *   Czas rozpoczęcia: $t_{50}$
    *   Czas trwania: ok. 10 minut.
    *   Maksymalna temperatura cyklu: $12.0^\circ\text{C}$
*   **Kryterium akceptacji:** Zwrócenie listy zawierającej 1 poprawny obiekt `DefrostCycle`.

---

## 🔗 2. Testy Integracyjne (Integration Tests)

### IT-01: Automatyczna Ocena Cykli na Danych z Logera Testo 184
*   **Cel testu:** Weryfikacja działania modułu przy pełnym imporcie raportu CSV z logera Testo 184.
*   **Przebieg testu:**
    1. Import pliku CSV z logera Testo 184 (symulacja 3 dni pracy komory chłodniczej z defrostami co 8 godzin).
    2. Uruchomienie pełnego parsera i przekazanie danych do `DefrostCycleDetector`.
*   **Oczekiwany wynik:**
    *   Wykrycie 9 cykli odszraniania w ciągu 72 godzin.
*   **Kryterium akceptacji:** Cały proces (od odczytu pliku po zapis cykli w bazie danych) przechodzi bez błędów.

---

## 🔒 3. Scenariusz Kwalifikacji GxP (CSV - Computer System Validation)

### CSV-01: Kwalifikacja Algorytmu FFT i Detekcji Cykli
*   **Cel testu:** Walidacja oprogramowania przed audytem GMP w celu wykazania poprawności analizy spektralnej FFT.
*   **Procedura:**
    1. Porównanie widma amplitudy wyliczonego przez aplikację `validation-desktop` z widmem wyliczonym przy pomocy programu MATLAB (funkcja `fft()`).
*   **Kryterium akceptacji:** Amplitudy i częstotliwości dla 3 pierwszych dominujących składowych harmonicznych w obu systemach różnią się o nie więcej niż $0.0001$.
