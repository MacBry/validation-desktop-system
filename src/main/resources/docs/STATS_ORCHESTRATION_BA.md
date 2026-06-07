# Analiza Biznesowa (BA) - Orkiestracja Statystyk i Konsolidacja Raportów

## 1. Cel Biznesowy
Ujednolicenie i uproszczenie dostępu do zaawansowanych wyliczeń statystycznych dla poszczególnych kanałów pomiarowych (czujników) w procesie rewalidacji komór chłodniczych. Celem jest stworzenie jednego punktu wejścia (Orkiestratora), który automatycznie wykona pełen zestaw analiz (opisowa, testy hipotez, SPC, analiza cykliczności) i zwróci skonsolidowaną strukturę danych. Ułatwia to integrację z generatorami raportów PDF/Word oraz interfejsem graficznym JavaFX.

## 2. Perspektywa Klienta (QA Manager)
Kierownik Zapewnienia Jakości (QA Manager) w hurtowni farmaceutycznej lub laboratorium nie analizuje osobno wyników z 4 różnych narzędzi. Potrzebuje on spójnego raportu dla każdego punktu pomiarowego, który odpowiada na kluczowe pytania:
1.  **Jakie były podstawowe parametry rozkładu?** (Średnia, odchylenie standardowe, percentyle).
2.  **Czy rozkład temperatur jest rozkładem normalnym?** (Wymóg stosowania klasycznych wskaźników SPC).
3.  **Jaka jest zdolność procesu utrzymania temperatury?** (Wskaźnik $C_{pk}$ względem limitów komory).
4.  **Czy w komorze zachodziły cykle odszraniania (defrost) i jak wpłynęły na stabilność?** (Liczba cykli, ich czas trwania i amplituda).
5.  **Jaki jest dominujący okres wahań temperatury?** (Analiza FFT).

## 3. Wymagania Funkcjonalne

### REQ-ORCH-01: Serwis Jednego Punktu Wejścia (StatisticsAggregationService)
*   System musi udostępniać interfejs serwisowy Springa, który przyjmuje encję serii pomiarowej (`ThermoMeasurementSeries`).
*   Serwis musi automatycznie skoordynować wywołania algorytmów z modułów:
    *   Statystyki opisowej (wyliczenie średniej, mediany, odchylenia standardowego, wariancji, skośności, kurtozy).
    *   Testowania hipotez (wykonanie testu Jarque-Bera dla weryfikacji normalności rozkładu).
    *   Statystycznego Sterowania Procesem SPC (wyznaczenie wskaźników $C_p$ i $C_{pk}$ na podstawie limitów komory).
    *   Analizy szeregów czasowych (detekcja cykli defrostu oraz transformata FFT z wyznaczeniem dominującej częstotliwości i okresu wahań).

### REQ-ORCH-02: Skonsolidowana struktura danych (StatsReportDTO)
*   Dane muszą być zwracane jako jeden, kompletny obiekt DTO (`StatsReportDTO`).
*   DTO musi zawierać precyzyjne wskaźniki gotowe do natychmiastowego renderowania w raportach PDF (np. automatyczna flaga zdolności procesu: `capable` / `uncapable` na podstawie $C_{pk} \ge 1.33$).

## 4. Kryteria Akceptacji (GxP Acceptance Criteria)
*   **AC-ORCH-01:** Obiekt `StatsReportDTO` musi zawierać kompletne dane dla każdego z 4 analizowanych modułów. Brak danych w którymkolwiek z modułów skutkuje niepełnym raportem, co jest niedozwolone.
*   **AC-ORCH-02:** Serwis agregujący musi obsługiwać sytuację, w której dane wejściowe nie posiadają zdefiniowanych limitów komory (wówczas wskaźniki $C_p$ / $C_{pk}$ powinny być pomijane lub zwracać bezpieczne wartości domyślne, zapobiegając przerwaniu generowania raportu).
*   **AC-ORCH-03:** Wynik analizy widmowej FFT w DTO musi wskazywać dominujący okres drgań w jednostkach czasu (np. w minutach lub godzinach), co pozwala na bezpośrednie przełożenie matematycznej częstotliwości na fizyczny cykl pracy sprężarki (np. "cykl sprężarki co 25 minut").
