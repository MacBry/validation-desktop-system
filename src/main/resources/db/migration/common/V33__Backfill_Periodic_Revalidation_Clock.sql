-- V33: Przeniesienie dotychczasowej daty rewalidacji na własny zegar.
--
-- Do V32 termin corocznej rewalidacji liczony był z last_mapping_date
-- (GxpNotificationService: lastMappingDate + 12 miesięcy). Po rozdzieleniu
-- zegarów (BA R2) nowe pole last_periodic_revalidation_date jest puste dla
-- wszystkich istniejących komór, co bez tego przeniesienia oznaczałoby
-- zgłoszenie całego parku urządzeń jako „BRAK REWALIDACJI OKRESOWEJ".
--
-- Data mapowania jest najlepszym dostępnym przybliżeniem: w poprzednim modelu
-- pełniła dokładnie tę rolę. Od tej migracji oba zegary biegną niezależnie
-- i mapowanie nie może już nadpisywać rewalidacji.
--
-- Uzupełniane są wyłącznie wiersze puste — migracja jest idempotentna
-- i nie nadpisze dat wprowadzonych po V32.

UPDATE cooling_chambers
SET last_periodic_revalidation_date = last_mapping_date
WHERE last_periodic_revalidation_date IS NULL
  AND last_mapping_date IS NOT NULL;