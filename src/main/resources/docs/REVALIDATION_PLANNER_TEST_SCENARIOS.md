# SCENARIUSZE TESTOWE (TEST SCENARIOS)
## Moduł: Inteligentny Planer Rewalidacji Okresowych i Mapowań (Revalidation & Mapping Scheduler)
**System**: `validation-desktop` (Spring Boot 3.5 / JUnit 5 / AssertJ / Mockito / Testcontainers)  
**Lokalizacja**: `src/main/resources/docs/REVALIDATION_PLANNER_TEST_SCENARIOS.md`  
**Data opracowania**: 2026-07-28  

---

## 1. Grupa 1: Testy Macierzy Zapotrzebowania na Rejestratory (Reguły R1 & R2)

### ST-R1-01: Weryfikacja alokacji dla komory klasy SMALL
* **Warunki wstępne**: Aktywna komora `VolumeCategory.SMALL` (objętość $0.2\,\text{m}^3$, np. chłodziarka podblatowa).
* **Krok**: Wywołanie `RevalidationSchedulerEngine.calculateRequiredLoggers(chamber, PERIODIC_REVALIDATION)`.
* **Oczekiwany rezultat**: Metoda zwraca dokładnie **2 rejestratory**.

### ST-R1-02: Agregacja alokacji na poziomie urządzenia 2-komorowego
* **Warunki wstępne**: Urządzenie (`CoolingDevice`) z dwiema komorami klasy `SMALL` (chłodziarko-zamrażarka).
* **Krok**: Wywołanie `calculateRequiredLoggers(chamber, PERIODIC_REVALIDATION)` dla każdej komory i zsumowanie na poziomie urządzenia.
* **Oczekiwany rezultat**: Każda komora zwraca **2**, suma dla urządzenia = **4 rejestratory** (po 2 na komorę).

### ST-R1-03: Weryfikacja alokacji dla komory klasy MEDIUM
* **Warunki wstępne**: Komora `VolumeCategory.MEDIUM` (szafa chłodnicza, objętość $5\,\text{m}^3$; zakres 2–20 m³).
* **Krok**: Wywołanie `calculateRequiredLoggers(chamber, PERIODIC_REVALIDATION)`.
* **Oczekiwany rezultat**: Metoda zwraca dokładnie **4 rejestratory**.

### ST-R1-04: Weryfikacja alokacji dla komory klasy LARGE (walk-in)
* **Warunki wstępne**: Komora `VolumeCategory.LARGE` (komora/walk-in, objętość $>20\,\text{m}^3$).
* **Krok**: Wywołanie `calculateRequiredLoggers(chamber, PERIODIC_REVALIDATION)`.
* **Oczekiwany rezultat**: Metoda zwraca dokładnie **8 rejestratorów**.

### ST-R1-05: Pokrycie minimalnej liczby punktów pomiarowych przez kanały
* **Warunki wstępne**: Komora `VolumeCategory.SMALL` (min. **9** punktów pomiarowych wg PDA TR-64); dostępne rejestratory wielokanałowe.
* **Krok**: Alokacja 2 rejestratorów do zadania.
* **Oczekiwany rezultat**: Suma kanałów przydzielonych rejestratorów **≥ 9**; w przeciwnym razie silnik zgłasza `InsufficientMeasurementPointsException` i żąda dodatkowych rejestratorów/kanałów.

### ST-R2-01: Obowiązek mapowania dla materiału krytycznego (KKCZ)
* **Warunki wstępne**: Komora z przypisanym materiałem `KKCZ` (`requiresMapping = TRUE`).
* **Krok**: Generowanie harmonogramu 5-letniego.
* **Oczekiwany rezultat**: Planer generuje coroczne rewalidacje okresowe oraz **pełne mapowanie 5-letnie z 8 rejestratorami**.

### ST-R2-02: Zwolnienie z mapowania dla odczynników
* **Warunki wstępne**: Komora z przypisanymi odczynnikami (`MaterialType.requiresMapping = FALSE`).
* **Krok**: Wywołanie `calculateRequiredLoggers(device, MAPPING)`.
* **Oczekiwany rezultat**: Metoda zwraca **0 rejestratorów** (mapowanie 5-letnie pomijane).

---

## 2. Grupa 2: Testy Kwalifikacji Metrologicznej i Świadectw PCA (Reguły W1 & W8)

