# SUPLEMENT DO DOKUMENTACJI PLANERA: WDROŻENIE REGUŁY W4
## Moduł: Inteligentny Planer Rewalidacji Okresowych i Mapowań (Revalidation & Mapping Scheduler)
**System**: `validation-desktop` (JavaFX / Spring Boot / GxP / ISO 17025)  
**Dokument źródłowy**: `src/main/resources/docs/REVALIDATION_PLANNER_W4_SUPPLEMENT.md`  
**Data opracowania**: 2026-07-30  
**Status**: Plan Wdrożenia Suplementu Reguły W4 (Hardware & Battery Capacity Invariant)  

---

## 1. Cel Suplementu i Zakres Projektowy

Niniejszy suplement precyzuje specyfikację techniczną, model matematyczny oraz plan wdrożenia dla **Reguły W4 (Walidacja Limitów Sprzętowych Rejestratora: Pamięć i Bateria)**.

W pierwotnej wersji dokumentacji BA v5.0 reguła W4 została oznaczona jako *deferred* (odroczona) z uwagi na brak w encjach `ThermoRecorder` oraz `ThermoRecorderModel` pól przechowujących pojemność pamięci próbówek oraz poziom naładowania baterii. Suplement ten definiuje pełny cykl aktywacji reguły W4 w systemie.

---

## 2. Rozszerzenia Bazy Danych i Encji JPA (Skrypt Flyway V34)

### A. Skrypt Migracji `V34__Thermo_Recorder_Hardware_Limits.sql`

```sql
-- 1. Rozszerzenie modelu rejestratora o pojemność pamięci i specyfikację baterii
ALTER TABLE thermo_recorder_models 
ADD COLUMN sample_capacity INT NOT NULL DEFAULT 16000,
ADD COLUMN battery_type VARCHAR(50) DEFAULT 'CR2032',
ADD COLUMN min_operating_temp DOUBLE PRECISION DEFAULT -30.0,
ADD COLUMN max_operating_temp DOUBLE PRECISION DEFAULT 70.0;

-- Ustawienie właściwych wartości domyślnych dla znanych modeli Testo
UPDATE thermo_recorder_models SET sample_capacity = 16000 WHERE name LIKE '%174T%';
UPDATE thermo_recorder_models SET sample_capacity = 2000000 WHERE name LIKE '%175%';
UPDATE thermo_recorder_models SET sample_capacity = 40000 WHERE name LIKE '%184%';

-- 2. Rozszerzenie fizycznego egzemplarza rejestratora o status baterii
ALTER TABLE thermo_recorders 
ADD COLUMN last_battery_level_percent INT NULL,
ADD COLUMN last_battery_read_at TIMESTAMP NULL,
ADD COLUMN battery_replacement_date DATE NULL,
ADD COLUMN max_battery_lifespan_months INT DEFAULT 24;
```

---

### B. Modyfikacja Encji JPA

1. **`ThermoRecorderModel.java`**:
   * Dodanie pola `private Integer sampleCapacity;` (domyślnie `16000`).
   * Dodanie pola `private String batteryType;`.
   * Dodanie pól `private Double minOperatingTemp;` oraz `private Double maxOperatingTemp;`.

2. **`ThermoRecorder.java`**:
   * Dodanie pola `private Integer lastBatteryLevelPercent;` (aktualizowanego automatycznie podczas każdego odczytu z ramki `ab31` Testo USB).
   * Dodanie pola `private LocalDateTime lastBatteryReadAt;`.
   * Dodanie pola `private LocalDate batteryReplacementDate;`.

---

## 3. Matematyczny Model Walidacji Reguły W4 (Capacity & Battery Engine)

Silnik `HardwareCapacityService` przed zatwierdzeniem alokacji rejestratora do zadania weryfikuje dwa niezależne kryteria:

### A. Kryterium 1: Pojemność Pamięci (Sample Capacity Limit)
Liczba pomiarów wymaganych przez wybraną klasę procedury ($N_{req}$) nie może przekraczać maksymalnej pojemności bufora pamięci rejestratora ($N_{max}$).

$$N_{req} = \frac{T_{map\_minutes}}{\Delta t_{interval\_minutes}} \le \text{model.sampleCapacity}$$

* **Błąd walidacji**: Jeżeli $N_{req} > N_{max}$, system rzuca wyjątek `InsufficientRecorderCapacityException` i blokuje zadanie.

---

### B. Kryterium 2: Kompensowana Temperaturą Żywotność Baterii (Temperature-Derated Battery Rule)

Baterie litowe i alkaiczne w temperaturach ujemnych (np. w zamrażarkach $-20^{\circ}\text{C}$ lub ultra-kriostatach $-80^{\circ}\text{C}$) doświadczają spadku napięcia i pojemności użytecznej. Silnik stosuje **współczynnik degradacji temperaturowej ($k_{temp}$)**:

