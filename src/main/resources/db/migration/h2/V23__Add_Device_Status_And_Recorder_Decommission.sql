-- (wariant H2 — wygenerowany przez scripts/h2_split_alter.py:
--  wielokrotne ADD COLUMN rozdzielone na osobne ALTER TABLE)
-- ============================================================
-- V23: Dodanie statusu urządzenia chłodniczego (DeviceStatus)
-- oraz obsługa statusu DECOMMISSIONED dla rejestratorów
-- ============================================================

-- 1. Dodanie kolumny status do tabeli cooling_devices
--    Domyślnie wszystkie istniejące urządzenia otrzymują status ACTIVE
ALTER TABLE cooling_devices
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

-- 2. Aktualizacja tabeli audytowej cooling_devices_aud
--    (Hibernate Envers wymaga zgodności schematu)
ALTER TABLE cooling_devices_aud
    ADD COLUMN status VARCHAR(30) NULL;

-- Uwaga: Tabela thermo_recorders używa VARCHAR(50) z @Enumerated(EnumType.STRING),
-- więc wartość DECOMMISSIONED jest obsługiwana wyłącznie przez warstwę Java
-- i nie wymaga modyfikacji schematu SQL.
