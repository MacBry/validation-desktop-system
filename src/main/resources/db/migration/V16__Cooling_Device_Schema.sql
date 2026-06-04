-- V16: Cooling Device and Material Type Schema
-- Author: macie - Deepmind coding agent

CREATE TABLE IF NOT EXISTS material_types (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    min_storage_temp DOUBLE,
    max_storage_temp DOUBLE,
    activation_energy DECIMAL(10, 4),
    standard_source VARCHAR(255),
    application VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS material_types_aud (
    id BIGINT NOT NULL,
    rev INTEGER NOT NULL,
    revtype TINYINT,
    name VARCHAR(100),
    description VARCHAR(500),
    min_storage_temp DOUBLE,
    max_storage_temp DOUBLE,
    activation_energy DECIMAL(10, 4),
    standard_source VARCHAR(255),
    application VARCHAR(255),
    active BOOLEAN,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_material_type_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE IF NOT EXISTS cooling_devices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventory_number VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    department_id BIGINT NOT NULL,
    laboratory_id BIGINT,
    chamber_type VARCHAR(30) NOT NULL,
    material_type_id BIGINT,
    min_operating_temp DOUBLE,
    max_operating_temp DOUBLE,
    volume_m3 DOUBLE,
    volume_category VARCHAR(10),
    CONSTRAINT fk_cooling_device_dept FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_cooling_device_lab FOREIGN KEY (laboratory_id) REFERENCES laboratories(id),
    CONSTRAINT fk_cooling_device_material FOREIGN KEY (material_type_id) REFERENCES material_types(id)
);

CREATE TABLE IF NOT EXISTS cooling_devices_aud (
    id BIGINT NOT NULL,
    rev INTEGER NOT NULL,
    revtype TINYINT,
    inventory_number VARCHAR(50),
    name VARCHAR(200),
    department_id BIGINT,
    laboratory_id BIGINT,
    chamber_type VARCHAR(30),
    material_type_id BIGINT,
    min_operating_temp DOUBLE,
    max_operating_temp DOUBLE,
    volume_m3 DOUBLE,
    volume_category VARCHAR(10),
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_cooling_device_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- Zasilenie początkowe słownika typów materiałów (GMP Seed Data)
INSERT INTO material_types (name, description, min_storage_temp, max_storage_temp, activation_energy, standard_source, application, active)
VALUES 
('Szczepionki (2-8°C)', 'Standardowe warunki przechowywania większości szczepionek i preparatów biologicznych.', 2.0, 8.0, 83.1440, 'Farmakopea Polska / WHO', 'Szczepionki i bio-produkty', TRUE),
('Osocze świeżo mrożone (<-20°C)', 'Mrożone składniki krwi i osocze do celów leczniczych.', -40.0, -20.0, 120.5000, 'Zalecenia RCKiK', 'Składniki krwi', TRUE),
('Krew pełna i KKCz (2-6°C)', 'Koncentrat Krwinek Czerwonych i krew pełna do transfuzji.', 2.0, 6.0, 75.0000, 'Dyrektywy Unijne krew', 'Krwiolecznictwo', TRUE),
('Surowce farmaceutyczne (15-25°C)', 'Przechowywanie w temperaturze pokojowej kontrolowanej.', 15.0, 25.0, 50.0000, 'Wytyczne DPD (Good Distribution Practice)', 'Magazynowanie surowców', TRUE),
('Inkubacja mikrobiologiczna (30-37°C)', 'Środowisko hodowli pożywek i inkubacji prób w laboratoriach kontroli jakości.', 30.0, 37.0, 65.4000, 'ISO 11133 / GMP', 'Laboratorium Kontroli Jakości', TRUE);