### ST-W8-01: Blokada rejestratora skalibrowanego w złym zakresie temperatur (Test Przykładu KKCZ)
* **Warunki wstępne**: 
  * Komora przechowywania KKCZ ($+2.0^{\circ}\text{C} \dots +6.0^{\circ}\text{C}$).
  * Rejestrator Testo ze świadectwem wzorcowania PCA wyłącznie na $[-30.0, -20.0]^{\circ}\text{C}$.
* **Krok**: Próba alokacji rejestratora do zadania walidacji komory KKCZ.
* **Oczekiwany rezultat**: Silnik wyrzuca wyjątek `MetrologicalRangeMismatchException`. Rejestrator zostaje odrzucony.

### ST-W8-02: Akceptacja rejestratora o prawidłowym zakresie wzorcowania
* **Warunki wstępne**: 
  * Komora przechowywania KKCZ ($+2.0^{\circ}\text{C} \dots +6.0^{\circ}\text{C}$).
  * Rejestrator ze świadectwem wzorcowania PCA w punktach $0.0^{\circ}\text{C}, +5.0^{\circ}\text{C}, +10.0^{\circ}\text{C}$ ($[0.0, 10.0]^{\circ}\text{C}$).
* **Krok**: Próba alokacji rejestratora.
* **Oczekiwany rezultat**: Rejestrator przechodzi walidację W8 i zostaje przypisany do zadania.

### ST-W8-03: Częściowe pokrycie zakresu materiału (przypadek brzegowy)
* **Warunki wstępne**:
  * Komora KKCZ ($+2.0^{\circ}\text{C} \dots +6.0^{\circ}\text{C}$).
  * Rejestrator ze świadectwem PCA na zakresie $[+3.0, +8.0]^{\circ}\text{C}$ — **nie** pokrywa dolnej granicy $+2.0^{\circ}\text{C}$.
* **Krok**: Próba alokacji rejestratora.
* **Oczekiwany rezultat**: Silnik wyrzuca `MetrologicalRangeMismatchException` (wymagane pełne pokrycie: $T_{min}^{cal} \le T_{min}^{mat}$ **oraz** $T_{max}^{cal} \ge T_{max}^{mat}$). Rejestrator odrzucony.

### ST-W8-04: Materiał mrożony na granicy zakresu (FFP)
* **Warunki wstępne**: Komora FFP ($\le -25.0^{\circ}\text{C}$, efektywny zakres materiału do $-25.0^{\circ}\text{C}$); rejestrator PCA $[-30.0, -20.0]^{\circ}\text{C}$.
* **Krok**: Walidacja W8 dla granicy $-25.0^{\circ}\text{C}$.
* **Oczekiwany rezultat**: Zakres $[-30, -20]$ pokrywa $-25.0^{\circ}\text{C}$ (inclusive) → rejestrator **zaakceptowany**. Test dokumentuje semantykę granicy jako domkniętą (`<=`).

### ST-W1-01: Wygaśnięcie świadectwa wzorcowania w trakcie pomiaru
* **Warunki wstępne**: Świadectwo wzorcowania rejestratora wygasa za 3 dni. Mapowanie zaplanowane na 5 dni.
* **Krok**: Walidacja reguły W1.
* **Oczekiwany rezultat**: Wyrzucenie wyjątku `CalibrationExpiredException`. Rejestrator wykluczony.

---

## 3. Grupa 3: Testy Rytmu Pracy Operatora i Wyliczania Opóźnienia Startu (W3, W9 & Delayed Start)

### ST-W9-01: Przesunięcie akcji manualnej poza godzinami pracy (06:30–13:30)
* **Warunki wstępne**: Wyliczona wstępnie data Krok 5 (Wyjęcie) przypada na godzinę 16:00 w piątek.
* **Krok**: Uruchomienie przeliczania w `OperatorCalendarService`.
* **Oczekiwany rezultat**: Planer przesuwa opóźnienie startu tak, aby Krok 5 wypadł w poniedziałek o godz. **06:30**.

### ST-W9-02: Wykluczenie weekendów i urlopów operatora
* **Warunki wstępne**: Zadeklarowany urlop operatora w dniach 01.08.2026 – 07.08.2026.
* **Krok**: Generowanie planu zadań na sierpień.
* **Oczekiwany rezultat**: Żadna akcja manualna (Krok 1, Krok 2, Krok 5) nie zostaje zaplanowana w okresie urlopowym.

