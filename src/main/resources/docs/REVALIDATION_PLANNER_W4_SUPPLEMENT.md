# SUPLEMENT DO DOKUMENTACJI PLANERA: WDROŻENIE REGUŁY W4
## Moduł: Inteligentny Planer Rewalidacji Okresowych i Mapowań (Revalidation & Mapping Scheduler)
**System**: `validation-desktop` (JavaFX / Spring Boot / GxP / ISO 17025)
**Dokument nadrzędny**: `src/main/resources/docs/REVALIDATION_PLANNER_BA.md` (BA v5.0, reguła W4)
**Data opracowania**: 2026-07-30
**Data korekty**: 2026-07-31 (wersja 1.1)
**Status**: Plan Wdrożenia Suplementu Reguły W4 (Hardware Limits: pamięć, zakres pracy, budżet baterii)

---

## 0. Historia zmian

| Wersja | Data | Zmiana |
|---|---|---|
| 1.0 | 2026-07-30 | Pierwsza wersja planu W4 (model `Battery_eff = Battery × k_temp`) |
| 1.1 | 2026-07-31 | **Korekta modelu matematycznego.** Procentowy derating baterii zastąpiony budżetem czasu pracy w dniach, zgodnym z kartami katalogowymi Testo; dodana twarda bramka zakresu pracy urządzenia; poprawione pojemności pamięci (175 ≠ 176, 184 T1 ≠ T3); uwzględniona liczba kanałów i pełny czas misji; poprawiony punkt wpięcia w `RecorderAllocationService`; migracja rozbita na `h2`/`mysql` wraz z tabelami Envers. |

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
| Stan naładowania baterii [%] | `testo_usb_reader.py:423` → `payload_31[20]` (ramka `ab31`) | Mapowane na `ThermoMeasurementSeries.batteryLevelPercent` |
| Żywotność katalogowa [dni] | `testo_184_config.py:620` → pole XDP `<battery>500</battery>` | Wartość 500 odpowiada karcie katalogowej 184 T3 |
| Interwał pomiaru | `TestoUsbImportService.ImportedSession.intervalMinutes` | |
| Liczba kanałów modelu | `ThermoRecorderModel.channelCount` | **Pole już istnieje** — nie wymaga migracji |
| Sentinel „brak danych” | `TestoRevalidationService.java:112` | `batteryLevelPercent = -1` oznacza **N/D** (odczyt z PDF nie zawiera baterii) |

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
| $D_{spec}$ | katalogowa żywotność baterii [dni] | `model.batteryLifeDays` |
| $\Delta t_{ref}$, $T_{ref}$ | warunki referencyjne specyfikacji | `model.batteryLifeRefCycleMin` (15 min), `model.batteryLifeRefTempC` |
| $SoC$ | ostatni odczytany stan naładowania [%] | `recorder.lastBatteryLevelPercent` |

### 3.1. Kryterium W4a — Zakres pracy (bramka twarda)

Sprawdzane **jako pierwsze**, przed jakąkolwiek arytmetyką baterii:

$$\text{model.minOperatingTempC} \le T_{chamber} \le \text{model.maxOperatingTempC}$$

* **Błąd walidacji**: `RecorderOutOfOperatingRangeException` — rejestrator nie jest przewidziany do pracy w tej komorze.
* Uwaga implementacyjna: `CoolingChamber.getEffectiveMinTempLimit()` zwraca **`Double` (nullable)**; brak limitu komory musi być obsłużony jawnie (odrzucenie z komunikatem o brakującej konfiguracji komory, nigdy `null → 0.0`).

### 3.2. Kryterium W4b — Budżet pamięci

Liczba próbek wymaganych przez klasę procedury nie może przekroczyć pamięci przypadającej na kanał:

$$N_{req} \le \left\lfloor \frac{N_{max}}{n_{ch}} \right\rfloor$$

Równoważnie, maksymalny czas rejestracji wynikający z pamięci:

$$T_{mem}[\text{dni}] = \frac{N_{max}}{n_{ch}} \cdot \frac{\Delta t}{1440}$$

* **Błąd walidacji**: `InsufficientRecorderCapacityException` (klasa już istnieje w `service/planner/exception/`).
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

**Dostępny budżet energii** wyznaczamy z danych katalogowych, skalowanych cyklem pomiarowym i stanem naładowania:

$$D_{avail}[\text{dni}] = D_{spec} \cdot \min\!\left(1,\ \frac{\Delta t}{\Delta t_{ref}}\right) \cdot \frac{SoC}{100}$$

**Warunek dopuszczenia:**

