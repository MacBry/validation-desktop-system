-- Naprawa liczby kanałów dla modeli Testo 176T4, jeśli zostały źle zaimportowane lub dodane
UPDATE thermo_recorder_models 
SET channel_count = 4 
WHERE LOWER(name) LIKE '%t4%';

-- Zaktualizowanie również tabeli audytowej jeśli istnieje taka potrzeba (niewymagane dla prostego update, ale dla czystości)
-- Envers sam nie wychwyci tego update'u z czystego SQL, ale to tylko poprawka konfiguracyjna.
