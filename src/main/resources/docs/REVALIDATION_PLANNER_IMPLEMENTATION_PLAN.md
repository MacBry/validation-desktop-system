# PLAN WDROŻENIA (IMPLEMENTATION PLAN)
## Moduł: Inteligentny Planer Rewalidacji Okresowych i Mapowań (Revalidation & Mapping Scheduler)
**System**: `validation-desktop` (Spring Boot 3.5 / JavaFX 21 / Flyway / Hibernate Envers)  
**Lokalizacja**: `src/main/resources/docs/REVALIDATION_PLANNER_IMPLEMENTATION_PLAN.md`  
**Data opracowania**: 2026-07-28  

---

## 1. Architektura i Przepływ Komponentów

Nowy moduł zostanie wbudowany w istniejącą strukturę pakietową `com.mac.bry.desktop`:

```
com.mac.bry.desktop
├── model
│   ├── ProcedureClassConfig.java        [NEW] [@Audited] Encja szablonów klas procedur (5 kroków czasowych)
│   ├── OperatorShiftConfig.java         [NEW] [@Audited] Konfiguracja godzin pracy (06:30-13:30)
│   ├── UserVacation.java                [NEW] [@Audited] Kalendarz urlopów i absencji operatora
│   ├── PlannedValidationTask.java       [NEW] [@Audited] Encja zaplanowanego zadania walidacyjnego
│   ├── PlannedTaskRecorderAssignment.java [NEW] [@Audited] Przypisanie konkretnego rejestratora+kanału do zadania (W2/W5/W8)
│   └── PlannedTaskStatus.java           [NEW] Enum statusu zadania (PLANNED, IN_PROGRESS, READOUT_PENDING, COMPLETED)
├── repository
│   ├── ProcedureClassConfigRepository.java   [NEW]
│   ├── OperatorShiftConfigRepository.java     [NEW]
│   ├── UserVacationRepository.java           [NEW]
│   ├── PlannedValidationTaskRepository.java  [NEW]
│   └── PlannedTaskRecorderAssignmentRepository.java [NEW] Zapytania o zajętość rejestratora w oknie czasowym (W2/W5)
├── service
│   ├── planner
│   │   ├── RevalidationSchedulerEngine.java  [NEW] Główny algorytm sprawdzający reguły W1-W10 i alokację
│   │   ├── TestoDelayCalculatorService.java  [NEW] Wyliczanie opóźnienia startu Testo (Krok 2 + Krok 3)
│   │   ├── OperatorCalendarService.java      [NEW] Weryfikacja okien roboczych (06:30-13:30), świąt i urlopów
│   │   ├── PolishHolidayProvider.java        [NEW] Święta stałe + ruchome PL (Wielkanoc/Boże Ciało — algorytm Meeusa/Gaussa)
│   │   ├── RecorderAllocationService.java    [NEW] Dobór rejestratorów: pula wolnych (W2), brak kolizji (W5), zakres PCA (W8), kalibracja (W1)
│   │   └── PlannerEventNotificationBridge.java [NEW] Nasłuchiwanie zdarzeń (zatwierdzenie raportu, L4)
├── controller
│   ├── PlannerCalendarController.java        [NEW] Widok główny kalendarza/Gantta
│   ├── ProcedureClassConfigController.java   [NEW] Widok konfiguratora klas procedur
│   └── OperatorVacationDialogController.java  [NEW] Dialog zgłaszania urlopów / L4
└── resources
    ├── db/migration/h2/V32__Revalidation_Planner.sql    [NEW] Migracja H2
    ├── db/migration/mysql/V32__Revalidation_Planner.sql [NEW] Migracja MySQL
    │   (ostatnia istniejąca migracja to V31 — brak kolizji numeru)
    └── ui/
        ├── planner_calendar.fxml              [NEW] Layout kalendarza
        └── procedure_class_config.fxml        [NEW] Layout konfiguratora klas
```

---

## 2. Model Bazy Danych (Skrypt Flyway V32)