$$\frac{T_{mission}}{1440} \le \frac{D_{avail}}{f_{safety}}, \qquad f_{safety} = 1{,}5 \ \text{(domyślnie, konfigurowalne w SOP)}$$

* **Błąd walidacji**: `InsufficientBatteryLevelException`.
* **Dobór $D_{spec}$**: bierzemy wartość katalogową **dla warunków najbliższych warunkom misji**. Dla 184 T4 w −80 °C jest to wprost 100 dni. Dla 174 T w zamrażarce −20 °C producent nie publikuje wartości — patrz §7 (kwestia otwarta). Do czasu jej rozstrzygnięcia obowiązuje **ograniczenie konserwatywne**: brak danych dla $T_{chamber} < T_{ref}$ oznacza wymaganie ręcznego zatwierdzenia przez Kierownika Walidacji (ostrzeżenie w planerze, nigdy ciche przepuszczenie).
* **Uzasadnienie członu $\min(1, \Delta t/\Delta t_{ref})$**: specyfikacja jest podana przy 15 min; przy szybszym cyklu zużycie rośnie, przy wolniejszym nie zakładamy zysku (pobór spoczynkowy: LCD, zegar, NFC). Człon jest zachowawczy w obie strony — patrz §7.
* **Sentinel N/D**: jeżeli $SoC < 0$ (wartość `-1`), reguła W4c **nie liczy nic** — zwraca status `UNKNOWN` i blokuje zadanie z komunikatem o konieczności odczytu stanu baterii ze stacji USB. Arytmetyka na `-1` jest niedopuszczalna.
* **Bateria niewymienna (184 T1/T2)**: zamiast $D_{avail}$ obowiązuje pozostały limit `operatingDurationDays`, liczony od `firstActivationDate`.

### 3.4. Które kryterium wiąże jako pierwsze

Punkt przecięcia $T_{mem} = D_{avail}$ (przy $SoC = 100\%$, $f_{safety} = 1$):

| Model | Przecięcie | Interpretacja |
|---|---|---|
| 174 T @ +25 °C | Δt ≈ 45 min | poniżej 45 min ogranicza **pamięć** |
| 184 T3 @ +25 °C | Δt ≈ 18 min | poniżej 18 min ogranicza **pamięć** |
| **184 T4 @ −80 °C** | **Δt ≈ 3,6 min** | przy typowym mapowaniu (Δt ≥ 5 min) ogranicza **bateria** |

Wniosek projektowy: w komorach ultra‑niskotemperaturowych realnym ograniczeniem planowania jest **energia**, a nie pamięć — i żaden model oparty wyłącznie na progu procentowym tego nie wychwyci.

---

## 4. Rozszerzenia Bazy Danych i Encji JPA

### 4.1. Umiejscowienie migracji

Tabele `thermo_recorders` i `thermo_recorder_models` powstały w migracjach **vendorowych** (`db/migration/h2/V24__MultiChannelRecorders.sql` oraz `db/migration/mysql/V24__MultiChannelRecorders.sql`), nie w `common/`. Migrację W4 należy zatem:

1. dodać **w obu katalogach** (`h2/` i `mysql/`) jako `V34__Thermo_Recorder_Hardware_Limits.sql`,
2. objąć **również tabele Envers** `thermo_recorder_models_aud` i `thermo_recorders_aud` — inaczej `PlannerEnversMySqlIntegrationTest` i walidacja schematu Hibernate zgłoszą niezgodność,
3. używać typów zgodnych z oboma silnikami (`DOUBLE`, nie `DOUBLE PRECISION`).

### 4.2. Skrypt `V34__Thermo_Recorder_Hardware_Limits.sql` (wariant MySQL)

