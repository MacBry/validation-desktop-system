-- V36: Stan baterii mierzony w DNIACH, a nie w procentach.
--
-- KONTEKST — dlaczego ta migracja w ogóle istnieje
--
-- Do 2026-08-05 system czytał "stan baterii" z bajtu 20 payloadu ramki ab31
-- kołyski Testo. Zrzuty USB dwóch egzemplarzy 174 T, zestawione ze wskazaniami
-- oryginalnego oprogramowania producenta, wykazały że ten bajt NIE JEST stanem
-- baterii — to młodszy bajt GÓRNEGO PROGU ALARMOWEGO temperatury.
--
--   próg  8,0 °C  =  80 = 0x0050  ->  młodszy bajt 0x50 = 80  ->  "80 % baterii"
--   próg -20,0 °C = -200 = 0xFF38  ->  młodszy bajt 0x38 = 56  ->  "56 % baterii"
--
-- Trzy rejestratory o rzeczywistym stanie 388, 42 i 40 dni raportowały identyczne
-- "80 %", bo wszystkie miały skonfigurowany zakres 2…8 °C. Reguła W4c liczyła
-- z tej liczby budżet energii: rejestrator z 42 dniami życia był widziany jako
-- 80 % z 500 dni katalogowych, czyli 400 dni referencyjnych — zawyżenie ~10×
-- w kierunku optymistycznym (planer dopuszczał sprzęt, któremu bateria padnie
-- w trakcie mapowania).
--
-- Prawdziwy stan baterii ma własną komendę (ab010a), której nigdy nie wysyłaliśmy,
-- i jest podawany przez urządzenie WPROST W DNIACH — bez przeliczania z napięcia
-- i bez odnoszenia do pojemności katalogowej. Stąd zmiana jednostki modelu:
-- reguła W4c i tak porównuje czas z czasem, więc dni są miarą naturalną,
-- a procent był wielkością wtórną i — jak się okazało — nigdy niemierzoną.
--
-- Szczegóły: TESTO_USB_ANALYSIS.md §2.3b, REVALIDATION_PLANNER_W4_SUPPLEMENT.md §2.3.

-- ---------------------------------------------------------------------------
-- 1. Egzemplarz rejestratora: pozostały czas pracy baterii [dni]
-- ---------------------------------------------------------------------------
ALTER TABLE thermo_recorders ADD COLUMN battery_remaining_days INT;
ALTER TABLE thermo_recorders_aud ADD COLUMN battery_remaining_days INT;

-- ---------------------------------------------------------------------------
-- 2. Seria pomiarowa: stan baterii w chwili odczytu [dni]
-- ---------------------------------------------------------------------------
ALTER TABLE thermo_measurement_series ADD COLUMN battery_remaining_days INT;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN battery_remaining_days INT;

-- ---------------------------------------------------------------------------
-- 3. Wyzerowanie fałszywego BIEŻĄCEGO wskaźnika na rejestratorach
-- ---------------------------------------------------------------------------
-- last_battery_level_percent na egzemplarzu nie jest zapisem historycznym —
-- to bieżący stan, z którego planer liczy budżet energii TERAZ. Pozostawienie
-- tam progu alarmowego oznaczałoby, że reguła W4c dalej pracuje na fikcji,
-- mimo poprawionego parsera. Dlatego wartości są czyszczone, a nie zachowywane.
--
-- Skutek zamierzony: do czasu ponownego zczytania rejestratorów w stacji USB
-- reguła W4c zgłasza brak danych i blokuje planowanie. To jest właściwe
-- zachowanie — brak danych jest uczciwy, fikcyjna liczba nie.
UPDATE thermo_recorders SET last_battery_level_percent = NULL, last_battery_read_at = NULL;

-- Kolumny last_battery_level_percent NIE są usuwane: zostają w schemacie wraz
-- z historią w thermo_recorders_aud jako ślad audytowy tego, co system zapisywał
-- przed korektą protokołu.

-- ---------------------------------------------------------------------------
-- 4. Dane historyczne serii pomiarowych — świadomie NIETKNIĘTE
-- ---------------------------------------------------------------------------
-- thermo_measurement_series.battery_level_percent zachowuje dotychczasowe
-- wartości. Są one niewiarygodne (to progi alarmowe), ale stanowią zapis tego,
-- co system odczytał w chwili badania, i jako część audit trailu GxP nie
-- podlegają modyfikacji.
--
-- RYZYKO REZYDUALNE, przyjęte świadomie: ponowne wygenerowanie raportu
-- z archiwalnego badania wydrukuje dawną wartość (np. "80%") jako zmierzony
-- stan baterii. Serie zapisane po tej migracji mają battery_level_percent = -1
-- (N/D) i battery_remaining_days z ramki ab010a, więc raport pokazuje "N/D"
-- albo liczbę dni. Odróżnienie jednych od drugich: battery_remaining_days
-- IS NOT NULL oznacza odczyt po korekcie protokołu.