-- V32: Inteligentny Planer Rewalidacji Okresowych i Mapowań
-- Zakres: szablony klas procedur, okno pracy operatora, nieobecności,
--         zaplanowane zadania walidacyjne i rezerwacje kanałów rejestratorów.
--
-- Uwaga: ddl-auto=none, więc każda tabela _aud dla encji @Audited musi być
-- utworzona ręcznie (wzorzec z V18/V21). Envers nie utworzy ich sam.
--
-- Uwaga H2: jedno ADD COLUMN na ALTER — H2 nie wspiera wielokrotnego
-- ADD COLUMN w jednym poleceniu (patrz różnica wariantów w V31).

-- ---------------------------------------------------------------------------
-- A. Szablony klas procedur (5 kroków czasowych, BA §3)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS procedure_class_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    procedure_type VARCHAR(50) NOT NULL,
    step1_prog_minutes INT NOT NULL DEFAULT 10,
    step2_placement_minutes INT NOT NULL DEFAULT 20,
    step3_stab_hours INT NOT NULL DEFAULT 6,
    step4_interval_minutes INT NOT NULL DEFAULT 180,
    step4_sample_count INT NOT NULL DEFAULT 40,
    step5_readout_buffer_hours INT NOT NULL DEFAULT 6,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS procedure_class_configs_aud (
    id BIGINT NOT NULL,
    rev INT NOT NULL,
    revtype TINYINT,
    name VARCHAR(100),
    procedure_type VARCHAR(50),
    step1_prog_minutes INT,
    step2_placement_minutes INT,
    step3_stab_hours INT,
    step4_interval_minutes INT,
    step4_sample_count INT,
    step5_readout_buffer_hours INT,
    active BOOLEAN,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_pcc_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- ---------------------------------------------------------------------------
-- B. Okno pracy operatora (W9). user_id NULL = konfiguracja globalna.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS operator_shift_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    shift_start TIME NOT NULL DEFAULT '06:30:00',
    shift_end   TIME NOT NULL DEFAULT '13:30:00',
    works_monday    BOOLEAN NOT NULL DEFAULT TRUE,
    works_tuesday   BOOLEAN NOT NULL DEFAULT TRUE,
    works_wednesday BOOLEAN NOT NULL DEFAULT TRUE,
    works_thursday  BOOLEAN NOT NULL DEFAULT TRUE,
    works_friday    BOOLEAN NOT NULL DEFAULT TRUE,
    works_saturday  BOOLEAN NOT NULL DEFAULT FALSE,
    works_sunday    BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_osc_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS operator_shift_configs_aud (
    id BIGINT NOT NULL,
    rev INT NOT NULL,
    revtype TINYINT,
    user_id BIGINT,
    shift_start TIME,
    shift_end   TIME,
    works_monday    BOOLEAN,
    works_tuesday   BOOLEAN,
    works_wednesday BOOLEAN,
    works_thursday  BOOLEAN,
    works_friday    BOOLEAN,
    works_saturday  BOOLEAN,
    works_sunday    BOOLEAN,
    active BOOLEAN,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_osc_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- ---------------------------------------------------------------------------
-- C. Nieobecności operatora — urlopy i nieplanowane L4 (W9, W10)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_vacations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason VARCHAR(255),
    is_unplanned_l4 BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_uv_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_uv_user_range ON user_vacations (user_id, start_date, end_date);

CREATE TABLE IF NOT EXISTS user_vacations_aud (
    id BIGINT NOT NULL,
    rev INT NOT NULL,
    revtype TINYINT,
    user_id BIGINT,
    start_date DATE,
    end_date DATE,
    reason VARCHAR(255),
    is_unplanned_l4 BOOLEAN,
    created_at DATETIME,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_uv_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- ---------------------------------------------------------------------------
-- D. Zaplanowane zadania walidacyjne
--
-- task_number NIE jest unikalny: to numer RPW urządzenia, a jednostką
-- planowania jest komora (BA R1), więc urządzenie dwukomorowe generuje dwa
-- zadania o tym samym numerze. Realny duplikat pilnuje uq_pvt_chamber_type_due.
--
-- resource_status/shortage_reason/suggested_window_start to oś niezależna od
-- status: zadanie bez obsady pozostaje PLANNED, a przyczyna braku zasobów
-- jest zapisana wprost (wymóg audit trail 21 CFR Part 11, ST-W2-01).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS planned_validation_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_number VARCHAR(50) NOT NULL,
    cooling_chamber_id BIGINT NOT NULL,
    procedure_class_config_id BIGINT NOT NULL,
    procedure_type VARCHAR(50) NOT NULL,
    due_date DATE NOT NULL,
    planned_step1_time DATETIME NOT NULL,
    planned_step2_time DATETIME NOT NULL,
    planned_step3_stab_end DATETIME NOT NULL,
    planned_step4_map_end DATETIME NOT NULL,
    planned_step5_readout_deadline DATETIME NOT NULL,
    calculated_testo_delay_minutes INT NOT NULL,
    required_recorder_count INT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PLANNED',
    resource_status VARCHAR(50) NOT NULL DEFAULT 'OK',
    shortage_reason VARCHAR(500) NULL,
    suggested_window_start DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pvt_chamber FOREIGN KEY (cooling_chamber_id) REFERENCES cooling_chambers(id),
    CONSTRAINT fk_pvt_procedure_class FOREIGN KEY (procedure_class_config_id) REFERENCES procedure_class_configs(id),
    CONSTRAINT uq_pvt_chamber_type_due UNIQUE (cooling_chamber_id, procedure_type, due_date)
);

CREATE INDEX idx_pvt_status_due ON planned_validation_tasks (status, due_date);
CREATE INDEX idx_pvt_task_number ON planned_validation_tasks (task_number);

CREATE TABLE IF NOT EXISTS planned_validation_tasks_aud (
    id BIGINT NOT NULL,
    rev INT NOT NULL,
    revtype TINYINT,
    task_number VARCHAR(50),
    cooling_chamber_id BIGINT,
    procedure_class_config_id BIGINT,
    procedure_type VARCHAR(50),
    due_date DATE,
    planned_step1_time DATETIME,
    planned_step2_time DATETIME,
    planned_step3_stab_end DATETIME,
    planned_step4_map_end DATETIME,
    planned_step5_readout_deadline DATETIME,
    calculated_testo_delay_minutes INT,
    required_recorder_count INT,
    status VARCHAR(50),
    resource_status VARCHAR(50),
    shortage_reason VARCHAR(500),
    suggested_window_start DATETIME,
    created_at DATETIME,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_pvt_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- ---------------------------------------------------------------------------
-- E. Rezerwacje kanałów rejestratorów (fundament W1/W2/W5/W8)
--    Jeden wiersz = jeden kanał, więc liczba wierszy = liczba punktów pomiarowych.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS planned_task_recorder_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    planned_task_id BIGINT NOT NULL,
    thermo_recorder_id BIGINT NOT NULL,
    channel_number INT NOT NULL DEFAULT 1,
    reserved_from DATETIME NOT NULL,
    reserved_until DATETIME NOT NULL,
    CONSTRAINT fk_ptra_task FOREIGN KEY (planned_task_id) REFERENCES planned_validation_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_ptra_recorder FOREIGN KEY (thermo_recorder_id) REFERENCES thermo_recorders(id),
    CONSTRAINT uq_ptra_task_recorder_channel UNIQUE (planned_task_id, thermo_recorder_id, channel_number)
);

-- Wykrywanie kolizji rezerwacji (W5)
CREATE INDEX idx_ptra_recorder_window ON planned_task_recorder_assignments (thermo_recorder_id, reserved_from, reserved_until);

CREATE TABLE IF NOT EXISTS planned_task_recorder_assignments_aud (
    id BIGINT NOT NULL,
    rev INT NOT NULL,
    revtype TINYINT,
    planned_task_id BIGINT,
    thermo_recorder_id BIGINT,
    channel_number INT,
    reserved_from DATETIME,
    reserved_until DATETIME,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_ptra_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- ---------------------------------------------------------------------------
-- F. Rozdział zegarów: rewalidacja roczna vs mapowanie 5-letnie (BA R2)
--    last_mapping_date pozostaje WYŁĄCZNIE zegarem mapowania — nadpisanie go
--    po rocznej rewalidacji resetowałoby 5-letni cykl.
-- ---------------------------------------------------------------------------
ALTER TABLE cooling_chambers ADD COLUMN last_periodic_revalidation_date DATE NULL;
ALTER TABLE cooling_chambers_aud ADD COLUMN last_periodic_revalidation_date DATE;

-- ---------------------------------------------------------------------------
-- G. Dane startowe: globalne okno pracy i domyślne klasy procedur wg BA §3
-- ---------------------------------------------------------------------------
INSERT INTO operator_shift_configs (user_id, shift_start, shift_end, active)
VALUES (NULL, '06:30:00', '13:30:00', TRUE);

INSERT INTO procedure_class_configs
    (name, procedure_type, step1_prog_minutes, step2_placement_minutes, step3_stab_hours,
     step4_interval_minutes, step4_sample_count, step5_readout_buffer_hours, active)
VALUES
    ('Rewalidacja okresowa — standard', 'PERIODIC_REVALIDATION', 10, 20, 6, 180, 40, 6, TRUE),
    ('Mapowanie GxP 5-letnie — standard', 'MAPPING', 10, 20, 6, 180, 40, 6, TRUE);