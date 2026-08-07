# ANALIZA BIZNESOWA (BA v5.0)
## Moduł: Inteligentny Planer Rewalidacji Okresowych i Mapowań (Revalidation & Mapping Scheduler)
**System**: `validation-desktop` (JavaFX / Spring Boot / GxP / ISO 17025)  
**Data opracowania**: 2026-07-28  
**Data korekty**: 2026-08-07 — reguła W4 przeniesiona ze statusu *deferred* do *active* (§5 pkt 4); szczegóły wdrożenia i kwestie otwarte w suplemencie W4 v1.5  
**Lokalizacja**: `src/main/resources/docs/REVALIDATION_PLANNER_BA.md`  

---

## 1. Cel Biznesowy i Operacyjny

Głównym celem modułu jest automatyczne projektowanie, optymalizacja oraz nadzorowanie kalendarza **rewalidacji okresowych** oraz **mapowań rozkładu temperatur** dla urządzeń chłodniczych z wykorzystaniem floty rejestratorów (Testo).

Moduł zapewnia 100% zgodności z wymogami **GxP, ISO/IEC 17025, WHO TRS 961 oraz FDA 21 CFR Part 11**, eliminując ryzyko:
1. **Przekroczenia terminów walidacyjnych** komór chłodniczych i utraty statusu kwalifikacyjnego.
2. **Kwalifikowania nieodpowiednim sprzętem** (np. rejestratorem bez akredytowanej kalibracji PCA dla zakresu temperatur przechowywanego materiału).
3. **Konfliktów zasobów** (brak wystarczającej liczby wolnych/skalibrowanych rejestratorów w danym oknie czasowym).
4. **Błędów metodologicznych** (brak uwzględnienia czasu na stabilizację termiczną czujników lub niepotrzebny zapis próbek w fazie rozruchu).
5. **Niedopatrzeń logistycznych i absencji** (dostosowanie planera do godzin pracy operatora `06:30–13:30`, wykluczenie dni wolnych/świętych/urlopów oraz automatyczne przeliczanie przy nieplanowanym L4).

---

## 2. Kluczowe Zasady Biznesowe (Business Rules)

### R1: Macierz Zapotrzebowania na Rejestratory (Logger Allocation Matrix)

**Jednostka alokacji = pojedyncza komora (`CoolingChamber`).** Liczba rejestratorów wyliczana jest **per komora** na podstawie jej klasy kubatury (`CoolingChamber.volumeCategory` — enum `VolumeCategory`: `SMALL ≤ 2 m³`, `MEDIUM 2–20 m³`, `LARGE > 20 m³`). Zapotrzebowanie na poziomie urządzenia (`CoolingDevice`) to **suma zapotrzebowań jego komór** (np. chłodziarko-zamrażarka = dwie komory `SMALL` = 2 + 2 = 4 szt.).

| Klasa komory (`VolumeCategory`) | Typ Procedury | Wymagany Materiał | Liczba **fizycznych** Rejestratorów | Częstotliwość |
| :--- | :--- | :--- | :---: | :---: |
| **`SMALL`** (≤ 2 m³, np. chłodziarka podblatowa/apteczna) | **Rewalidacja Okresowa** | Dowolny | **2 szt.** | Co 12 miesięcy |
| **`MEDIUM`** (2–20 m³, np. szafa chłodnicza) | **Rewalidacja Okresowa** | Dowolny | **4 szt.** | Co 12 miesięcy |
| **`LARGE`** (> 20 m³, komora/walk-in) | **Rewalidacja Okresowa** | Dowolny | **8 szt.** | Co 12 miesięcy |
| **Dowolna klasa** | **Pełne Mapowanie GxP** | Materiały krytyczne (`requiresMapping = TRUE`) | **8 szt.** | Co 5 lat (lub po awarii) |
| **Dowolna klasa** | **Pełne Mapowanie GxP** | Odczynniki/Próby (`requiresMapping = FALSE`) | **ZWOLNIENIE** (0 szt.) | Brak obowiązku |

