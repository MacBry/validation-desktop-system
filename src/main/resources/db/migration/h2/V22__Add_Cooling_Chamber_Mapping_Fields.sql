-- (wariant H2 — wygenerowany przez scripts/h2_split_alter.py:
--  wielokrotne ADD COLUMN rozdzielone na osobne ALTER TABLE)
-- V22: Add Cooling Chamber Mapping Fields
-- Author: macie - Deepmind coding agent

ALTER TABLE cooling_chambers ADD COLUMN last_mapping_date DATE;
ALTER TABLE cooling_chambers ADD COLUMN hotspot_position VARCHAR(50);
ALTER TABLE cooling_chambers ADD COLUMN coldspot_position VARCHAR(50);

ALTER TABLE cooling_chambers_aud ADD COLUMN last_mapping_date DATE;
ALTER TABLE cooling_chambers_aud ADD COLUMN hotspot_position VARCHAR(50);
ALTER TABLE cooling_chambers_aud ADD COLUMN coldspot_position VARCHAR(50);

ALTER TABLE thermo_measurement_series ADD COLUMN procedure_type VARCHAR(50) DEFAULT 'PERIODIC_REVALIDATION';
ALTER TABLE thermo_measurement_series_aud ADD COLUMN procedure_type VARCHAR(50);

ALTER TABLE material_types ADD COLUMN requires_mapping BOOLEAN DEFAULT FALSE;
ALTER TABLE material_types_aud ADD COLUMN requires_mapping BOOLEAN;

-- Set requires_mapping = TRUE for blood- and plasma-related materials
UPDATE material_types 
SET requires_mapping = TRUE 
WHERE LOWER(name) LIKE '%krew%' OR LOWER(name) LIKE '%osocze%';