```sql
-- 1. Model rejestratora: pojemność pamięci, zakres pracy, dane katalogowe baterii
ALTER TABLE thermo_recorder_models
    ADD COLUMN sample_capacity            INT     NOT NULL DEFAULT 16000,
    ADD COLUMN min_operating_temp_c       DOUBLE  NULL,
    ADD COLUMN max_operating_temp_c       DOUBLE  NULL,
    ADD COLUMN battery_type               VARCHAR(50) NULL,
    ADD COLUMN battery_replaceable        BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN battery_life_days          INT     NULL,
    ADD COLUMN battery_life_ref_cycle_min INT     NOT NULL DEFAULT 15,
    ADD COLUMN battery_life_ref_temp_c    DOUBLE  NULL,
    ADD COLUMN operating_duration_days    INT     NULL,
    ADD COLUMN battery_shelf_life_months  INT     NULL;

ALTER TABLE thermo_recorder_models_aud
    ADD COLUMN sample_capacity            INT     NULL,
    ADD COLUMN min_operating_temp_c       DOUBLE  NULL,
    ADD COLUMN max_operating_temp_c       DOUBLE  NULL,
    ADD COLUMN battery_type               VARCHAR(50) NULL,
    ADD COLUMN battery_replaceable        BOOLEAN NULL,
    ADD COLUMN battery_life_days          INT     NULL,
    ADD COLUMN battery_life_ref_cycle_min INT     NULL,
    ADD COLUMN battery_life_ref_temp_c    DOUBLE  NULL,
    ADD COLUMN operating_duration_days    INT     NULL,
    ADD COLUMN battery_shelf_life_months  INT     NULL;

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
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%175%';

UPDATE thermo_recorder_models SET
    sample_capacity = 2000000, min_operating_temp_c = -40, max_operating_temp_c = 70,
    battery_type = 'AA', battery_life_days = 2920, battery_life_ref_temp_c = 25
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%176%';

-- 3. Egzemplarz rejestratora: ostatni znany stan baterii
ALTER TABLE thermo_recorders
    ADD COLUMN last_battery_level_percent INT       NULL,
    ADD COLUMN last_battery_read_at       TIMESTAMP NULL,
    ADD COLUMN battery_replacement_date   DATE      NULL,
    ADD COLUMN first_activation_date      DATE      NULL;

ALTER TABLE thermo_recorders_aud
    ADD COLUMN last_battery_level_percent INT       NULL,
    ADD COLUMN last_battery_read_at       TIMESTAMP NULL,
    ADD COLUMN battery_replacement_date   DATE      NULL,
    ADD COLUMN first_activation_date      DATE      NULL;
```

