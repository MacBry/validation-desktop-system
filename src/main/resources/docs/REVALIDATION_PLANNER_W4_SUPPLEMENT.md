# SUPLEMENT DO DOKUMENTACJI PLANERA: WDROŻENIE REGUŁY W4
## Moduł: Inteligentny Planer Rewalidacji Okresowych i Mapowań (Revalidation & Mapping Scheduler)
**System**: `validation-desktop` (JavaFX / Spring Boot / GxP / ISO 17025)
**Dokument nadrzędny**: `src/main/resources/docs/REVALIDATION_PLANNER_BA.md` (BA v5.0, reguła W4)
**Data opracowania**: 2026-07-30
**Data korekty**: 2026-08-07 (wersja 1.5)
**Status**: Reguła W4 wdrożona (Hardware Limits: zakres pracy, pamięć, budżet baterii)

---

## 0. Historia zmian

| Wersja | Data | Zmiana |
|---|---|---|
| 1.0 | 2026-07-30 | Pierwsza wersja planu W4 (model `Battery_eff = Battery × k_temp`) |
| 1.1 | 2026-07-31 | **Korekta modelu matematycznego.** Procentowy derating baterii zastąpiony budżetem czasu pracy w dniach, zgodnym z kartami katalogowymi Testo; dodana twarda bramka zakresu pracy urządzenia; poprawione pojemności pamięci (175 ≠ 176, 184 T1 ≠ T3); uwzględniona liczba kanałów i pełny czas misji; poprawiony punkt wpięcia w `RecorderAllocationService`; migracja rozbita na `h2`/`mysql` wraz z tabelami Envers. |
| 1.5 | 2026-08-07 | **Człon cyklu zmierzony, nie założony (§2.5).** Bieg trzech egzemplarzy 174 T przy Δt = 1/10/15 min obalił obie dotychczasowe hipotezy: przy 1 min wskazanie schodzi 2,05 dnia na dobę, wobec 1,0 dla licznika czysto zegarowego i 15,0 dla zużycia proporcjonalnego do pomiarów. Człon $\min(1, \Delta t/\Delta t_{ref})$ zastąpiony zmierzonym $\Delta t/(\Delta t + T_c)$ przy $T_c$ = 1 min; $\Delta t_{ref}$ przestaje wchodzić do arytmetyki W4c. Przeliczone ST-W4c-01/02/03/06, nowy scenariusz ST-W4c-07 kotwiczący współczynnik w danych pomiarowych, przeliczone punkty przecięcia §3.4. Nowe ryzyko rezydualne: $T_c$ zmierzone na 174 T w temperaturze pokojowej, stosowane do 184 T4 w −80 °C (§7 pkt 2). |
| 1.4 | 2026-08-06 | **Doba kontrolna na dwóch egzemplarzach (§2.4).** Rejestrator bezczynny i pracujący przy Δt = 10 min tracą tyle samo wskazania — obserwacja sprzeczna z hipotezą zużycia proporcjonalnego do liczby pomiarów, przemawiająca za licznikiem zegarowym. Poprawiony protokół §7 pkt 2: bieg przy Δt = 1 min zamiast 15 min, bo przy cyklu referencyjnym obie hipotezy przewidują ten sam spadek i taki pomiar nie mógłby niczego rozstrzygnąć. Odczyt i zapis stanu baterii zweryfikowane na sprzęcie wobec oprogramowania producenta (krok 17). |
| 1.3 | 2026-08-06 | **Jednostką stanu baterii są dni.** $D_{ref}$ przestaje być wyprowadzane z $D_{spec} \cdot SoC/100$ i jest czytane wprost z urządzenia (ramka `ab010a`, §2.3 U5) — symbol $SoC$ znika z modelu, a $D_{spec}$ zostaje daną informacyjną, wiążącą już tylko dla baterii niewymiennych. Migracja jednostki `common/V36__Battery_Remaining_Days.sql` wraz z wyczyszczeniem fałszywego `last_battery_level_percent` na egzemplarzach; przeliczone scenariusze ST-W4c-01…04 i przykład wyprowadzenia w komunikacie odrzucenia. |
| 1.2 | 2026-07-31 | **Synchronizacja z wdrożeniem.** Poprawiona arytmetyka ST-W4c-01/02 (pominięty współczynnik cyklu); migracja scalona do jednej przenośnej w `common/`; W4b dostaje własny wyjątek zamiast reużycia W2-owego; ocena zwraca `HardwareBudget` zamiast rzucać wyjątkiem wewnątrz filtra kandydatów; zastrzeżenie temperaturowe opisane jako ostrzeżenie, nie blokada. |

---

## 1. Cel Suplementu i Zakres Projektowy

Niniejszy suplement precyzuje specyfikację techniczną, model matematyczny oraz plan wdrożenia dla **Reguły W4 (Walidacja Limitów Sprzętowych Rejestratora)**.

W dokumentacji BA v5.0 reguła W4 została oznaczona jako *deferred* (odroczona) z uwagi na brak w encjach `ThermoRecorder` oraz `ThermoRecorderModel` pól opisujących pojemność pamięci, zakres pracy i stan baterii. Suplement definiuje pełny cykl aktywacji reguły W4.

**Zakres reguły W4 obejmuje trzy niezależne kryteria** (każde może samodzielnie zablokować alokację):

* **W4a — Zakres pracy urządzenia**: temperatura komory mieści się w zakresie pracy modelu rejestratora.
* **W4b — Budżet pamięci**: liczba próbek wymaganych przez procedurę mieści się w pamięci przypadającej na kanał.
* **W4c — Budżet energii**: pozostała energia baterii pokrywa pełny czas misji przy zadanym interwale i temperaturze.

---

## 2. Dane Źródłowe — Oficjalne Specyfikacje Producenta

Wszystkie wartości poniżej pochodzą z kart katalogowych i instrukcji Testo (odnośniki w §9). Są to dane wejściowe reguły W4 i **muszą być przechowywane w bazie jako dane referencyjne producenta**, nie jako stałe w kodzie.

| Model | Pamięć (odczyty) | Kanały | Bateria | Żywotność baterii | Zakres pracy |
|---|---|---|---|---|---|
| **testo 174 T** | 16 000 | 1 | 2× CR2032 (Li‑MnO₂) | 500 dni @ 15 min, **+25 °C** | −30…+70 °C |
| **testo 184 T1** | 16 000 | 1 | niewymienna | ograniczenie pracy: **90 dni** | −35…+70 °C |
| **testo 184 T2** | 40 000 | 1 | niewymienna | ograniczenie pracy: **150 dni** | −35…+70 °C |
| **testo 184 T3** | 40 000 | 1 | CR2450 (wymienna) | 500 dni @ 15 min, **+25 °C** | −35…+70 °C |
| **testo 184 T4** | 40 000 | 1 | ER2450T (Li‑SOCl₂) | **100 dni @ 15 min, −80 °C** | **−80**…+70 °C |
| **testo 175 T1/T2/T3** | 1 000 000 | 1–3 | 3× AAA AlMn | ok. 3 lata @ 15 min, +25 °C | −35…+70 °C |
| **testo 176 T3/T4** | 2 000 000 | 4 | AA / litowa | do 8 lat | −40…+70 °C |

Interwał pomiarowy w serii 184: **1 min … 24 h**.

### 2.1. Kluczowe wnioski z danych producenta

1. **Testo nie definiuje procentowego współczynnika degradacji baterii z temperaturą.** Żywotność jest zawsze podawana jako **liczba dni przy referencyjnym cyklu pomiarowym i referencyjnej temperaturze**. Model walidacji musi więc porównywać *czas z czasem*, a nie *procent z procentem*.
2. **Praca w ultra‑niskiej temperaturze jest rozwiązywana doborem modelu, nie deratingiem.** Do −80 °C Testo przewiduje wyłącznie model **184 T4** z baterią litowo‑tionylową ER2450T. Rejestrator z CR2032 (174 T) w −80 °C jest **poza zakresem pracy** i musi zostać odrzucony bezwarunkowo — niezależnie od stanu naładowania.
3. **Implikowany stosunek żywotności T4 (−80 °C) do T3 (+25 °C) wynosi 100/500 = 0,2**, a nie 0,4 — i dotyczy **innej chemii ogniwa**, więc nie wolno przenosić go na pozostałe modele.
4. **184 T1 i T2 mają baterię niewymienną** i sztywny limit pracy urządzenia (90 / 150 dni). Dla nich pojęcie „daty wymiany baterii” nie istnieje.
5. **Pojemność pamięci jest wspólna dla wszystkich aktywnych kanałów** — w modelach wielokanałowych (175 T3, 176 T3/T4) dostępna liczba próbek na kanał to `sampleCapacity / channelCount`.

