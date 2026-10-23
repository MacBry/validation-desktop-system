-- V34: Dane sprzętowe rejestratorów wymagane przez regułę W4 (limity sprzętowe).
--
-- W4 była w BA v5.0 oznaczona jako *deferred*, bo encje ThermoRecorder i
-- ThermoRecorderModel nie przechowywały pojemności pamięci, zakresu pracy ani
-- stanu baterii. Ta migracja dostarcza dane wejściowe reguły.
--
-- Model danych baterii wynika wprost z kart katalogowych Testo: producent podaje
-- żywotność jako LICZBĘ DNI przy referencyjnym cyklu pomiarowym i temperaturze
-- (np. testo 174 T: 500 dni @ 15 min / +25 °C; testo 184 T4: 100 dni @ 15 min /
-- -80 °C), a nie jako procentowy współczynnik degradacji. Dlatego zapisujemy
-- trójkę (battery_life_days, battery_life_ref_cycle_min, battery_life_ref_temp_c),
-- a nie sam typ ogniwa — patrz REVALIDATION_PLANNER_W4_SUPPLEMENT.md §2-§3.
--
-- SQL jest celowo przenośny (osobne ALTER-y na kolumnę, bez DOUBLE PRECISION
-- i bez dialektowych funkcji), dzięki czemu jedna migracja obsługuje H2 i MySQL.
-- Tabele docelowe powstały w wariantach vendorowych V24__MultiChannelRecorders.sql,
-- ale w obu mają identyczny kształt.

-- ---------------------------------------------------------------------------
-- 1. Model rejestratora: pamięć, zakres pracy, dane katalogowe baterii
-- ---------------------------------------------------------------------------
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

-- Envers: encja jest @Audited, więc tabela rewizji musi mieć te same kolumny
-- (bez ograniczeń NOT NULL — rewizja zapisuje stan sprzed dodania pola).
ALTER TABLE thermo_recorder_models_aud ADD COLUMN sample_capacity INT;
ALTER TABLE thermo_recorder_models_aud ADD COLUMN min_operating_temp_c DOUBLE;
ALTER TABLE thermo_recorder_models_aud ADD COLUMN max_operating_temp_c DOUBLE;
ALTER TABLE thermo_recorder_models_aud ADD COLUMN battery_type VARCHAR(50);
ALTER TABLE thermo_recorder_models_aud ADD COLUMN battery_replaceable BOOLEAN;
ALTER TABLE thermo_recorder_models_aud ADD COLUMN battery_life_days INT;
ALTER TABLE thermo_recorder_models_aud ADD COLUMN battery_life_ref_cycle_min INT;
ALTER TABLE thermo_recorder_models_aud ADD COLUMN battery_life_ref_temp_c DOUBLE;
ALTER TABLE thermo_recorder_models_aud ADD COLUMN operating_duration_days INT;
ALTER TABLE thermo_recorder_models_aud ADD COLUMN battery_shelf_life_months INT;

-- ---------------------------------------------------------------------------
-- 2. Egzemplarz rejestratora: ostatni znany stan baterii
-- ---------------------------------------------------------------------------
ALTER TABLE thermo_recorders ADD COLUMN last_battery_level_percent INT;
ALTER TABLE thermo_recorders ADD COLUMN last_battery_read_at TIMESTAMP;
ALTER TABLE thermo_recorders ADD COLUMN battery_replacement_date DATE;
ALTER TABLE thermo_recorders ADD COLUMN first_activation_date DATE;

ALTER TABLE thermo_recorders_aud ADD COLUMN last_battery_level_percent INT;
ALTER TABLE thermo_recorders_aud ADD COLUMN last_battery_read_at TIMESTAMP;
ALTER TABLE thermo_recorders_aud ADD COLUMN battery_replacement_date DATE;
ALTER TABLE thermo_recorders_aud ADD COLUMN first_activation_date DATE;

