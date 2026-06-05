# Analiza Biznesowa (BA) - Analiza Szeregów Czasowych i Cykliczności

## 1. Cel Biznesowy
Umożliwienie automatycznej identyfikacji cykli pracy urządzeń grzewczo-chłodniczych (sprężarki, cykle odszraniania parownika) oraz trendów środowiskowych. Pozwala to na wczesne wykrywanie anomalii mechanicznych (np. wydłużanie cykli chłodzenia) oraz zapobieganie awariom sprzętu (konserwacja predykcyjna - Predictive Maintenance).

## 2. Kontekst Walidacyjny
W walidacji urządzeń chłodniczych (np. lodówek farmaceutycznych, szaf chłodniczych) kluczowe są dwa zjawiska:
*   **Cykl Odszraniania (Defrost cycle):** Podczas odszraniania temperatura parownika gwałtownie rośnie, co powoduje krótkotrwałe podwyższenie temperatury powietrza w komorze. Konieczne jest wykazanie, że czas i amplituda tego wzrostu nie zagrażają przechowywanemu produktowi.
*   **Histereza Sterownika (Compressor cycle):** Temperatura oscyluje wokół punktu nastawy. Analiza tych oscylacji pozwala ocenić stan zużycia sprężarki oraz poprawność nastawienia parametrów PID sterownika.

## 3. Wymagania Funkcjonalne

### REQ-01: Autodetekcja Cykli Odszraniania (Defrost)
System musi automatycznie wykrywać cykle odszraniania na wykresie temperatury poprzez:
*   Analizę pochodnej temperatury po czasie (szybkość wzrostu $\frac{dT}{dt}$).
*   Wykrywanie charakterystycznych pików o określonej minimalnej wysokości i czasie trwania.
*   **Wyliczanie parametrów cyklu:** Średni czas trwania odszraniania, maksymalny pik temperatury podczas cyklu, częstotliwość występowania (np. co 6 godzin).

### REQ-02: Dekompozycja Szeregu Czasowego
Rozbicie surowego sygnału pomiarowego na trzy składowe:
*   **Trend (Trend):** Długoterminowa zmiana (np. powolny wzrost temperatury średniej w ciągu 72 godzin świadczący o ubytku czynnika chłodniczego).
*   **Sezonowość / Cykliczność (Seasonal):** Regularne wahania temperatury związane z pracą agregatu lub cyklem dobowym.
*   **Szum / Reszty (Residuals):** Losowe zakłócenia pomiarowe.

### REQ-03: Analiza Częstotliwościowa (FFT - Fast Fourier Transform)
*   Przekształcenie sygnału temperatury do dziedziny częstotliwości za pomocą algorytmu FFT w celu identyfikacji dominującego okresu drgań temperatury.
*   Wykrywanie nieprawidłowości: np. jeśli okres cyklu sprężarki ulega skróceniu (częstsze włączanie/wyłączanie), wskazuje to na uszkodzenie czujnika temperatury lub sterownika (zjawisko tzw. taktowania sprężarki, które drastycznie skraca jej żywotność).

## 4. Kryteria Akceptacji (GxP)
*   **AC-01:** Maksymalna temperatura w trakcie cyklu odszraniania nie może przekroczyć krytycznej granicy (np. $+8.0^\circ\text{C}$ dla komory chłodniczej) przez czas dłuższy niż zdefiniowany w kryteriach (np. maks. 15 minut).
*   **AC-02:** System musi automatycznie generować ostrzeżenie, jeśli wykryty okres cykli pracy agregatu ulegnie zmianie o więcej niż 30% w porównaniu do fazy bazowej.