### ST-DELAY-01: Matematyczna weryfikacja opóźnienia startu Testo
* **Warunki wstępne**: Krok 2 (Umieszczenie) = 20 min, Krok 3 (Stabilizacja) = 6 godzin (360 min).
* **Krok**: Wywołanie `TestoDelayCalculatorService.calculateStartDelay(config)`.
* **Oczekiwany rezultat**: Zwrócona wartość to dokładnie **380 minut** (6h 20m). Pierwszy pomiar w czujniku następuje tuż po stabilizacji.

### ST-DELAY-02: Krok 1 (programowanie) NIE wchodzi do opóźnienia startu
* **Warunki wstępne**: Krok 1 (Programowanie) = 10 min, Krok 2 = 20 min, Krok 3 = 360 min.
* **Krok**: Wywołanie `TestoDelayCalculatorService.calculateStartDelay(config)`.
* **Oczekiwany rezultat**: Wynik = **380 min** (bez 10 min programowania — zegar opóźnienia rusza dopiero po zaprogramowaniu, na starcie transportu). Zmiana Kroku 1 nie wpływa na wynik.

---

## 4. Grupa 4: Testy Reaktywności Zdarzeniowej i Nieplanowanego L4 (Reguła W10)

### ST-L4-01: Obsługa nieplanowanej absencji L4 dla zadań w trakcie pomiaru
* **Warunki wstępne**: Rejestratory w komorze w Kroku 4 (Pomiar GxP). Zgłoszenie L4 na 3 dni.
* **Krok**: Wywołanie `PlannerEventNotificationBridge.handleUnplannedAbsence(l4Event)`.
* **Oczekiwany rezultat**: 
  * Rejestratory dokonują bezpiecznego zapisu do końca pamięci (`Stop when full`).
  * Krok 5 (Wyjęcie i Odczyt USB) zostaje ustawiony na godz. 06:30 w pierwszym dniu po powrocie z L4.
  * Zostaje utworzony rekord w ścieżce audytowej (Audit Trail).

### ST-EVENT-01: Aktualizacja po raporcie REWALIDACJI OKRESOWEJ (właściwy zegar)
* **Warunki wstępne**: Zatwierdzenie raportu PDF dla procedury `PERIODIC_REVALIDATION`. `lastMappingDate` = 2024-01-10 (mapowanie sprzed 1,5 roku, wciąż ważne).
* **Krok**: Emisja zdarzenia `RevalidationReportGeneratedEvent(type = PERIODIC_REVALIDATION)`.
* **Oczekiwany rezultat**:
  * Zaktualizowane zostaje **wyłącznie** pole `CoolingChamber.lastPeriodicRevalidationDate` = bieżąca data.
  * Pole `lastMappingDate` pozostaje **niezmienione** (2024-01-10) — 5-letni cykl mapowania nie zostaje zresetowany.
  * Planer przelicza kolejny termin rewalidacji na +12 miesięcy i zwalnia rejestratory (usuwa `PlannedTaskRecorderAssignment`).

### ST-EVENT-02: Aktualizacja po raporcie MAPOWANIA (5-letni zegar)
* **Warunki wstępne**: Zatwierdzenie raportu PDF dla procedury `MAPPING`.
* **Krok**: Emisja zdarzenia `RevalidationReportGeneratedEvent(type = MAPPING)`.
* **Oczekiwany rezultat**: Zaktualizowane zostaje **wyłącznie** `lastMappingDate` = bieżąca data; kolejne mapowanie zaplanowane na +5 lat. `lastPeriodicRevalidationDate` bez zmian.

---

## 5. Grupa 5: Pojemność Puli, Kolizje i Terminy Krytyczne (W2, W4, W5, W6, W7)

### ST-W2-01: Wyczerpanie puli wolnych rejestratorów w oknie czasowym
* **Warunki wstępne**: Komora `LARGE` wymaga 8 rejestratorów; w oknie czasowym wolnych (o właściwym zakresie PCA) jest tylko 6.
* **Krok**: Wywołanie `RecorderAllocationService.allocateRecorders(task, chamber)`.
* **Oczekiwany rezultat**: Silnik zgłasza `InsufficientRecorderCapacityException`; zadanie zostaje oznaczone `PLANNED` z flagą braku zasobów i propozycją najbliższego wolnego okna (nie tworzy się częściowa, niekompletna alokacja).