> **Uwaga o kruchości `LIKE`**: dopasowanie po nazwie jest podatne na literówki i warianty zapisu („testo 174 T", „Testo174T", „174-T"). Dopóki nie ma pliku referencyjnego (§7 pkt 5), po migracji należy raportem sprawdzić, czy żaden aktywny model nie został z domyślnymi 16 000 przez brak dopasowania.

### 4.3. Modyfikacja encji JPA

1. **`ThermoRecorderModel.java`** — dodać: `sampleCapacity` (Integer), `minOperatingTempC` / `maxOperatingTempC` (Double), `batteryType` (String), `batteryReplaceable` (Boolean, domyślnie `true`), `batteryLifeDays` (Integer), `batteryLifeRefCycleMin` (Integer, domyślnie `15`), `batteryLifeRefTempC` (Double), `operatingDurationDays` (Integer), `batteryShelfLifeMonths` (Integer). Pole `channelCount` **już istnieje** (`ThermoRecorderModel.java:30-33`).
2. **`ThermoRecorder.java`** — dodać: `lastBatteryLevelPercent` (Integer), `lastBatteryReadAt` (LocalDateTime), `batteryReplacementDate` (LocalDate), `firstActivationDate` (LocalDate). Aktualizacja `lastBatteryLevelPercent` przy każdym odczycie z ramki `ab31` (`TestoUsbImportService`), **z pominięciem wartości `-1`**.
3. Obie encje są `@Audited` — zmiany muszą być odzwierciedlone w tabelach `_aud` (§4.1).

---

## 5. Architektura Klas i Integracja z Planerem

```
com.mac.bry.desktop.service.planner
├── HardwareCapacityService.java                       [NEW] W4a/W4b/W4c
├── dto
│   └── HardwareBudget.java                            [NEW] wynik: T_mem, D_avail, wiążące kryterium
└── exception
    ├── InsufficientRecorderCapacityException.java     [EXISTING] W4b
    ├── InsufficientBatteryLevelException.java         [NEW] W4c
    ├── RecorderOutOfOperatingRangeException.java      [NEW] W4a
    └── BatteryStatusUnknownException.java             [NEW] W4c, SoC = N/D
```

### 5.1. Punkt wpięcia

`RecorderAllocationService` **nie posiada metody `validateRecorderQualification(...)`**. Publiczne API klasy to `allocateRecorders(...)`, `releaseRecorders(...)` i `requireNoDoubleBooking(...)`, a filtrowanie kandydatów odbywa się w prywatnej metodzie `qualifiedChannelsOf(ThermoRecorder, CoolingChamber, ...)`.

Walidację W4 wpinamy **w `qualifiedChannelsOf(...)`**, obok istniejącej kwalifikacji metrologicznej — dzięki temu rejestrator niespełniający W4 nie trafia do puli kandydatów, zamiast być odrzucanym dopiero po wyborze:

```java
// RecorderAllocationService.qualifiedChannelsOf(...)
private List<Integer> qualifiedChannelsOf(ThermoRecorder recorder, CoolingChamber chamber,
                                          ProcedureClassConfig config, ...) {
    // W4a + W4b + W4c — twarde odrzucenie kandydata
    hardwareCapacityService.validateHardwareBudget(recorder, config, chamber);

    // ... istniejąca kwalifikacja metrologiczna i kalibracyjna
}
```

Jeżeli reguła ma raportować przyczynę odrzucenia w UI planera (a nie tylko usuwać kandydata z puli), `HardwareCapacityService` powinien zwracać `HardwareBudget` z listą naruszeń, a `noQualifiedRecorder(...)` agregować je do komunikatu — analogicznie do obecnej obsługi `hasChannelRejectedOnlyForCalibration(...)`.

### 5.2. Szkic serwisu

```java
public HardwareBudget validateHardwareBudget(ThermoRecorder recorder,
                                             ProcedureClassConfig config,
                                             CoolingChamber chamber) {
    ThermoRecorderModel model = recorder.getModel();

    // --- W4a: zakres pracy urządzenia ---
    Double chamberTemp = chamber.getEffectiveMinTempLimit();
    if (chamberTemp == null) {
        throw new RecorderOutOfOperatingRangeException(
                "Komora " + chamber.getName() + " nie ma skonfigurowanego dolnego limitu temperatury");
    }
    if (model.getMinOperatingTempC() != null && chamberTemp < model.getMinOperatingTempC()) {
        throw new RecorderOutOfOperatingRangeException(String.format(
                "Rejestrator S/N:%s (%s) pracuje od %.1f°C, a komora wymaga %.1f°C",
                recorder.getSerialNumber(), model.getName(),
                model.getMinOperatingTempC(), chamberTemp));
    }

    // --- W4b: budżet pamięci (dzielony między kanały) ---
    int perChannelCapacity = model.getSampleCapacity() / Math.max(1, model.getChannelCount());
    int requiredSamples = config.getStep4SampleCount();
    if (requiredSamples > perChannelCapacity) {
        throw new InsufficientRecorderCapacityException(String.format(
                "Rejestrator S/N:%s (%s) ma %d próbek na kanał (%d / %d kanałów), a procedura wymaga %d",
                recorder.getSerialNumber(), model.getName(), perChannelCapacity,
                model.getSampleCapacity(), model.getChannelCount(), requiredSamples));
    }

    // --- W4c: budżet energii ---
    return batteryBudget(recorder, config, chamberTemp);
}
```

---

## 6. Scenariusze Testowe (W4 Test Suite)

Miejsce docelowe: `HardwareCapacityServiceTest` oraz odblokowanie testu wyłączonego adnotacją
`@Disabled("W4 odroczone: ThermoRecorder nie ma pól batteryLevel ani sampleCapacity")` w `RecorderAllocationServiceTest.java:278`.

| ID | Warunki | Oczekiwany wynik |
|---|---|---|
| **ST-W4a-01** | Komora −80 °C, rejestrator testo 174 T (zakres −30…+70 °C), bateria 100 % | `RecorderOutOfOperatingRangeException`. **Odrzucenie na zakresie pracy, nie na baterii.** |
| **ST-W4a-02** | Komora −80 °C, rejestrator testo 184 T4 (zakres −80…+70 °C) | W4a zaliczone, walidacja przechodzi dalej |
| **ST-W4b-01** | Δt = 1 min, 14 dni → $N_{req}$ = 20 160; testo 174 T (16 000 / 1 kanał) | `InsufficientRecorderCapacityException` |
| **ST-W4b-02** | $N_{req}$ = 20 160; testo 184 T3 (40 000 / 1 kanał) | Zaliczone (limit 40 000) |
| **ST-W4b-03** | $N_{req}$ = 400 000; testo 175 T3 (1 000 000 / **3 kanały** → 333 333 na kanał) | `InsufficientRecorderCapacityException` — **regresja na dzielenie przez `channelCount`** |
| **ST-W4c-01** | 184 T4, −80 °C, Δt = 10 min, SoC = 60 %, misja 21 dni. $D_{avail} = 100 \cdot 1 \cdot 0{,}6 = 60$ dni; próg $60/1{,}5 = 40$ dni | Zaliczone |
| **ST-W4c-02** | 184 T4, −80 °C, Δt = 10 min, SoC = 25 %, misja 21 dni. $D_{avail} = 25$ dni; próg $16{,}7$ dni | `InsufficientBatteryLevelException` |
| **ST-W4c-03** | 184 T3, +4 °C, Δt = 1 min, SoC = 75 %. $D_{avail} = 500 \cdot (1/15) \cdot 0{,}75 = 25$ dni; $T_{mem} = 27{,}8$ dni | Wiąże **bateria**, nie pamięć — asercja na polu `bindingConstraint` |
| **ST-W4c-04** | SoC = `-1` (odczyt z PDF bez informacji o baterii) | `BatteryStatusUnknownException`; **brak arytmetyki na wartości ujemnej** |
| **ST-W4c-05** | 184 T1 (bateria niewymienna), 80 dni od `firstActivationDate`, misja 21 dni, limit 90 dni | `InsufficientBatteryLevelException` — obowiązuje `operatingDurationDays` |
| **ST-W4c-06** | Misja obejmuje stabilizację 6 h + Krok 4 + bufor odczytu 6 h; budżet mieści wyłącznie Krok 4 | Odrzucenie — **regresja na pełny $T_{mission}$**, nie sam Krok 4 |

---

## 7. Kwestie Otwarte (do potwierdzenia u producenta przed zatwierdzeniem walidacyjnym)

Poniższe punkty **nie mogą zostać rozstrzygnięte oszacowaniem** — dla dokumentacji GxP wymagane jest oświadczenie producenta lub pomiar własny udokumentowany protokołem:

1. **Żywotność baterii poza warunkami referencyjnymi.** Testo publikuje jeden punkt (15 min, +25 °C lub −80 °C). Dla 174 T / 184 T3 pracujących w −20 °C brak danych. Do czasu ich uzyskania obowiązuje ścieżka ręcznego zatwierdzenia (§3.3).
2. **Kształt zależności zużycia od cyklu pomiarowego.** Przyjęty człon $\min(1, \Delta t/\Delta t_{ref})$ jest zachowawczy (zakłada zużycie proporcjonalne do liczby odczytów poniżej 15 min i brak zysku powyżej). Rzeczywisty pobór to suma składowej spoczynkowej i pomiarowej — dwa punkty pomiarowe od producenta pozwoliłyby zastąpić go modelem $D = Q/(I_q + q/\Delta t)$.
3. **Zachowanie przy zapełnionej pamięci: zatrzymanie zapisu czy nadpisanie najstarszych odczytów (ring buffer).** Instrukcje wskazują na kryterium stopu „memory full", ale jeśli którykolwiek model nadpisuje dane, przekroczenie W4b oznacza **cichą utratę fragmentu serii pomiarowej**, a nie tylko jej skrócenie — co jest naruszeniem integralności danych wg 21 CFR Part 11 i wymaga podniesienia rangi alertu.
4. **Interpretacja wskazania procentowego baterii z ramki `ab31`.** Nie jest udokumentowane, czy jest to pomiar napięcia, czy licznik zużycia — a to determinuje, czy mnożenie $D_{spec} \cdot SoC/100$ jest uprawnione.
5. **Źródło danych katalogowych.** Zasilanie tabeli modeli przez `LIKE` na nazwie jest kruche (§4.2). Docelowo: plik referencyjny `testo_models.yml` wersjonowany w repozytorium, z odnośnikiem do karty katalogowej przy każdej wartości.

---

## 8. Harmonogram Wdrożenia Suplementu W4

| Krok | Zakres | Zależności |
|---|---|---|
| **1** | Migracje `V34__Thermo_Recorder_Hardware_Limits.sql` w `h2/` **i** `mysql/`, wraz z tabelami `_aud`; aktualizacja encji `ThermoRecorder` i `ThermoRecorderModel` | — |
| **2** | `HardwareCapacityService` z kryteriami W4a/W4b/W4c i typem wynikowym `HardwareBudget` | Krok 1 |
| **3** | Zapis `lastBatteryLevelPercent` przy imporcie USB (z filtrowaniem sentinela `-1`) | Krok 1 |
| **4** | Wpięcie w `RecorderAllocationService.qualifiedChannelsOf(...)` i aktywacja W4 w `RevalidationSchedulerEngine` | Kroki 2–3 |
| **5** | Testy `HardwareCapacityServiceTest` (ST-W4a/b/c) + odblokowanie `RecorderAllocationServiceTest:278` | Krok 4 |
| **6** | Aktualizacja BA v5.0: zmiana statusu W4 z *deferred* na *active*; rejestracja kwestii otwartych z §7 w dokumentacji walidacyjnej | Krok 5 |

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