$$k_{temp} = \begin{cases}
1.0 & \text{dla } T_{chamber} \ge 0^{\circ}\text{C} \quad (\text{Chłodziarki }+2\dots+8^{\circ}\text{C}) \\
0.7 & \text{dla } -30^{\circ}\text{C} \le T_{chamber} < 0^{\circ}\text{C} \quad (\text{Zamrażarki }-20^{\circ}\text{C}) \\
0.4 & \text{dla } T_{chamber} < -30^{\circ}\text{C} \quad (\text{Ultra-Kriostaty }-80^{\circ}\text{C})
\end{cases}$$

**Efektywny Poziom Naładowania Baterii ($Battery_{eff}$)**:
$$Battery_{eff} = Battery_{last\_known} \times k_{temp}$$

**Warunek dopuszczenia rejestratora do zadania (Reguła W4)**:
1. $Battery_{last\_known} \ge 50\%$ (Absolutny poziom naładowania ze stacji Testo USB).
2. $Battery_{eff} \ge 30\%$ (Efektywna pojemność w temperaturze pracy komory).
3. Data od ostatniej wymiany baterii $\le \text{max\_battery\_lifespan\_months}$ (zazwyczaj 24 miesiące).

---

## 4. Architektura Klas i Integracja z Planerem

```
com.mac.bry.desktop.service.planner
├── HardwareCapacityService.java         [NEW] Serwis wyliczania pamięci i deratingu baterii
├── exception
│   ├── InsufficientRecorderCapacityException.java [EXISTING] (Uruchomienie dla limitu pamięci)
│   └── InsufficientBatteryLevelException.java     [NEW] Wyjątek braku energii baterii
```

### Integracja z `RecorderAllocationService`:
```java
// Wewnątrz metody RecorderAllocationService.validateRecorderQualification(...)
public void validateHardwareCapacity(ThermoRecorder recorder, ProcedureClassConfig config, CoolingChamber chamber) {
    // 1. Sprawdzenie pojemności pamięci
    int requiredSamples = config.getStep4SampleCount();
    int maxCapacity = recorder.getModel().getSampleCapacity();
    if (requiredSamples > maxCapacity) {
        throw new InsufficientRecorderCapacityException(
            String.format("Rejestrator S/N:%s (Model: %s) ma pojemność %d próbek, a procedura wymaga %d próbek",
                recorder.getSerialNumber(), recorder.getModel().getName(), maxCapacity, requiredSamples)
        );
    }

    // 2. Sprawdzenie poziomu baterii z uwzględnieniem deratingu temperaturotwego
    double chamberMinTemp = chamber.getEffectiveMinTempLimit();
    hardwareCapacityService.verifyBatteryLevel(recorder, chamberMinTemp);
}
```

---

## 5. Scenariusze Testowe (W4 Test Suite)

### ST-W4-01: Odrzucenie rejestratora z uwagi na przepełnienie pamięci
* **Warunki**: Procedura wymagająca 20 000 próbek (próbkowanie co 1 min przez 14 dni). Rejestrator Testo 174T z limitem 16 000 próbek.
* **Wynik**: Rzucenie wyjątku `InsufficientRecorderCapacityException`. Zadanie zablokowane.

### ST-W4-02: Odrzucenie rejestratora w strefie $-80^{\circ}\text{C}$ z niską baterią
* **Warunki**: Zamrażarka $-80^{\circ}\text{C}$ ($k_{temp} = 0.4$). Rejestrator z Ostatnio zmierzonym poziomem baterii $60\%$.
* **Wyliczenie**: $Battery_{eff} = 60\% \times 0.4 = 24\%$ ($<30\%$).
* **Wynik**: Rzucenie wyjątku `InsufficientBatteryLevelException`. Zadanie zablokowane z komunikatem: *"Niewystarczający poziom baterii do pracy w temperaturze -80°C"*.

### ST-W4-03: Akceptacja rejestratora w chłodziarce $+4^{\circ}\text{C}$
* **Warunki**: Chłodziarka $+4^{\circ}\text{C}$ ($k_{temp} = 1.0$). Rejestrator z baterią $75\%$.
* **Wynik**: Walidacja W4 zaliczona pomyślnie. Rejestrator przypisany.

---

## 6. Harmonogram Wdrożenia Suplementu W4

1. **Krok 1**: Utworzenie skryptu Flyway `V34__Thermo_Recorder_Hardware_Limits.sql` oraz aktualizacja encji `ThermoRecorder` i `ThermoRecorderModel`.
2. **Krok 2**: Implementacja serwisu `HardwareCapacityService` z przelicznikiem $k_{temp}$.
3. **Krok 3**: Podłączenie walidacji do `RecorderAllocationService` i uaktywnienie reguły W4 w `RevalidationSchedulerEngine`.
4. **Krok 4**: Dopisanie automatycznych testów jednostkowych w `HardwareCapacityServiceTest` oraz `RecorderAllocationServiceTest`.