### A. Tabela `procedure_class_configs`
```sql
CREATE TABLE procedure_class_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    procedure_type VARCHAR(50) NOT NULL, -- PERIODIC_REVALIDATION / MAPPING
    step1_prog_minutes INT NOT NULL DEFAULT 10,
    step2_placement_minutes INT NOT NULL DEFAULT 20,
    step3_stab_hours INT NOT NULL DEFAULT 6,
    step4_interval_minutes INT NOT NULL DEFAULT 180,
    step4_sample_count INT NOT NULL DEFAULT 40,
    step5_readout_buffer_hours INT NOT NULL DEFAULT 6,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
```

### B. Tabela `operator_shift_configs`
```sql
CREATE TABLE operator_shift_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,                          -- NULL = konfiguracja globalna/domyślna
    shift_start TIME NOT NULL DEFAULT '06:30:00',
    shift_end   TIME NOT NULL DEFAULT '13:30:00',
    works_monday    BOOLEAN NOT NULL DEFAULT TRUE,
    works_tuesday   BOOLEAN NOT NULL DEFAULT TRUE,
    works_wednesday BOOLEAN NOT NULL DEFAULT TRUE,
    works_thursday  BOOLEAN NOT NULL DEFAULT TRUE,
    works_friday    BOOLEAN NOT NULL DEFAULT TRUE,
    works_saturday  BOOLEAN NOT NULL DEFAULT FALSE,
    works_sunday    BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
```

