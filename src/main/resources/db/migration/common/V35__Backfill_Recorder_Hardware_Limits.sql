-- V35: Domknięcie zasilenia kartoteki sprzętowej z V34.
--
-- V34 dopasowywała modele wzorcem '%174t%', który wymaga litery po numerze.
-- W rzeczywistej bazie nazwy pochodzą z wolnego pola tekstowego (migracja V24)
-- i bywają zapisane bez oznaczenia wariantu — np. „Testo 174". Takie modele
-- zostały bez zakresu pracy, przez co reguła W4 blokowała planowanie
-- komunikatem „Model ... nie ma w kartotece zakresu pracy".
--
-- Ta migracja uzupełnia dane tam, gdzie ich brak (min_operating_temp_c IS NULL),
-- i NIE nadpisuje wartości ustawionych wcześniej ani wprowadzonych ręcznie.

-- testo 174 — wariant T i H mają tę samą pamięć (16 000), ten sam zakres pracy
-- (-30…+70 °C) i tę samą baterię (2x CR2032, 500 dni @ 15 min / +25 °C), więc
-- uzupełnienie po samym numerze serii jest bezpieczne.
UPDATE thermo_recorder_models SET
    sample_capacity = 16000,
    min_operating_temp_c = -30, max_operating_temp_c = 70,
    battery_type = 'CR2032', battery_replaceable = TRUE,
    battery_life_days = 500, battery_life_ref_cycle_min = 15, battery_life_ref_temp_c = 25,
    battery_shelf_life_months = 24
WHERE min_operating_temp_c IS NULL
  AND REPLACE(LOWER(name), ' ', '') LIKE '%174%';

-- testo 175 — 1 000 000 odczytów, 3x AAA, ok. 3 lata @ 15 min / +25 °C
UPDATE thermo_recorder_models SET
    sample_capacity = 1000000,
    min_operating_temp_c = -35, max_operating_temp_c = 70,
    battery_type = '3xAAA AlMn', battery_replaceable = TRUE,
    battery_life_days = 1095, battery_life_ref_cycle_min = 15, battery_life_ref_temp_c = 25,
    battery_shelf_life_months = 24
WHERE min_operating_temp_c IS NULL
  AND REPLACE(LOWER(name), ' ', '') LIKE '%175%'
  AND REPLACE(LOWER(name), ' ', '') NOT LIKE '%184%';

-- testo 176 — 2 000 000 odczytów, do 8 lat pracy
UPDATE thermo_recorder_models SET
    sample_capacity = 2000000,
    min_operating_temp_c = -40, max_operating_temp_c = 70,
    battery_type = 'AA', battery_replaceable = TRUE,
    battery_life_days = 2920, battery_life_ref_cycle_min = 15, battery_life_ref_temp_c = 25,
    battery_shelf_life_months = 24
WHERE min_operating_temp_c IS NULL
  AND REPLACE(LOWER(name), ' ', '') LIKE '%176%'
  AND REPLACE(LOWER(name), ' ', '') NOT LIKE '%184%';

-- UWAGA: seria 184 celowo NIE dostaje wartości domyślnych po samym numerze.
-- Warianty różnią się zasadniczo — T1 ma 16 000 odczytów i baterię niewymienną
-- na 90 dni, T3 ma 40 000 i baterię wymienną, a T4 jako jedyny pracuje do
-- -80 °C. Zgadywanie tych parametrów wstawiłoby do systemu walidacyjnego dane
-- niezgodne ze sprzętem, więc model nazwany samym „Testo 184" musi zostać
-- uzupełniony ręcznie w kartotece modeli (zakładka Rejestratory → Modele).

-- Kontrola po migracji — modele nadal wymagające uzupełnienia:
--   SELECT name FROM thermo_recorder_models WHERE min_operating_temp_c IS NULL AND active = TRUE;