-- ---------------------------------------------------------------------------
-- 3. Zasilenie danymi katalogowymi producenta
-- ---------------------------------------------------------------------------
-- Nazwy modeli pochodzą z migracji V24 (przeniesione z wolnego pola tekstowego
-- thermo_recorders.model), więc dopasowanie musi znosić spacje i wielkość liter:
-- „testo 174 T", „Testo174T" i „TESTO 174 T" to ten sam model.
--
-- Kolejność ma znaczenie: warianty 184 są dopasowywane przed ogólnymi 175/176,
-- żeby nazwa zawierająca oba oznaczenia nie została nadpisana wartością serii.
--
-- UWAGA WALIDACYJNA: modele spoza tej listy zostają z domyślną pojemnością
-- 16 000 i pustym zakresem pracy. Reguła W4 traktuje brak min_operating_temp_c
-- jako „dane niepotwierdzone" i wymaga uzupełnienia kartoteki, nie przepuszcza
-- takiego rejestratora po cichu. Kontrola po migracji:
--   SELECT name FROM thermo_recorder_models WHERE min_operating_temp_c IS NULL AND active = TRUE;

-- testo 174 T — 16 000 odczytów, 2x CR2032, 500 dni @ 15 min / +25 °C, -30…+70 °C
UPDATE thermo_recorder_models SET
    sample_capacity = 16000,
    min_operating_temp_c = -30, max_operating_temp_c = 70,
    battery_type = 'CR2032', battery_replaceable = TRUE,
    battery_life_days = 500, battery_life_ref_cycle_min = 15, battery_life_ref_temp_c = 25,
    battery_shelf_life_months = 24
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%174t%';

-- testo 184 T1 — 16 000 odczytów, bateria niewymienna, limit pracy 90 dni
UPDATE thermo_recorder_models SET
    sample_capacity = 16000,
    min_operating_temp_c = -35, max_operating_temp_c = 70,
    battery_replaceable = FALSE, operating_duration_days = 90
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%184t1%';

-- testo 184 T2 — 40 000 odczytów, bateria niewymienna, limit pracy 150 dni
UPDATE thermo_recorder_models SET
    sample_capacity = 40000,
    min_operating_temp_c = -35, max_operating_temp_c = 70,
    battery_replaceable = FALSE, operating_duration_days = 150
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%184t2%';

-- testo 184 T3 — 40 000 odczytów, CR2450 wymienna, 500 dni @ 15 min / +25 °C
UPDATE thermo_recorder_models SET
    sample_capacity = 40000,
    min_operating_temp_c = -35, max_operating_temp_c = 70,
    battery_type = 'CR2450', battery_replaceable = TRUE,
    battery_life_days = 500, battery_life_ref_cycle_min = 15, battery_life_ref_temp_c = 25,
    battery_shelf_life_months = 24
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%184t3%';

-- testo 184 T4 — jedyny model do -80 °C; ER2450T (Li-SOCl2), 100 dni @ 15 min / -80 °C
UPDATE thermo_recorder_models SET
    sample_capacity = 40000,
    min_operating_temp_c = -80, max_operating_temp_c = 70,
    battery_type = 'ER2450T', battery_replaceable = TRUE,
    battery_life_days = 100, battery_life_ref_cycle_min = 15, battery_life_ref_temp_c = -80,
    battery_shelf_life_months = 24
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%184t4%';

-- testo 175 — 1 000 000 odczytów (NIE 2 mln, to wartość serii 176), 3x AAA, ok. 3 lata
UPDATE thermo_recorder_models SET
    sample_capacity = 1000000,
    min_operating_temp_c = -35, max_operating_temp_c = 70,
    battery_type = '3xAAA AlMn', battery_replaceable = TRUE,
    battery_life_days = 1095, battery_life_ref_cycle_min = 15, battery_life_ref_temp_c = 25,
    battery_shelf_life_months = 24
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%175%'
  AND REPLACE(LOWER(name), ' ', '') NOT LIKE '%184%';

-- testo 176 — 2 000 000 odczytów, do 8 lat pracy
UPDATE thermo_recorder_models SET
    sample_capacity = 2000000,
    min_operating_temp_c = -40, max_operating_temp_c = 70,
    battery_type = 'AA', battery_replaceable = TRUE,
    battery_life_days = 2920, battery_life_ref_cycle_min = 15, battery_life_ref_temp_c = 25,
    battery_shelf_life_months = 24
WHERE REPLACE(LOWER(name), ' ', '') LIKE '%176%'
  AND REPLACE(LOWER(name), ' ', '') NOT LIKE '%184%';