### 2.2. Dane dostępne już dziś w systemie

| Dana | Źródło | Uwagi |
|---|---|---|
| ~~Stan naładowania baterii [%]~~ | ~~`testo_usb_reader.py:423` → `payload_31[20]`~~ | **WYCOFANE 2026-08-05** — to nie była bateria, tylko młodszy bajt progu alarmowego (§2.3 U4) |
| Pozostały czas pracy baterii [dni] | ramka `ab010a` → `uint16 BE` | Podawany przez urządzenie wprost; jednostka zgodna z modelem W4c (§2.3 U5) |
| Progi alarmowe temperatury [°C] | `payload_31[19:20]` i `[21:22]`, `int16 BE` ×0,1 | Ta sama struktura co payload komendy `AB 61` |
| Żywotność katalogowa [dni] | `testo_184_config.py:620` → pole XDP `<battery>500</battery>` | Wartość 500 odpowiada karcie katalogowej 184 T3 |
| Interwał pomiaru | `TestoUsbImportService.ImportedSession.intervalMinutes` | |
| Liczba kanałów modelu | `ThermoRecorderModel.channelCount` | **Pole już istnieje** — nie wymaga migracji |
| Sentinel „brak danych” | `TestoRevalidationService.recordBatteryDays(...)` | `batteryRemainingDays < 0` oznacza **N/D** (źródło nie podaje stanu baterii) — do kartoteki i do serii trafia wtedy `null`, nigdy liczba ujemna |

### 2.3. Weryfikacja na sprzęcie — testo 174 T, 2026-08-05

Egzemplarz 174 T podłączony do stacji USB, odczyt w oryginalnym oprogramowaniu producenta zestawiony z odczytem naszego importu. **Cztery ustalenia, wszystkie potwierdzone pomiarem, nie wywnioskowane z karty katalogowej:**

**U1 — „Okres” nad cyklem zapisu to limit pamięci, nie baterii.**

| Ustawiony interwał | Wskazanie producenta | Rozkład |
|---|---|---|
| 1 min | `11d 2h 39m` | 15 999 min = 15 999 × 1 min |
| 10 min | `111d 2h 30m` | 159 990 min = 15 999 × 10 min |

Liczba **interwałów** to 15 999 = 16 000 − 1; pierwszy odczyt zapada w chwili startu. Potwierdza to pojemność 16 000 przyjętą w `V34` i wymusza korektę wzoru $T_{mem}$ (§3.2).

**U2 — zmiana zakresu alarmowego (2…8 °C) nie zmienia „Okresu”.** Producent nie wiąże progów alarmowych z czasem pracy. Nasza reguła też nie — zgodność potwierdzona.

**U3 — wskazanie stanu baterii jest podawane w dniach i nie zależy od interwału.** Urządzenie pokazywało `Stan baterii: 388 dni` **identycznie przy 1 min i przy 10 min**. Przy katalogowych 500 dniach daje to $388/500 = 77{,}6\%$. Wskazanie producenta to zatem **stan naładowania wyrażony w dniach referencyjnych**, a nie prognoza czasu pracy dla ustawionego cyklu — Testo takiej prognozy w ogóle nie oferuje. Konsekwencja dla §3.3: producent **nie dostarcza żadnej zależności zużycia od interwału**, więc człon cyklu musi pochodzić od nas i być tak opisany. *(Do wersji 1.4 było to założenie inżynierskie $\min(1, \Delta t/\Delta t_{ref})$; od wersji 1.5 — wielkość zmierzona własnym biegiem, §2.5.)*

