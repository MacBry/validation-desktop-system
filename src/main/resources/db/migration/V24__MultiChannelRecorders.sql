-- Usunięcie tabeli z nieudanej częściowej migracji
DROP TABLE IF EXISTS thermo_recorder_models;

-- Tabela modeli rejestratorów
CREATE TABLE thermo_recorder_models (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    channel_count INT NOT NULL DEFAULT 1,
    default_resolution DECIMAL(4,3) NOT NULL DEFAULT 0.100,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Tabela logów (envers) dla modeli rejestratorów
CREATE TABLE thermo_recorder_models_aud (
    id BIGINT NOT NULL,
    REV INT NOT NULL,
    REVTYPE TINYINT,
    name VARCHAR(100),
    channel_count INT,
    default_resolution DECIMAL(4,3),
    active BOOLEAN,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_thermo_recorder_models_aud_rev FOREIGN KEY (REV) REFERENCES revinfo (rev)
);

-- Migracja modeli z tabeli thermo_recorders
INSERT INTO thermo_recorder_models (name, channel_count, default_resolution)
SELECT DISTINCT model, 
       CASE WHEN LOWER(model) LIKE '%t4%' THEN 4 ELSE 1 END,
       CASE WHEN LOWER(model) LIKE '%testo%' THEN 0.100 ELSE 0.010 END
FROM thermo_recorders
WHERE model IS NOT NULL AND model != '';

-- Dodanie klucza obcego model_id do thermo_recorders
ALTER TABLE thermo_recorders ADD COLUMN model_id BIGINT;
ALTER TABLE thermo_recorders_aud ADD COLUMN model_id BIGINT;

-- Zaktualizowanie model_id na podstawie modelu (jako string)
UPDATE thermo_recorders tr
JOIN thermo_recorder_models trm ON tr.model = trm.name
SET tr.model_id = trm.id;

UPDATE thermo_recorders_aud tr
JOIN thermo_recorder_models trm ON tr.model = trm.name
SET tr.model_id = trm.id;

-- Wymuszenie NOT NULL dla nowej kolumny
ALTER TABLE thermo_recorders MODIFY COLUMN model_id BIGINT NOT NULL;
ALTER TABLE thermo_recorders ADD CONSTRAINT fk_thermo_recorder_model FOREIGN KEY (model_id) REFERENCES thermo_recorder_models(id);

-- Usunięcie starej kolumny model
ALTER TABLE thermo_recorders DROP COLUMN model;
ALTER TABLE thermo_recorders_aud DROP COLUMN model;

-- Dodanie pola channel_number do calibrations
ALTER TABLE calibrations ADD COLUMN channel_number INT NOT NULL DEFAULT 1;

-- Dodanie pola channel_number do thermo_measurement_series
ALTER TABLE thermo_measurement_series ADD COLUMN channel_number INT NOT NULL DEFAULT 1;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN channel_number INT;