> **Uwaga metrologiczna (loggery vs punkty pomiarowe).** Powyższa kolumna liczy **fizyczne rejestratory Testo**, a nie punkty pomiarowe. Niezależnie obowiązuje minimalna liczba **punktów** pomiarowych z `VolumeCategory.getMinMeasurementPoints()` (`SMALL = 9`, `MEDIUM = 15`, `LARGE = 27`, wg PDA TR-64). Rejestratory są **wielokanałowe** (`V24__MultiChannelRecorders`, `RevalidationSession.PositionData.channelNumber`), dlatego przydzielona pula loggerów × liczba kanałów **musi pokryć** minimalną liczbę punktów. Silnik planera weryfikuje oba warunki jednocześnie: `Σ(kanały przydzielonych rejestratorów) ≥ VolumeCategory.minMeasurementPoints`.

---

### R2: Kwalifikacja Obowiązku Mapowania (Material-Driven Mapping Requirement)
Obowiązek wykonywania 5-letniego mapowania rozkładu temperatur jest cechą **materiału przechowywanego w komorze (`MaterialType.requiresMapping`)**:
* **Materiały Krytyczne (np. KKCZ $+2\dots+6^{\circ}\text{C}$, Osocze FFP $\le -25^{\circ}\text{C}$, Szczepionki $+2\dots+8^{\circ}\text{C}$)**:
  * Wymagają zarówno **corocznej rewalidacji okresowej**, jak i **5-letniego pełnego mapowania GxP**.
* **Materiały Standardowe (np. Odczynniki laboratoryjne, próby środowiskowe)**:
  * Wymagają **wyłącznie corocznej rewalidacji okresowej**. Mapowanie 5-letnie jest automatycznie pomijane przez silnik planera.

> **Rozdział cykli — dwie niezależne daty.** Coroczna rewalidacja i 5-letnie mapowanie mają **osobne zegary** i nie wolno ich mylić:
> * `CoolingChamber.lastPeriodicRevalidationDate` **[POLE DO DODANIA]** — baza dla terminu **rocznej** rewalidacji (`+ 12 miesięcy`).
> * `CoolingChamber.lastMappingDate` (istniejące, `CoolingChamber.java:61`) — baza wyłącznie dla **5-letniego mapowania** (walidowane przez `isMappingValid()`, `CoolingChamber.java:138`).
>
> Zatwierdzenie raportu aktualizuje **tylko** datę odpowiadającą wykonanemu typowi procedury (`PERIODIC_REVALIDATION` → `lastPeriodicRevalidationDate`; `MAPPING` → `lastMappingDate`). Nadpisywanie `lastMappingDate` po każdej rocznej rewalidacji jest **zabronione** — resetowałoby 5-letni cykl mapowania.

---

## 3. Konfigurowalna Macierz Klas Procedur & Ochrona Pamięci Testo

W systemie udostępniony zostaje konfigurator klas procedur. Dla każdej klasy użytkownik definiuje 5 konkretnych kroków czasowych:

| Krok | Nazwa Krok | Opis Roli Operacyjnej | Wpływ na Rejestrator Testo | Przykładowe Wartości |
| :--- | :--- | :--- | :--- | :--- |
| **Krok 1** | **Programowanie** | Czas na zaprogramowanie rejestratorów w stacji USB. | Konfiguracja parametrów i start zegara opóźnienia. | **10 minut** |
| **Krok 2** | **Umieszczenie w komorze** | Czas na transport rejestratorów z laboratorium i montaż na siatce pomiarowej. | Odliczanie opóźnienia startu. | **15 – 20 minut** |
| **Krok 3** | **Stabilizacja układu** | Czas po zamknięciu drzwi komory na osiągnięcie stanu ustalonego. | **BRAK ZAPISU POMIARÓW!** Rejestrator odlicza opóźnienie. | **6 godzin** |
| **Krok 4** | **Start Pomiaru GxP** | **Właściwy okres pomiarowy GxP**: $\text{Interwał} \times \text{Liczba pomiarów}$. | **START REJESTRACJI!** Próbka #1 = pierwsza czysta próbka GxP. | **Próbkowanie: co 3h**<br>**Liczba próbek: 40** (120h) |
| **Krok 5** | **Wyjęcie i Odczyt** | Dopuszczalny czas/bufor dla technika na wyjęcie i zczytanie danych z Testo USB. | Pomiar zakończony (`Stop when full`). Oczekiwanie na import z alertem. | **6 godzin** |