### ST-W5-01: Blokada podwójnej rezerwacji tego samego rejestratora
* **Warunki wstępne**: Rejestrator `SN-001` przypisany do zadania A na okno $[reserved\_from, reserved\_until]$. Zadanie B próbuje użyć `SN-001` w oknie nakładającym się (z uwzględnieniem buforu logistycznego).
* **Krok**: Alokacja rejestratora do zadania B.
* **Oczekiwany rezultat**: Wykrycie kolizji po indeksie `idx_ptra_recorder_window`; rejestrator odrzucony dla zadania B (`RecorderDoubleBookingException`).

### ST-W5-02: Brak kolizji przy oknach rozłącznych z buforem
* **Warunki wstępne**: Zadanie A kończy odczyt (Krok 5) o 10:00; zadanie B startuje po upływie buforu logistycznego (np. następnego dnia roboczego).
* **Krok**: Alokacja `SN-001` do zadania B.
* **Oczekiwany rezultat**: Brak kolizji — rejestrator przypisany do zadania B.

### ST-W6-01: Priorytet komory z przekroczonym terminem (no-gap deadline)
* **Warunki wstępne**: Dwie komory; komora X ma `dueDate` w przeszłości (walidacja wygasła), komora Y w przyszłości. Ograniczona pula rejestratorów.
* **Krok**: `generateYearlySchedule`.
* **Oczekiwany rezultat**: Komora X zostaje zaplanowana jako **pierwsza** (najwcześniejsze wolne okno) i oznaczona jako przeterminowana; badanie planowane przed wygaśnięciem tam, gdzie to jeszcze możliwe.

### ST-W7-01: Alert braku odczytu po przekroczeniu buforu Kroku 5
* **Warunki wstępne**: Zadanie w statusie `READOUT_PENDING`; minął `planned_step5_readout_deadline`, brak importu danych z Testo.
* **Krok**: Przebieg zadania monitorującego terminy odczytu.
* **Oczekiwany rezultat**: Wygenerowany alert (W7), status pozostaje `READOUT_PENDING`, wpis w Audit Trail.

### ST-W4-01: Odrzucenie rejestratora z niską baterią / niewystarczającą pamięcią (deferred)
* **Warunki wstępne**: Rejestrator z `batteryLevel = 40%` (< 50%) lub `sampleCapacity` < liczby próbek Kroku 4. *(Zależne od udostępnienia danych sprzętowych — patrz BA W4.)*
* **Krok**: Alokacja rejestratora.
* **Oczekiwany rezultat**: Rejestrator odrzucony (`HardwareLimitExceededException`). Gdy dane sprzętowe niedostępne — test oznaczony `@Disabled("W4 deferred: brak pól batteryLevel/sampleCapacity")`.

---

## 6. Grupa 6: Przypadki Brzegowe Kalendarza

### ST-CAL-01: Nakładające się urlop + święto + weekend
* **Warunki wstępne**: Termin wypada w tygodniu, w którym są: urlop operatora (2 dni), święto państwowe (1 dzień) i weekend.
* **Krok**: `findNextValidShiftStart`.
* **Oczekiwany rezultat**: Wszystkie akcje manualne przesunięte na pierwszy dzień roboczy poza tą kumulacją, godz. 06:30.

### ST-CAL-02: Święto ruchome (Wielkanoc / Boże Ciało)
* **Warunki wstępne**: Termin akcji manualnej wypada w Poniedziałek Wielkanocny lub Boże Ciało danego roku.
* **Krok**: `PolishHolidayProvider.isHoliday(date)` + przeliczenie okna.
* **Oczekiwany rezultat**: Dzień rozpoznany jako świąteczny (poprawnie wyliczony algorytmem Meeusa/Gaussa); akcja przesunięta na najbliższy dzień roboczy.

### ST-CAL-03: Przebieg pomiaru przez zmianę czasu (DST)
* **Warunki wstępne**: Krok 4 (~120h) obejmuje ostatnią niedzielę marca (przejście na czas letni) w strefie `Europe/Warsaw`.
* **Krok**: Wyliczenie `planned_step5_readout_deadline`.
* **Oczekiwany rezultat**: Deadline odczytu zgodny z rzeczywistym czasem lokalnym (godzina okna 06:30–13:30 zachowana mimo przesunięcia zegara), a `Krok 5` nadal wypada w oknie roboczym.

### ST-CAL-04: Rok przestępny
* **Warunki wstępne**: `lastPeriodicRevalidationDate = 2024-02-29`.
* **Krok**: Wyliczenie terminu +12 miesięcy.
* **Oczekiwany rezultat**: `dueDate = 2025-02-28` (poprawna obsługa braku 29 lutego), bez wyjątku.
