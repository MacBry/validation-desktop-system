-- Dodanie brakującej kolumny channel_number do tabeli audytowej calibrations_aud
ALTER TABLE calibrations_aud ADD COLUMN channel_number INT;