### C. Tabela `user_vacations`
```sql
CREATE TABLE user_vacations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason VARCHAR(255),
    is_unplanned_l4 BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### D. Tabela `planned_validation_tasks`
```sql
CREATE TABLE planned_validation_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_number VARCHAR(50) NOT NULL UNIQUE,   -- generowany przez istniejący ValidationPlanNumber
    cooling_chamber_id BIGINT NOT NULL,
    procedure_class_config_id BIGINT NOT NULL,
    procedure_type VARCHAR(50) NOT NULL,        -- PERIODIC_REVALIDATION / MAPPING (GxPProcedureType)
    due_date DATE NOT NULL,
    planned_step1_time TIMESTAMP NOT NULL,
    planned_step2_time TIMESTAMP NOT NULL,
    planned_step3_stab_end TIMESTAMP NOT NULL,
    planned_step4_map_end TIMESTAMP NOT NULL,
    planned_step5_readout_deadline TIMESTAMP NOT NULL,
    calculated_testo_delay_minutes INT NOT NULL,
    required_recorder_count INT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PLANNED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (cooling_chamber_id) REFERENCES cooling_chambers(id),
    FOREIGN KEY (procedure_class_config_id) REFERENCES procedure_class_configs(id)
);
```

### E. Tabela `planned_task_recorder_assignments` (fundament reguł W2/W5/W8/W1)
Bez konkretnego przypisania rejestrator↔zadanie↔okno czasowe nie da się egzekwować pojemności puli (W2), braku podwójnej rezerwacji (W5), zakresu PCA (W8) ani ważności kalibracji (W1).
```sql
CREATE TABLE planned_task_recorder_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    planned_task_id BIGINT NOT NULL,
    thermo_recorder_id BIGINT NOT NULL,
    channel_number INT NOT NULL DEFAULT 1,
    -- okno blokady zasobu = od Kroku 1 (programowanie) do deadline'u odczytu (Krok 5) + bufor logistyczny
    reserved_from TIMESTAMP NOT NULL,
    reserved_until TIMESTAMP NOT NULL,
    FOREIGN KEY (planned_task_id) REFERENCES planned_validation_tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (thermo_recorder_id) REFERENCES thermo_recorders(id)
);
-- Wykrywanie kolizji (W5) po (thermo_recorder_id, channel_number, [reserved_from, reserved_until])
CREATE INDEX idx_ptra_recorder_window ON planned_task_recorder_assignments (thermo_recorder_id, reserved_from, reserved_until);
```

### F. Rozszerzenie tabeli `cooling_chambers` (rozdział cykli — patrz BA R2)
```sql
ALTER TABLE cooling_chambers ADD COLUMN last_periodic_revalidation_date DATE NULL;
-- last_mapping_date pozostaje wyłącznie zegarem 5-letniego mapowania (nie nadpisywać przy rewalidacji rocznej)
```

---

## 3. Algorytm Silnika Planera (`RevalidationSchedulerEngine`)

### Pseudokod Generowania Planu Rocznego:
```java
public List<PlannedValidationTask> generateYearlySchedule(int year) {
    List<CoolingChamber> chambers = chamberRepository.findAllActive();
    List<PlannedValidationTask> scheduledTasks = new ArrayList<>();

    // Jednostka planowania = pojedyncza KOMORA (CoolingChamber). Zapotrzebowanie na poziomie
    // urządzenia to suma zapotrzebowań jego komór (patrz BA R1).
    for (CoolingChamber chamber : chambers) {

        // 1. Ustal typ procedury (Rewalidacja roczna vs Mapowanie 5-letnie) — determinuje który zegar liczy termin
        GxPProcedureType type = determineProcedureType(chamber);

        // 2. Termin z WŁAŚCIWEGO zegara: rewalidacja od lastPeriodicRevalidationDate (+12 mies.),
        //    mapowanie od lastMappingDate (+5 lat). NIGDY nie mieszać tych dat (patrz BA R2).
        LocalDate dueDate = (type == GxPProcedureType.MAPPING)
            ? nvl(chamber.getLastMappingDate(), LocalDate.now()).plusYears(5)
            : nvl(chamber.getLastPeriodicRevalidationDate(), LocalDate.now()).plusYears(1);

        // 3. Pobierz liczbę wymaganych rejestratorów per komora wg VolumeCategory (Zasada R1 & R2)
        int loggerCount = calculateRequiredLoggers(chamber, type);
        if (loggerCount == 0) continue; // Zwolnienie z mapowania dla odczynników (requiresMapping=FALSE)!

        // 4. Znajdź najbliższe bezpieczne okno rano w godzinach 06:30–13:30 (Zasada W9)
        LocalDateTime step1Time = operatorCalendarService.findNextValidShiftStart(dueDate.atTime(6, 30));

        // 5. Dopasuj opóźnienie startu Testo tak, aby Krok 5 wypadł w oknie roboczym
        PlannedValidationTask task = fitTaskToShiftAndLoggerCapacity(chamber, step1Time, loggerCount);

        // 6. Dobierz konkretne rejestratory: pula wolnych w oknie (W2), brak kolizji (W5),
        //    zakres PCA pokrywa zakres materiału (W8), kalibracja ważna do końca pomiaru +7 dni (W1),
        //    Σ kanałów ≥ VolumeCategory.minMeasurementPoints (uwaga metrologiczna R1).
        recorderAllocationService.allocateRecorders(task, chamber); // tworzy PlannedTaskRecorderAssignment[]

        // 7. Zweryfikuj pozostałe reguły W3-W4, W6-W7, W9-W10
        schedulerValidator.validateTask(task);

        scheduledTasks.add(task);
    }
    return scheduledTasks;
}
```

> **Uwaga (strefa czasowa / DST).** Wyliczenia okien (Krok 3 stabilizacja 6h + Krok 4 ~120h przez noc/weekend) przechodzą przez zmianę czasu letni/zimowy. Operować konsekwentnie na `LocalDateTime` w jawnie zadeklarowanej strefie (`Europe/Warsaw`); w testach uwzględnić przebieg obejmujący ostatnią niedzielę marca/października.

---

## 4. Etapy Implementacji (Phase Plan)

| Faza | Zakres Prac | Szacowany Czas |
| :--- | :--- | :---: |
| **Faza 1** | Encje JPA (wszystkie `@Audited` — 21 CFR Part 11), skrypty Flyway V32 (w tym `planned_task_recorder_assignments`, `operator_shift_configs` oraz `ALTER cooling_chambers`) i repozytoria Spring Data. | 1 dzień |
| **Faza 2** | `OperatorCalendarService` + `PolishHolidayProvider` (święta stałe i ruchome) oraz `TestoDelayCalculatorService`. | 1 dzień |
| **Faza 3** | Silnik `RevalidationSchedulerEngine` + `RecorderAllocationService` z kompletem reguł W1–W10. | 2 dni |
| **Faza 4** | Budowa widoków JavaFX w FXML (`planner_calendar.fxml`, `procedure_class_config.fxml`). | 2 dni |
| **Faza 5** | Integracja ze zdarzeniami aplikacji (przycisk zgłaszania L4, autoupdate po wygenerowaniu PDF). | 1 dzień |
| **Faza 6** | Testy jednostkowe, integracyjne oraz weryfikacja GxP. | 1 dzień |