### Matematyka Opóźnienia Startu:
$$\text{Testo\_Start\_Delay\_Minutes} = \text{Krok\_2\_Umieszczenie\_Minutes} + \text{Krok\_3\_Stabilizacja\_Minutes}$$

---

## 4. Harmonogram Pracy Operatora i Reaktywność na Zdarzenia (L4)

### A. Podział na Akcje Manualne i Autonomiczne:
* **Akcje Manualne (Krok 1, Krok 2, Krok 5)**: Zaplanowane wyłącznie w oknie **06:30 – 13:30**, od poniedziałku do piątku (z wyłączeniem weekendów, świąt państwowych i urlopów operatora).
* **Akcje Autonomiczne (Krok 3, Krok 4)**: Rejestratory pracują samoczynnie w komorze. Pomiar może trwać nieprzerwanie przez noc, weekendy i święta.

### B. Dynamiczna Rekalkulacja przy Nieplanowanym L4:
1. Operator zgłasza nieobecność L4.
2. Niezapoczątkowane procedury zostają automatycznie przesunięte na pierwszy dzień po powrocie z L4 (godz. 06:30).
3. Procedury w trakcie pomiaru dokonują bezpiecznej rejestracji w pamięci czujnika (`Stop when full`), a odczyt USB (Krok 5) zostaje ustawiony na poniedziałek 06:30 po L4.

---

## 5. Zintegrowane Reguły Walidacyjne (W1 – W10)

1. **W1 (Calibration Expiry)**: Świadectwo wzorcowania ważne przez cały okres pomiaru $+ 7\text{ dni}$.
2. **W2 (Capacity)**: Wystarczająca liczba wolnych rejestratorów w oknie czasowym.
3. **W3 (Zero-Junk Data)**: Brak zapisu pomiarów podczas stabilizacji.
4. **W4 (Hardware Limits)**: Status *active* — reguła blokuje planowanie. Trzy kryteria: **W4a** zakres pracy urządzenia wobec temperatury komory (bramka twarda), **W4b** pojemność pamięci na kanał wobec liczby próbek GxP, **W4c** budżet energii na pełny czas misji z zapasem ×1,5. Budżet energii liczony jest **w dniach, nie w procentach** — próg „$>50\%$" z wcześniejszych wersji BA odpadł wraz z ustaleniem, że urządzenie raportuje pozostałe dni wprost (ramka `ab010a`), a bajt czytany wcześniej jako stan naładowania był progiem alarmowym temperatury. Dane sprzętowe: `ThermoRecorder.batteryRemainingDays` i `ThermoRecorderModel.sampleCapacity` (migracje V34–V36). Model matematyczny, scenariusze testowe i **kwestie otwarte wymagające decyzji przy zatwierdzaniu walidacyjnym**: `REVALIDATION_PLANNER_W4_SUPPLEMENT.md` (wersja 1.5) — §7 pkt 1 (brak deratingu temperaturowego) i §7 pkt 2a (przenoszalność zmierzonego kosztu odczytu na −80 °C).
5. **W5 (No Double-Booking)**: Brak nakładania się zadań dla rejestratora z buforem logistycznym.
6. **W6 (No-Gap Deadline)**: Wykonanie badania przed wygaśnięciem poprzedniej walidacji.
7. **W7 (Readout Timeout Alert)**: Alert przy braku odczytu po przekroczeniu buforu z Kroku 5.
8. **W8 (Metrological & Material Range)**: Świadectwo kalibracji PCA $[T_{min}^{cal}, T_{max}^{cal}]$ pokrywa zakres dopuszczalny materiału $[T_{min}^{mat}, T_{max}^{mat}]$.
9. **W9 (Operator Shift & Vacation)**: Akcje manualne wyłącznie w oknie **06:30 – 13:30** w dni robocze.
10. **W10 (Dynamic Event Adaptation)**: Automatyczna rekalkulacja po zdarzeniach w aplikacji i L4 z zachowaniem Audit Trail (21 CFR Part 11).