**U4 — `payload_31[20]` NIE JEST STANEM BATERII.** *(Ustalenie skorygowane 2026-08-05 po zbadaniu kolejnych egzemplarzy — pierwotnie zapisano tu hipotezę „procent z granulacją 5 %", która okazała się błędna.)*

Trzy egzemplarze o skrajnie różnym stanie baterii zwróciły **tę samą wartość 80**:

| Egzemplarz | Wskazanie stacji | Rzeczywisty stan | Nasz odczyt |
|---|---|---|---|
| #1 | 388 dni | 77,6 % | 80 % |
| #2 | 42 dni | 8,4 % | 80 % |
| #3 | 40 dni | 8,0 % | 80 % |

Bajt 20 to **młodszy bajt górnego progu alarmowego temperatury**. Wszystkie trzy rejestratory miały ustawione 2…8 °C, a 8,0 °C = 80 = `0x0050`, którego młodszy bajt to `0x50` = 80. Rejestrator w zamrażarce (próg −20,0 °C = −200 = `0xFF38`) raportuje „56 %".

Pełna struktura, potwierdzona zrzutami USB i **zgodna z istniejącym opisem payloadu komendy `AB 61`** w `TESTO_USB_PROGRAMMING_TECHNICAL_SPEC.md` §3:

| Offset | Typ | Znaczenie |
|---|---|---|
| `[19:20]` | `int16 BE` ×0,1 | górny próg alarmowy |
| `[21:22]` | `int16 BE` ×0,1 | dolny próg alarmowy |
| `[23:24]` | `int16 BE` ×0,1 | limit sondy (typowo 100,0 °C) |

**Skutek dla reguły W4c:** budżet energii był liczony z progu alarmowego. Rejestrator z 42 dniami życia planer widział jako 80 % z 500 dni katalogowych = **400 dni referencyjnych** — zawyżenie ~10×, w kierunku optymistycznym. Sprzeczność istniała w repozytorium od dawna: dokument programowania opisywał te bajty poprawnie, dokument odczytu (`TESTO_USB_ANALYSIS.md:46`) błędnie.

**U5 — stan baterii ma własną komendę, której nie wysyłaliśmy.**

```
TX:  ab 01 0a 00 00 05
RX:  ab 01 0a <uint16 BE = POZOSTAŁE DNI> <crc>
```

Zweryfikowane: `00 2a` → 42 dni, `01 84` → 388 dni — zgodność co do jedności ze wskazaniem stacji. Urządzenie podaje **dni**, nie procenty; procent bywa liczony wtórnie jako `dni / żywotność katalogowa`, ale nie jest wielkością mierzoną. Suma kontrolna rodziny `ab 01 xx`: `crc = 0x0F − cmd`.

**To upraszcza model W4c**: `D_ref` przestaje być wyprowadzane z `D_spec × SoC/100` i przychodzi wprost ze sprzętu — znika zależność od poprawności `battery_life_days` w kartotece modelu.

### 2.4. Weryfikacja doby kontrolnej — dwa egzemplarze 174 T, 2026-08-06

Pierwszy pomiar zestawiający **obciążenie pomiarowe** ze spadkiem wskazania. Oba egzemplarze odczytane 2026-08-05 i ponownie po ok. 24 h, w temperaturze pokojowej; odczyt zweryfikowany równolegle w oryginalnym oprogramowaniu producenta i w naszej aplikacji — **wskazania zgodne co do jedności** (387 dni), co potwierdza poprawność implementacji komendy `ab010a` end‑to‑end wraz z zapisem do kartoteki.

| Egzemplarz | Obciążenie przez 24 h | Odczytów | Wskazanie 05.08 | Wskazanie 06.08 | Spadek |
|---|---|---|---|---|---|
| A | rejestracja przy Δt = 10 min | ~144 | 388 dni | 387 dni | 1 dzień |
| B | bezczynny, bez zaprogramowanej sesji | 0 | 388 dni | 387 dni | 1 dzień |

**U6 — wskazanie odlicza czas, nie pomiary.** Egzemplarz, który nie wykonał ani jednego odczytu, stracił dokładnie tyle samo co pracujący. Hipoteza „zużycie proporcjonalne do liczby pomiarów" przewiduje dla B spadek bliski zeru, więc obserwacja jest z nią **sprzeczna**, a nie tylko z nią niezgodna. Wskazanie zachowuje się jak zegar odliczający od stanu ogniwa.

**Czego ta obserwacja jeszcze nie rozstrzyga.** Przy granulacji jednego dnia i nieznanej regule zaokrąglania doba jest za krótka, by rozdzielić dwa warianty: licznik *czysto* zegarowy (spadek 1 dzień/dobę niezależnie od cyklu) od zegarowego ze **składową pomiarową** na tyle małą, że przy Δt = 10 min ginie w zaokrągleniu. Rozdziela je dopiero bieg przy Δt = 1 min — dziesięciokrotnie gęstszy niż A — opisany w §7 pkt 2.

> **Uwaga metodyczna.** Sam egzemplarz A niczego by nie dowiódł: model z członem cyklu przewiduje dla niego spadek 1,5 dnia, co po zaokrągleniu również mogło dać 387. Dowodową wartość ma dopiero **kontrast** z egzemplarzem B. To ta sama lekcja co przy §2.3 U4 — jedna zgodna obserwacja nie potwierdza hipotezy; potrzebny jest punkt, który może ją **obalić**.

### 2.5. Pomiar zależności zużycia od cyklu — trzy egzemplarze 174 T, 2026-08-07

Wykonanie protokołu z §7 pkt 2. Trzy egzemplarze pracowały równolegle od 5 do 7 sierpnia w temperaturze pokojowej, każdy przy innym interwale. Czas pracy nie był przyjmowany z kalendarza, lecz **wyliczony z danych zaimportowanych przez aplikację** jako `liczba odczytów × Δt` — wszystkie trzy dają ok. 2 doby, co jest zarazem kontrolą poprawności odczytu obu pól z urządzenia.

| Egzemplarz | Δt | Odczytów | Czas pracy | Wskazanie 05.08 | Wskazanie 07.08 | Spadek | Tempo [dni/dobę] |
|---|---|---|---|---|---|---|---|
| A | 15 min | 188 | 1,96 doby | 388 dni | 386 dni | 2 dni | **1,02** |
| B | 1 min | 2810 | 1,95 doby | 42 dni | 38 dni | 4 dni | **2,05** |
| C | 10 min | 288 | 2,00 doby | — (nieodczytane) | 484 dni | — | — |

**U7 — zużycie ma składową spoczynkową i pomiarową; obie hipotezy z §2.4 są obalone.** Dla egzemplarza B przy Δt = 1 min konkurencyjne modele przewidywały spadek: licznik czysto zegarowy **2 dni**, zużycie proporcjonalne do liczby pomiarów **29 dni**. Obserwacja — **4 dni** — nie mieści się w żadnym z nich. Dopasowanie modelu $D = Q/(I_q + q/\Delta t)$, zapowiedzianego w §7 pkt 2, daje tempo spadku wskazania:

$$\rho(\Delta t) = 1 + \frac{T_c}{\Delta t} \quad [\text{dni/dobę}], \qquad T_c \approx 1{,}05\ \text{min}$$

czyli: **jeden odczyt kosztuje mniej więcej tyle energii, co minuta pracy spoczynkowej.** Stąd przy Δt = 1 min wskazanie schodzi dokładnie dwa razy szybciej niż czas — i tak jest.

**Niepewność.** Przy granulacji wskazania równej 1 dzień i oknie dwóch dób $T_c$ mieści się w przedziale ok. **0,8–1,6 min** (zależnie od reguły zaokrąglania w urządzeniu). Do modelu przyjęto $T_c$ = **1,0 min** — wartość najlepiej dopasowaną, o czytelnej interpretacji fizycznej. Parametr jest konfigurowalny (`app.planner.w4.measurement-cost-minutes`), żeby dłuższy bieg mógł zawęzić przedział bez zmiany kodu.

**Egzemplarze A i C nie mają wartości dowodowej** — przy 15 i 10 min oba modele przewidują spadek nieodróżnialny od zegarowego w granulacji jednego dnia. Cały ciężar dowodu niesie bieg 1-minutowy, dokładnie tak, jak przewidywał poprawiony protokół. Egzemplarz C wszedł do biegu bez odczytanego stanu wyjściowego, więc dostarcza wyłącznie punktu odniesienia na przyszłość (484 dni na 07.08) i potwierdzenia, że świeże ogniwo 174 T startuje blisko katalogowych 500 dni.

> **Uwaga metodyczna.** Dwa kolejne pomiary — §2.4 i §2.5 — obaliły po jednej hipotezie, w tym tę, którą sam §2.4 wskazywał jako prawdopodobną. Doba kontrolna nie była błędem: przy Δt = 10 min składowa pomiarowa wynosi 0,1 dnia na dobę i **musiała** zginąć w zaokrągleniu. Wniosek do stosowania szerzej: obserwacja zgodna z hipotezą przy niewystarczającej rozdzielczości pomiaru nie jest jej potwierdzeniem, tylko brakiem informacji.

---

## 3. Model Matematyczny Reguły W4

Oznaczenia:

| Symbol | Znaczenie | Źródło |
|---|---|---|
| $N_{max}$ | pojemność pamięci modelu [odczyty] | `model.sampleCapacity` |
| $n_{ch}$ | liczba aktywnych kanałów | `model.channelCount` |
| $\Delta t$ | interwał próbkowania [min] | `config.step4IntervalMinutes` |
| $N_{req}$ | liczba próbek GxP | `config.step4SampleCount` |
| $T_{mission}$ | pełny czas pracy rejestratora [min] | patrz §3.3 |
| $T_{chamber}$ | temperatura pracy komory [°C] | `chamber.getEffectiveMinTempLimit()` |
| $D_{spec}$ | katalogowa żywotność baterii [dni] | `model.batteryLifeDays` — **dana informacyjna**, do W4c nie wchodzi |
| $\Delta t_{ref}$, $T_{ref}$ | warunki referencyjne specyfikacji | `model.batteryLifeRefCycleMin` (15 min), `model.batteryLifeRefTempC` — **dane informacyjne**, od wersji 1.5 do W4c nie wchodzą |
| $D_{ref}$ | pozostały czas pracy odczytany z urządzenia [dni] | `recorder.batteryRemainingDays` (ramka `ab010a`) |
| $T_c$ | koszt jednego odczytu w minutach pracy spoczynkowej | **zmierzony** (§2.5): 1,0 min; `app.planner.w4.measurement-cost-minutes` |

### 3.1. Kryterium W4a — Zakres pracy (bramka twarda)

Sprawdzane **jako pierwsze**, przed jakąkolwiek arytmetyką baterii:

$$\text{model.minOperatingTempC} \le T_{chamber} \le \text{model.maxOperatingTempC}$$

* **Błąd walidacji**: `RecorderOutOfOperatingRangeException` — rejestrator nie jest przewidziany do pracy w tej komorze.
* Uwaga implementacyjna: `CoolingChamber.getEffectiveMinTempLimit()` zwraca **`Double` (nullable)**; brak limitu komory musi być obsłużony jawnie (odrzucenie z komunikatem o brakującej konfiguracji komory, nigdy `null → 0.0`).

### 3.2. Kryterium W4b — Budżet pamięci

Liczba próbek wymaganych przez klasę procedury nie może przekroczyć pamięci przypadającej na kanał:

$$N_{req} \le \left\lfloor \frac{N_{max}}{n_{ch}} \right\rfloor$$

Równoważnie, maksymalny czas rejestracji wynikający z pamięci:

$$T_{mem}[\text{dni}] = \left(\left\lfloor \frac{N_{max}}{n_{ch}} \right\rfloor - 1\right) \cdot \frac{\Delta t}{1440}$$

**Odjęcie jedynki nie jest kosmetyką.** Liczba interwałów jest o jeden mniejsza od liczby próbek, bo pierwszy odczyt zapada w chwili startu — tak liczy oprogramowanie producenta (§2.3 U1: 15 999, nie 16 000). Bez tej korekty zawyżamy limit dokładnie o jeden interwał, co przy $\Delta t$ = 24 h oznacza całą dobę wpisaną do dokumentacji walidacyjnej jako dostępna, a faktycznie nieistniejącą. Regresja: **ST-W4b-04**.

* **Błąd walidacji**: `InsufficientRecorderMemoryException` — nowa klasa, **nie** reużycie `InsufficientRecorderCapacityException` (ta opisuje regułę W2, patrz §5).
* **Uwaga**: $N_{req}$ bierzemy wprost z `config.getStep4SampleCount()`. Wzoru $N_{req} = T_{map}/\Delta t$ **nie stosujemy** — `ProcedureClassConfig` przechowuje interwał i liczbę próbek jako dwa niezależne pola (`ProcedureClassConfig.java:64-75`), a wyprowadzanie jednego z drugiego może dać wynik rozbieżny z konfiguracją zatwierdzoną przez QA.

**Maksymalny czas rejestracji z limitu pamięci [dni]:**

| Model (pamięć / kanały) | Δt = 1 min | Δt = 5 min | Δt = 10 min | Δt = 15 min |
|---|---|---|---|---|
| 174 T, 184 T1 (16 000 / 1) | 11,1 | 55,6 | 111,1 | 166,7 |
| 184 T2/T3/T4 (40 000 / 1) | 27,8 | 138,9 | 277,8 | 416,7 |
| 175 T1 (1 000 000 / 1) | 694,4 | — | — | — |
| 175 T3 (1 000 000 / 3) | 231,5 | — | — | — |
| 176 T3/T4 (2 000 000 / 4) | 347,2 | — | — | — |

### 3.3. Kryterium W4c — Budżet energii

**Czas misji to nie sam Krok 4.** Rejestrator pobiera energię od zaprogramowania aż do odczytu:

$$T_{mission}[\text{min}] = \text{step2Placement} + 60 \cdot \text{step3StabHours} + \Delta t \cdot N_{req} + 60 \cdot \text{step5ReadoutBuffer}$$

(Krok 3 nie zużywa pamięci — start jest opóźniony zgodnie z regułą W3 — ale zużywa energię.)

Liczymy w **dwóch krokach**, bo są to dwie różne wielkości i operator widzi je obie.

**Krok 1 — stan ogniwa w dniach referencyjnych.** Ta sama wielkość, którą pokazuje oprogramowanie producenta (§2.3 U3), czytana **wprost z urządzenia** ramką `ab010a` (§2.3 U5):

$$D_{ref}[\text{dni}] = \text{recorder.batteryRemainingDays}$$

Do sierpnia 2026 wielkość ta była wyprowadzana jako $D_{spec} \cdot SoC/100$, gdzie „$SoC$” pochodził z bajtu okazującego się **młodszym bajtem górnego progu alarmowego** (§2.3 U4) — rejestrator z 42 dniami życia wyceniano wtedy na 400 dni. Wraz z odczytem wprost znika z modelu zarówno $SoC$, jak i zależność od poprawności `battery_life_days` w kartotece modelu. Migracja jednostki: `V36__Battery_Remaining_Days.sql`.

**Krok 2 — budżet dla tego konkretnego badania**, po przeliczeniu na cykl pomiarowy. Wskazanie urządzenia schodzi w tempie $\rho(\Delta t) = 1 + T_c/\Delta t$ dnia na dobę (§2.5), więc realny czas pracy przy tym interwale to $D_{ref}$ podzielone przez to tempo:

$$D_{avail}[\text{dni}] = \frac{D_{ref}}{1 + T_c/\Delta t} = D_{ref} \cdot \frac{\Delta t}{\Delta t + T_c}$$

| $\Delta t$ | 1 min | 5 min | 10 min | 15 min | 60 min |
|---|---|---|---|---|---|
| **współczynnik cyklu, wersja 1.5** | 0,500 | 0,833 | 0,909 | 0,938 | 0,984 |
| ~~wersja 1.4, $\min(1, \Delta t/\Delta t_{ref})$~~ | ~~0,067~~ | ~~0,333~~ | ~~0,667~~ | ~~1,000~~ | ~~1,000~~ |

**Warunek dopuszczenia:**

$$\frac{T_{mission}}{1440} \le \frac{D_{avail}}{f_{safety}}, \qquad f_{safety} = 1{,}5 \ \text{(domyślnie, konfigurowalne w SOP)}$$

* **Błąd walidacji**: `InsufficientBatteryLevelException`.
* **$D_{spec}$ nie wchodzi już do W4c dla baterii wymiennych.** Kwestia doboru wartości katalogowej „dla warunków najbliższych misji” dotyczyła modelu wyprowadzanego z procentu i wraz z nim odpada: urządzenie raportuje pozostałe dni bez odnoszenia ich do pojemności katalogowej. `battery_life_days` pozostaje daną informacyjną karty rejestratora. Wartość katalogowa nadal wiąże wyłącznie przy **bateriach niewymiennych** (`operating_duration_days`, patrz niżej), bo tam urządzenie nie podaje nic.
* **Człon cyklu pochodzi z pomiaru własnego, nie z danych producenta.** Że producent go nie publikuje, wiadomo z §2.3 U3: wskazanie stanu baterii jest **identyczne przy 1 min i przy 10 min**, więc nie jest prognozą dla ustawionego interwału i samo z siebie nie mówi nic o zużyciu. Zależność zmierzono własnym biegiem (§2.5) i to ona stoi za wzorem powyżej. Do wersji 1.4 obowiązywał tu **zachowawczy domysł** $\min(1, \Delta t/\Delta t_{ref})$, zakładający zużycie wprost proporcjonalne do liczby odczytów; pomiar pokazał, że przy Δt = 1 min był **7,3× za pesymistyczny**, a przy Δt = 15 min o ok. 6 % za optymistyczny. Regresja: **ST-W4c-07**.
* **$\Delta t_{ref}$ wypada z arytmetyki W4c.** Koszt odczytu $T_c$ jest wielkością fizyczną ogniwa i elektroniki, a nie odniesieniem do cyklu z karty katalogowej — nowy wzór nie potrzebuje warunku referencyjnego. `battery_life_ref_cycle_min` zostaje daną informacyjną karty rejestratora (pokazywaną przy szacowanym czasie pracy), tak samo jak `battery_life_ref_temp_c`. **Skutek dla kartotek modeli**: błędna wartość w tym polu nie fałszuje już budżetu energii.
* **Deratingu temperaturowego świadomie nie modelujemy.** Wcześniejsza wersja wystawiała ostrzeżenie „praca poniżej temperatury referencyjnej” do `HardwareBudget.warnings()`. Mechanizm został **usunięty**, bo dotyczył każdej chłodziarki i każdej zamrażarki bez wyjątku, nie zmieniał wyniku oceny i nie miał odbiorcy — w dokumentacji walidacyjnej wyglądał na zabezpieczenie, którego faktycznie nie było. Temperatura wchodzi do W4 wyłącznie jako bramka W4a. `batteryLifeRefTempC` pozostaje daną informacyjną karty rejestratora. **To jest świadomie przyjęte ryzyko rezydualne** — §7 pkt 1.
* **Rozbieżność wobec stacji Testo musi być czytelna dla operatora.** Operator widzi w oryginalnym oprogramowaniu liczbę dni, a w planerze budżet po przeliczeniu na cykl i po zapasie — przy $\Delta t$ = 1 min i zapasie ×1,5 jest to jedna trzecia wskazania ze stacji. Dlatego komunikat `InsufficientBatteryLevelException` niesie **całe wyprowadzenie**, nie samą liczbę końcową: *„urządzenie raportuje 50 dni pozostałej pracy, po przeliczeniu na cykl 1 min → 25,0 dnia; dopuszczalne 16,7 dnia przy zapasie ×1,5, a badanie w komorze X trwa 21,4 dnia”*. Bez tego rozbieżność wygląda na błąd aplikacji. Teraz pierwszy człon komunikatu jest **dokładnie tą liczbą, którą operator ma na ekranie stacji** — wcześniej trzeba było w nim tłumaczyć również przeliczenie z procentu.
* **Brak odczytu**: jeżeli `batteryRemainingDays` jest `null` albo ujemne, reguła W4c **nie liczy nic** — zwraca status `UNKNOWN` i blokuje zadanie z komunikatem o konieczności zczytania rejestratora w stacji USB. Arytmetyka na wartości zastępczej jest niedopuszczalna. Dotyczy to również **każdego rejestratora zastanego w bazie przed migracją V36**: fałszywe `last_battery_level_percent` zostało wyczyszczone, więc do ponownego odczytu sprzęt jest dla planera bez stanu baterii. To zachowanie zamierzone — brak danych jest uczciwy, fikcyjna liczba nie.
* **Bateria niewymienna (184 T1/T2)**: zamiast $D_{avail}$ obowiązuje pozostały limit `operatingDurationDays`, liczony od `firstActivationDate`.

### 3.4. Które kryterium wiąże jako pierwsze

Punkt przecięcia $T_{mem} = D_{avail}$ (przy świeżym ogniwie, tj. $D_{ref} = D_{spec}$, oraz $f_{safety} = 1$):

| Model | Przecięcie (wersja 1.5) | ~~wersja 1.4~~ | Interpretacja |
|---|---|---|---|
| 174 T @ +25 °C | Δt ≈ 44 min | ~~45 min~~ | poniżej 44 min ogranicza **pamięć** |
| 184 T3 @ +25 °C | Δt ≈ 17 min | ~~18 min~~ | poniżej 17 min ogranicza **pamięć** |
| **184 T4 @ −80 °C** | **Δt ≈ 2,6 min** | ~~3,6 min~~ | przy typowym mapowaniu (Δt ≥ 5 min) ogranicza **bateria** |

Korekta członu cyklu przesuwa każde przecięcie dokładnie o $T_c$ = 1 min w dół (nowy warunek to $\Delta t + T_c$ tam, gdzie stary miał $\Delta t$), więc **żaden wniosek jakościowy z wersji 1.4 się nie zmienia** — zmieniają się natomiast same budżety, i to wielokrotnie (§3.3).

Wniosek projektowy: w komorach ultra‑niskotemperaturowych realnym ograniczeniem planowania jest **energia**, a nie pamięć — i żaden model oparty wyłącznie na progu procentowym tego nie wychwyci.

---

## 4. Rozszerzenia Bazy Danych i Encji JPA

### 4.1. Umiejscowienie migracji

Tabele `thermo_recorders` i `thermo_recorder_models` powstały w migracjach **vendorowych** (`db/migration/h2/V24__MultiChannelRecorders.sql` oraz `db/migration/mysql/V24__MultiChannelRecorders.sql`), nie w `common/` — bo tam potrzebne były konstrukcje dialektowe (`UPDATE ... JOIN`, `MODIFY COLUMN`).

Migracja W4 takich konstrukcji nie potrzebuje, więc **jest jedna, w `common/V34__Thermo_Recorder_Hardware_Limits.sql`**, i obsługuje oba silniki. Warunki, które to umożliwiają i których nie wolno naruszyć przy późniejszych zmianach:

1. osobny `ALTER TABLE ... ADD COLUMN` na kolumnę (składnia wielokolumnowa różni się między H2 a MySQL),
2. typ `DOUBLE`, nie `DOUBLE PRECISION`,
3. objęcie **również tabel Envers** `thermo_recorder_models_aud` i `thermo_recorders_aud` — inaczej `PlannerEnversMySqlIntegrationTest` i walidacja schematu Hibernate zgłoszą niezgodność. Kolumny audytowe są bez `NOT NULL`: rewizja zapisuje stan sprzed dodania pola.

> Gdyby przyszła zmiana wymagała składni dialektowej, wtedy — i tylko wtedy — trzeba rozbić skrypt na warianty `h2/` i `mysql/`.

### 4.2. Skrypt `V34__Thermo_Recorder_Hardware_Limits.sql` (fragmenty)

```sql
-- 1. Model rejestratora: pojemność pamięci, zakres pracy, dane katalogowe baterii.
--    Osobny ALTER na kolumnę — składnia wielokolumnowa różni się między H2 a MySQL.
ALTER TABLE thermo_recorder_models ADD COLUMN sample_capacity INT DEFAULT 16000 NOT NULL;
ALTER TABLE thermo_recorder_models ADD COLUMN min_operating_temp_c DOUBLE;
ALTER TABLE thermo_recorder_models ADD COLUMN max_operating_temp_c DOUBLE;
ALTER TABLE thermo_recorder_models ADD COLUMN battery_type VARCHAR(50);
ALTER TABLE thermo_recorder_models ADD COLUMN battery_replaceable BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE thermo_recorder_models ADD COLUMN battery_life_days INT;
ALTER TABLE thermo_recorder_models ADD COLUMN battery_life_ref_cycle_min INT DEFAULT 15 NOT NULL;
ALTER TABLE thermo_recorder_models ADD COLUMN battery_life_ref_temp_c DOUBLE;
ALTER TABLE thermo_recorder_models ADD COLUMN operating_duration_days INT;
ALTER TABLE thermo_recorder_models ADD COLUMN battery_shelf_life_months INT;

-- Envers: te same kolumny, bez NOT NULL (rewizja zapisuje stan sprzed dodania pola)
ALTER TABLE thermo_recorder_models_aud ADD COLUMN sample_capacity INT;
ALTER TABLE thermo_recorder_models_aud ADD COLUMN min_operating_temp_c DOUBLE;
-- ... pozostałe kolumny analogicznie

-- 2. Dane katalogowe producenta (dopasowanie odporne na spacje i wielkość liter)
UPDATE thermo_recorder_models SET
    sample_capacity = 16000, min_operating_temp_c = -30, max_operating_temp_c = 70,
    battery_type = 'CR2032', battery_life_days = 500, battery_life_ref_temp_c = 25,
    battery_shelf_life_months = 24
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%174t%';

UPDATE thermo_recorder_models SET
    sample_capacity = 16000, min_operating_temp_c = -35, max_operating_temp_c = 70,
    battery_replaceable = FALSE, operating_duration_days = 90
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%184t1%';

UPDATE thermo_recorder_models SET
    sample_capacity = 40000, min_operating_temp_c = -35, max_operating_temp_c = 70,
    battery_replaceable = FALSE, operating_duration_days = 150
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%184t2%';

UPDATE thermo_recorder_models SET
    sample_capacity = 40000, min_operating_temp_c = -35, max_operating_temp_c = 70,
    battery_type = 'CR2450', battery_life_days = 500, battery_life_ref_temp_c = 25,
    battery_shelf_life_months = 24
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%184t3%';

UPDATE thermo_recorder_models SET
    sample_capacity = 40000, min_operating_temp_c = -80, max_operating_temp_c = 70,
    battery_type = 'ER2450T', battery_life_days = 100, battery_life_ref_temp_c = -80,
    battery_shelf_life_months = 24
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%184t4%';

UPDATE thermo_recorder_models SET
    sample_capacity = 1000000, min_operating_temp_c = -35, max_operating_temp_c = 70,
    battery_type = '3xAAA AlMn', battery_life_days = 1095, battery_life_ref_temp_c = 25
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%175%'
  AND REPLACE(LOWER(name), ' ', '') NOT LIKE '%184%';

UPDATE thermo_recorder_models SET
    sample_capacity = 2000000, min_operating_temp_c = -40, max_operating_temp_c = 70,
    battery_type = 'AA', battery_life_days = 2920, battery_life_ref_temp_c = 25
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%176%'
  AND REPLACE(LOWER(name), ' ', '') NOT LIKE '%184%';

-- 3. Egzemplarz rejestratora: ostatni znany stan baterii
ALTER TABLE thermo_recorders ADD COLUMN last_battery_level_percent INT;
ALTER TABLE thermo_recorders ADD COLUMN last_battery_read_at TIMESTAMP;
ALTER TABLE thermo_recorders ADD COLUMN battery_replacement_date DATE;
ALTER TABLE thermo_recorders ADD COLUMN first_activation_date DATE;
-- oraz te same cztery kolumny na thermo_recorders_aud
```

> Wykluczenie `NOT LIKE '%184%'` przy seriach 175 i 176 chroni przed nadpisaniem danych modelu 184 nazwą zawierającą oba oznaczenia.

> **Uwaga o kruchości `LIKE`**: dopasowanie po nazwie jest podatne na literówki i warianty zapisu („testo 174 T", „Testo174T", „174-T"). Dopóki nie ma pliku referencyjnego (§7 pkt 5), po migracji należy raportem sprawdzić, czy żaden aktywny model nie został z domyślnymi 16 000 przez brak dopasowania.

### 4.3. Modyfikacja encji JPA

1. **`ThermoRecorderModel.java`** — dodać: `sampleCapacity` (Integer), `minOperatingTempC` / `maxOperatingTempC` (Double), `batteryType` (String), `batteryReplaceable` (Boolean, domyślnie `true`), `batteryLifeDays` (Integer), `batteryLifeRefCycleMin` (Integer, domyślnie `15`), `batteryLifeRefTempC` (Double), `operatingDurationDays` (Integer), `batteryShelfLifeMonths` (Integer). Pole `channelCount` **już istnieje** (`ThermoRecorderModel.java:30-33`).
2. **`ThermoRecorder.java`** — dodać: `batteryRemainingDays` (Integer), `lastBatteryReadAt` (LocalDateTime), `batteryReplacementDate` (LocalDate), `firstActivationDate` (LocalDate). Aktualizacja `batteryRemainingDays` przy każdym odczycie ramką `ab010a` (`TestoRevalidationService`), **z pominięciem wartości `-1`**. Pole `lastBatteryLevelPercent` z pierwotnego wdrożenia **pozostaje w schemacie i encji jako `@Deprecated`** — kolumna jest śladem audytowym tego, co system zapisywał przed korektą protokołu (V36 §3).
3. Obie encje są `@Audited` — zmiany muszą być odzwierciedlone w tabelach `_aud` (§4.1).
4. **`ThermoRecorderModel`** dostaje dwie metody pomocnicze, żeby reguła nie powielała arytmetyki: `getSampleCapacityPerChannel()` (pojemność / `channelCount`) oraz `hasHardwareSpecification()` (czy kartoteka ma komplet danych do oceny W4).

---

## 5. Architektura Klas i Integracja z Planerem

```
com.mac.bry.desktop.service.planner
├── HardwareCapacityService.java                       [NEW] W4a/W4b/W4c
├── dto
│   ├── HardwareBudget.java                            [NEW] T_mem, D_avail, T_mission, wiążące kryterium
│   └── HardwareViolation.java                         [NEW] naruszenie + liczby stojące za odrzuceniem
└── exception
    ├── RecorderOutOfOperatingRangeException.java      [NEW] W4a
    ├── InsufficientRecorderMemoryException.java       [NEW] W4b
    ├── InsufficientBatteryLevelException.java         [NEW] W4c
    └── HardwareDataIncompleteException.java           [NEW] brak danych do oceny
```

**W4b dostaje własny wyjątek, a nie reużywa `InsufficientRecorderCapacityException`.** Tamten opisuje regułę W2 — niedobór **sztuk** sprzętu w oknie czasowym — i niesie `TaskResourceStatus.INSUFFICIENT_CAPACITY` („Brak wolnych rejestratorów") wraz z propozycją najbliższego wolnego okna. Użyty do braku pamięci mówiłby operatorowi „poczekaj na zwolnienie sprzętu", podczas gdy czekanie nie zmieni pojemności bufora; naprawą jest rozrzedzenie interwału albo inny model.

Nowe statusy w `TaskResourceStatus`:

| Status | Znaczenie |
|---|---|
| `HARDWARE_LIMITS_EXCEEDED` | sprzęt nie udźwignie badania — zakres pracy, pamięć albo budżet energii |
| `HARDWARE_DATA_INCOMPLETE` | reguły nie da się rozstrzygnąć; **blokujący celowo**, bo w GxP „nie wiadomo" nie może znaczyć „wolno" |

### 5.1. Punkt wpięcia

`RecorderAllocationService` **nie posiada metody `validateRecorderQualification(...)`**. Publiczne API klasy to `allocateRecorders(...)`, `releaseRecorders(...)` i `requireNoDoubleBooking(...)`, a filtrowanie kandydatów odbywa się w prywatnej metodzie `qualifiedChannelsOf(ThermoRecorder, CoolingChamber, ...)`.

Walidacja W4 wchodzi **do pętli filtrującej kandydatów w `allocateRecorders(...)`**, zaraz za kwalifikacją metrologiczną. Kluczowe: ocena **nie rzuca wyjątku** — zwraca `HardwareBudget`. Rzucenie wewnątrz `qualifiedChannelsOf(...)` przerwałoby całą alokację na pierwszym niepasującym rejestratorze, podczas gdy kandydat ma po prostu wypaść z puli, a planer szukać dalej:

```java
// RecorderAllocationService.allocateRecorders(...)
HardwareBudget budget = hardwareCapacityService.evaluate(recorder, config, chamber, missionStart);
if (!budget.isAcceptable()) {
    rejectedForHardware++;
    if (firstHardwareViolation == null) {
        firstHardwareViolation = budget.firstViolation();
    }
    continue;
}
```

Gdy **cała** pula odpadła na W4, `noQualifiedRecorder(...)` zwraca wyjątek zbudowany z pierwszego naruszenia (`hardwareCapacityService.exceptionFor(...)`) zamiast ogólnego komunikatu metrologicznego — odrzucenie sprzętowe niesie konkretne liczby (próbki, dni, zakres), więc jest najużyteczniejszą przyczyną, jaką planer może pokazać. Licznik odrzuceń sprzętowych trafia też do komunikatu `MetrologicalRangeMismatchException`, gdy przyczyny się mieszają.

Dla **ręcznej podmiany sprzętu** w zaplanowanym zadaniu służy `HardwareCapacityService.require(...)` — wariant rzucający, analogiczny do `requireNoDoubleBooking(...)`.

`missionStart` to `task.getPlannedStep1Time().toLocalDate()`: wiek baterii i zużyty limit loggerów jednorazowych liczą się na moment rozpoczęcia badania, nie na dzień planowania.

### 5.2. Kontrakt serwisu

```java
/** Ocena bez rzucania wyjątku — kandydat ma wypaść z puli, nie przerwać alokacji. */
public HardwareBudget evaluate(ThermoRecorder recorder, ProcedureClassConfig config,
                               CoolingChamber chamber, LocalDate missionStart);

/** Wariant rzucający — ręczna podmiana sprzętu, gdzie operator oczekuje przyczyny odmowy. */
public void require(ThermoRecorder recorder, ProcedureClassConfig config,
                    CoolingChamber chamber, LocalDate missionStart);

/** Przekład naruszenia na wyjątek alokacji z właściwym TaskResourceStatus. */
public RecorderAllocationException exceptionFor(HardwareViolation violation);
```

Kolejność sprawdzania wewnątrz `evaluate(...)`: **W4a → W4b → W4c**. Wszystkie trzy są oceniane (lista naruszeń bywa dłuższa niż jedno), ale `firstViolation()` zwraca najwcześniejsze w tej kolejności — bo zakres pracy jest przyczyną najbardziej podstawową.

Pułapki wychwycone przy wdrożeniu:

* `CoolingChamber.getEffectiveMinTempLimit()` zwraca **`Double`** — brak limitu komory to `DATA_INCOMPLETE`, nigdy `null → 0.0`.
* `batteryRemainingDays` ujemne (sentinel `-1` z importu PDF) lub `null` nie wchodzi do żadnego działania arytmetycznego; kończy się `DATA_INCOMPLETE`.
* Czas misji liczony w `long` — `step4IntervalMinutes × step4SampleCount` przy pojemnościach rzędu 10⁶ przekracza zakres `int`.

---

## 6. Scenariusze Testowe (W4 Test Suite)

Zrealizowane w `HardwareCapacityServiceTest`; test wyłączony adnotacją
`@Disabled("W4 odroczone: ThermoRecorder nie ma pól batteryLevel ani sampleCapacity")`
w `RecorderAllocationServiceTest` został odblokowany i zastąpiony scenariuszami na poziomie puli.

Przyjęte założenia liczbowe: $f_{safety} = 1{,}5$; „misja 21 dni" to `Δt = 10 min × 2950 próbek` plus 20 min umieszczenia, 6 h stabilizacji i 6 h buforu odczytu (razem 30 240 min).

| ID | Warunki | Oczekiwany wynik |
|---|---|---|
| **ST-W4a-01** | Komora −80 °C, rejestrator testo 174 T (zakres −30…+70 °C), bateria 100 % | `RecorderOutOfOperatingRangeException`. **Odrzucenie na zakresie pracy, nie na baterii.** |
| **ST-W4a-02** | Komora −80 °C, rejestrator testo 184 T4 (zakres −80…+70 °C) | W4a zaliczone, walidacja przechodzi dalej |
| **ST-W4b-01** | Δt = 1 min, 14 dni → $N_{req}$ = 20 160; testo 174 T (16 000 / 1 kanał) | `InsufficientRecorderMemoryException`; $T_{mem} = 11{,}1$ dnia |
| **ST-W4b-02** | $N_{req}$ = 20 160; testo 184 T3 (40 000 / 1 kanał) | Zaliczone (limit 40 000) |
| **ST-W4b-03** | $N_{req}$ = 400 000; testo 175 T3 (1 000 000 / **3 kanały** → 333 333 na kanał) | `InsufficientRecorderMemoryException` — **regresja na dzielenie przez `channelCount`** |
| **ST-W4b-04** | testo 174 T, $\Delta t$ = 1 min oraz 10 min | $T_{mem}$ = 15 999 min i 159 990 min — **zgodność co do minuty ze wskazaniem stacji Testo** (§2.3 U1); regresja na odjęcie jedynki |
| **ST-W4c-01** | 184 T4, −80 °C, Δt = 10 min, $D_{ref}$ = 60 dni z urządzenia, misja 21 dni. $D_{avail} = 60 \cdot \tfrac{10}{11} = 54{,}5$ dnia; próg $54{,}5/1{,}5 = 36{,}4$ dnia | Zaliczone |
| **ST-W4c-02** | 184 T4, −80 °C, Δt = 10 min, $D_{ref}$ = 25 dni, misja 21 dni. $D_{avail} = 25 \cdot \tfrac{10}{11} = 22{,}7$ dnia; próg $15{,}2$ dnia | `InsufficientBatteryLevelException` |
| **ST-W4c-03** | 184 T3, +4 °C, Δt = 1 min, $D_{ref}$ = 60 dni. $D_{avail} = 60 \cdot \tfrac{1}{2} = 30{,}0$ dnia; $T_{mem} = 27{,}8$ dnia | Wiąże **bateria**, nie pamięć — asercja na `HardwareBudget.binding()`. Wskazanie wyjściowe obniżone z 375 do 60 dni wraz z korektą §2.5: przy zmierzonym zużyciu energia wiąże wcześniej niż pamięć dopiero blisko wyczerpania ogniwa |
| **ST-W4c-04** | `batteryRemainingDays` = `-1` (sentinel z importu PDF) oraz `null` (nigdy nieodczytany) | `HardwareDataIncompleteException`; $D_{avail}$ = `NaN`, **brak arytmetyki na wartości ujemnej** |
| **ST-W4c-05** | 184 T1 (bateria niewymienna), 80 dni od `firstActivationDate`, misja 21 dni, limit 90 dni | `InsufficientBatteryLevelException` — obowiązuje `operatingDurationDays`; brak odczytu z urządzenia **nie** jest tu brakiem danych |
| **ST-W4c-06** | Δt = 15 min × 96 próbek; $D_{ref}$ = 2 dni → $D_{avail} = 1{,}875$, próg $1{,}25$ dnia mieści sam Krok 4 (1,0 dnia), ale nie pełną misję (1,51 dnia) | Odrzucenie — **regresja na pełny $T_{mission}$**, nie sam Krok 4 |
| **ST-W4c-07** | $D_{ref}$ = 480 dni przy Δt = 15 / 10 / 1 min → $D_{avail}$ = 450,0 / 436,4 / 240,0 dnia | **Kotwica pomiarowa §2.5**: współczynnik cyklu odtwarza zmierzone tempo spadku wskazania. Osobna asercja wyklucza powrót do obalonego $\min(1, \Delta t/\Delta t_{ref})$, który przy Δt = 1 min dawał 32 dni |

Dodatkowo pokryte: przeterminowana bateria mimo wysokiego stanu naładowania (`batteryShelfLifeMonths`), **niezależność budżetu energii od temperatury komory** (ten sam rejestrator w chłodziarce i w zamrażarce −20 °C dostaje identyczny budżet — regresja na usunięty derating), **obecność pełnego wyprowadzenia w komunikacie odrzucenia** — osobno dla baterii wymiennej („urządzenie raportuje … dni pozostałej pracy … cykl … min”) i dla loggera jednorazowego („z limitu pracy urządzenia … dni zużyto …”), mapowanie każdego naruszenia na właściwy typ wyjątku w `require(...)`, oraz na poziomie `RecorderAllocationServiceTest` — odrzucenie całej puli na pamięci i na braku odczytu baterii.

> **Uwaga o locale**: komunikaty naruszeń formatują liczby przez `String.format`, więc separator dziesiętny zależy od locale JVM. Asercje tekstowe muszą budować oczekiwany fragment tym samym wywołaniem — inaczej test przechodzi lokalnie (`pl-PL`), a pada na CI (`en-US`).

---

## 7. Kwestie Otwarte (do potwierdzenia u producenta przed zatwierdzeniem walidacyjnym)

Poniższe punkty **nie mogą zostać rozstrzygnięte oszacowaniem** — dla dokumentacji GxP wymagane jest oświadczenie producenta lub pomiar własny udokumentowany protokołem:

1. **Żywotność baterii poza warunkami referencyjnymi.** Testo publikuje jeden punkt (15 min, +25 °C lub −80 °C). Dla 174 T / 184 T3 pracujących w −20 °C brak danych. Deratingu **nie modelujemy w ogóle** — decyzja z 2026-08-05, uzasadnienie w §3.3. Wcześniejsze ostrzeżenie zostało usunięte jako pozorne zabezpieczenie (powstawało dla każdej komory chłodniczej i nie miało odbiorcy). **To jest świadomie przyjęte ryzyko rezydualne, wymagające decyzji przy zatwierdzaniu walidacyjnym.** Gdyby Kierownik Walidacji uznał je za nieakceptowalne, właściwą reakcją jest pełna ścieżka zatwierdzania (§8 krok 7), a nie przywrócenie ostrzeżenia bez odbiorcy.
2. **Kształt zależności zużycia od cyklu pomiarowego — ZMIERZONE 2026-08-07, §2.5.** Pytanie do producenta jest **zamknięte pomiarem własnym**: $\rho(\Delta t) = 1 + T_c/\Delta t$ przy $T_c \approx 1$ min, czyli zapowiadany model $D = Q/(I_q + q/\Delta t)$ z wyznaczonym $q/I_q$. Człon $\min(1, \Delta t/\Delta t_{ref})$ — założenie własne z wersji 1.1 — został zastąpiony i **nie wolno go przywracać**; pilnuje tego ST-W4c-07.

   **Co pozostaje otwarte — dwa punkty, oba do decyzji przy zatwierdzaniu walidacyjnym:**

   a) **Przenoszalność $T_c$ na 184 T4 w −80 °C.** $T_c$ zmierzono na **174 T w temperaturze pokojowej**, a stosujemy je do wszystkich modeli z baterią wymienną — w tym do 184 T4 pracującego przy −80 °C na ogniwie ER2450T. W niskiej temperaturze rośnie rezystancja wewnętrzna ogniwa, co uderza przede wszystkim w **impulsowy** pobór pomiarowy, a nie w spoczynkowy. Ekstrapolacja **nie jest więc z definicji zachowawcza**: prawdziwe $T_c$ w −80 °C może być większe niż 1 min, a wtedy budżet wychodzi zawyżony. Skala: przy $\Delta t$ = 5 min i wskazaniu 80 dni $D_{avail}$ wynosi **66,7 dnia**, czyli po zapasie ×1,5 dopuszczalne **44,4 dnia** — wobec 26,7 i 17,8 dnia w wersji 1.4. To **świadomie przyjęte ryzyko rezydualne** — tego samego rodzaju co §7 pkt 1 i wymagające tej samej decyzji. Domknięcie wymaga biegu 184 T4 w komorze −80 °C przy $\Delta t$ = 1 min, wobec egzemplarza tego samego modelu w temperaturze pokojowej.

   b) **Zawężenie przedziału $T_c$ (0,8–1,6 min).** Granulacja wskazania to 1 dzień, więc dwie doby biegu nie wystarczą na więcej. Protokół: **ten sam egzemplarz przy Δt = 1 min przez ok. 8–10 dni**, odczyt co dobę. Przy $T_c$ = 1,0 min łączny spadek wyniesie ok. 16–20 dni, przy $T_c$ = 0,3 min — ok. 5; przedział zwęża się proporcjonalnie do długości biegu. Wynik wpisać w `app.planner.w4.measurement-cost-minutes`, bez zmiany kodu. **Ograniczenie operacyjne:** egzemplarz użyty w §2.5 ma 38 dni wskazania, co przy 1 min oznacza ok. 18 dni realnej pracy, a pamięć 174 T (16 000 odczytów) zwiąże jeszcze wcześniej — po ok. 9 dobach trzeba zczytać i przeprogramować.

   > **Dlaczego protokół wymagał Δt = 1 min.** Przy $\Delta t = \Delta t_{ref}$ obie konkurencyjne hipotezy przewidują **ten sam** spadek, więc bieg przy 15 min nie mógłby niczego rozstrzygnąć niezależnie od czasu trwania — to była pierwotna wersja protokołu i była bezużyteczna. Potwierdziło się to w danych: egzemplarze A (15 min) i C (10 min) z §2.5 nie mają wartości dowodowej, cały wynik pochodzi z egzemplarza B.
3. **Zachowanie przy zapełnionej pamięci: zatrzymanie zapisu czy nadpisanie najstarszych odczytów (ring buffer).** Instrukcje wskazują na kryterium stopu „memory full", ale jeśli którykolwiek model nadpisuje dane, przekroczenie W4b oznacza **cichą utratę fragmentu serii pomiarowej**, a nie tylko jej skrócenie — co jest naruszeniem integralności danych wg 21 CFR Part 11 i wymaga podniesienia rangi alertu.
4. **~~Rozdzielczość wskazania baterii z ramki `ab31`~~ — ZAMKNIĘTE 2026-08-05.** Kwestia okazała się źle postawiona: bajt w ogóle nie był stanem baterii (§2.3 U4). Stan baterii pochodzi z osobnej komendy `ab010a` i jest podawany w **dniach**, więc pytanie o rozdzielczość procentu przestaje mieć sens. Zaproponowany wcześniej parametr `app.planner.w4.soc-reading-tolerance-pp` **nie jest już potrzebny** i nie został wdrożony.

   Pytanie pochodne — **przy jakim cyklu pomiarowym urządzenie wyznacza te dni** — też jest już zamknięte. Wskazanie nie zmienia się przy zmianie interwału (§2.3 U3), a schodzi w tempie zależnym od interwału (§2.5), więc jest **stanem ogniwa wyrażonym w dniach odniesienia**, a nie prognozą dla ustawionej konfiguracji. Gdyby było prognozą, spadałoby o 1 dzień na dobę niezależnie od Δt — egzemplarz B gubił 2,05.
5. **Źródło danych katalogowych.** Zasilanie tabeli modeli przez `LIKE` na nazwie jest kruche (§4.2). Docelowo: plik referencyjny `testo_models.yml` wersjonowany w repozytorium, z odnośnikiem do karty katalogowej przy każdej wartości.

---

## 8. Stan Wdrożenia Suplementu W4

| Krok | Zakres | Stan |
|---|---|---|
| **1** | Migracja `common/V34__Thermo_Recorder_Hardware_Limits.sql` wraz z tabelami `_aud`; aktualizacja encji `ThermoRecorder` i `ThermoRecorderModel` | ✅ zrobione |
| **2** | `HardwareCapacityService` z kryteriami W4a/W4b/W4c i typem wynikowym `HardwareBudget` | ✅ zrobione |
| **3** | Zapis stanu baterii przy imporcie USB i PDF (z filtrowaniem sentinela `-1`) | ✅ zrobione — od kroku 16 zapisywane są **dni**, nie procent |
| **4** | Wpięcie w pętlę filtrującą kandydatów w `RecorderAllocationService.allocateRecorders(...)` | ✅ zrobione |
| **5** | Testy `HardwareCapacityServiceTest` (ST-W4a/b/c) + odblokowanie testu W4 w `RecorderAllocationServiceTest` | ✅ zrobione |
| **6** | Aktualizacja BA v5.0: zmiana statusu W4 z *deferred* na *active*; rejestracja kwestii otwartych z §7 w dokumentacji walidacyjnej | ✅ zrobione 2026-08-07 — BA §5 pkt 4 opisuje trzy kryteria, budżet w dniach zamiast progu „>50 %" i odsyła do §7 pkt 1 oraz 2a |
| **7** | Ścieżka zatwierdzania przez Kierownika Walidacji dla pracy poniżej temperatury referencyjnej (§7 pkt 1) | ⬜ do zrobienia — `HardwareBudget.warnings()` usunięte, ścieżkę trzeba zbudować od zera, gdy zapadnie decyzja |
| **8** | Zastąpienie dopasowania `LIKE` plikiem referencyjnym `testo_models.yml` (§7 pkt 5) | ⬜ do zrobienia |
| **9** | Weryfikacja na sprzęcie: „Okres”, niezmienność wskazania baterii wobec interwału, wykluczenie skali 0–255 (§2.3) | ✅ zrobione 2026-08-05 |
| **10** | Korekta $T_{mem}$ o jeden interwał + zgodność co do minuty ze stacją Testo (ST-W4b-04) | ✅ zrobione |
| **11** | Wyprowadzenie budżetu energii w komunikacie odrzucenia (rozbieżność wobec wskazania stacji Testo) | ✅ zrobione |
| **12** | Pomiar zależności zużycia od cyklu — protokół w §7 pkt 2 | ✅ zrobione 2026-08-07 — trzy egzemplarze, Δt = 1/10/15 min (§2.5); $T_c \approx 1$ min |
| **18** | Zastąpienie założonego członu cyklu zmierzonym: `HardwareCapacityService`, parametr `app.planner.w4.measurement-cost-minutes`, przeliczone ST-W4c-01/02/03/06 i nowy ST-W4c-07 | ✅ zrobione 2026-08-07 |
| **19** | Bieg 8–10 dni przy Δt = 1 min zawężający przedział $T_c$ (§7 pkt 2b) | ⬜ do zrobienia — parametr konfigurowalny, korekta nie wymaga zmiany kodu |
| **20** | Bieg 184 T4 w −80 °C weryfikujący przenoszalność $T_c$ na niską temperaturę (§7 pkt 2a) | ⬜ do zrobienia — ryzyko rezydualne do decyzji Kierownika Walidacji |
| **17** | Weryfikacja odczytu i zapisu stanu baterii na sprzęcie (zgodność ze stacją producenta, karta szczegółów) | ✅ zrobione 2026-08-06 — 387 dni w obu aplikacjach |
| **13** | ~~Pomiar granulacji bajtu `ab31[20]`~~ | ❌ nieaktualne — bajt nie był baterią (§2.3 U4) |
| **14** | Odkrycie komendy `ab010a` i korekta map protokołu (`TESTO_USB_ANALYSIS.md`, `ANALIZA_TESTO_174T_FINAL.md`) | ✅ zrobione 2026-08-05 |
| **15** | Odczyt pozostałych dni i progów alarmowych w `testo_usb_reader.py`; sentinel `-1` zamiast fałszywego procentu | ✅ zrobione 2026-08-05 |
| **16** | Model danych w **dniach** zamiast procentów (`V36__Battery_Remaining_Days.sql`, `ThermoRecorder`, `ThermoMeasurementSeries`, W4c, karta rejestratora) | ✅ zrobione 2026-08-06 |

---

## 9. Źródła Specyfikacji Producenta

| Model | Dokument |
|---|---|
| testo 174 T | https://www.testo.com/en-US/testo-174-t/p/0572-1740-01 |
| testo 184 T1/T2/T3 | https://static-int.testo.com/media/1d/ef/1713ebf17722/testo-184T1-T2-T3-Data-sheet.pdf |
| testo 184 T3 | https://www.testo.com/en-US/testo-184-t3/p/0572-1843 |
| testo 184 T4 | https://static.testo.com/image/upload/HQ/testo-184t4-data-sheet.pdf |
| testo 184 — instrukcja obsługi | https://static-int.testo.com/media/f9/28/b521c5648ee6/testo-184_Instruction-Manual-us.pdf |
| testo 175 T1 | https://www.testo.com/en-UK/testo-175-t1/p/0572-1751 |
| testo 176 T3/T4 | https://static-int.testo.com/media/4d/70/2022947b6fed/testo-176-T3-T4-Data-sheet.pdf |
| Broszura 174 / 175 / 176 | https://www.transcat.com/media/pdf/Testo174_175_176Brochure.pdf |

> Dane z tabeli §2 należy zweryfikować względem kart katalogowych obowiązujących w dniu zatwierdzenia dokumentu walidacyjnego — producent aktualizuje specyfikacje wraz z rewizjami sprzętu.