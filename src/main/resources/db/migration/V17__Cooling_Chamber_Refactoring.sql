-- V17: Cooling Chamber Refactoring
-- Author: macie - Deepmind coding agent

-- 1. Tworzenie tabeli operacyjnej komór chłodniczych
CREATE TABLE IF NOT EXISTS cooling_chambers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cooling_device_id BIGINT NOT NULL,
    chamber_name VARCHAR(100) NOT NULL,
    chamber_type VARCHAR(30) NOT NULL,
    material_type_id BIGINT,
    min_operating_temp DOUBLE,
    max_operating_temp DOUBLE,
    volume_m3 DOUBLE,
    volume_category VARCHAR(10),
    CONSTRAINT fk_chamber_device FOREIGN KEY (cooling_device_id) REFERENCES cooling_devices(id) ON DELETE CASCADE,
    CONSTRAINT fk_chamber_material FOREIGN KEY (material_type_id) REFERENCES material_types(id)
);

-- 2. Tworzenie tabeli audytowej komór chłodniczych (Hibernate Envers)
CREATE TABLE IF NOT EXISTS cooling_chambers_aud (
    id BIGINT NOT NULL,
    rev INTEGER NOT NULL,
    revtype TINYINT,
    cooling_device_id BIGINT,
    chamber_name VARCHAR(100),
    chamber_type VARCHAR(30),
    material_type_id BIGINT,
    min_operating_temp DOUBLE,
    max_operating_temp DOUBLE,
    volume_m3 DOUBLE,
    volume_category VARCHAR(10),
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_chamber_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- 3. Kopiowanie danych operacyjnych ze starej struktury do nowej (id komory odpowiada id urządzenia dla zachowania relacji)
INSERT INTO cooling_chambers (id, cooling_device_id, chamber_name, chamber_type, material_type_id, min_operating_temp, max_operating_temp, volume_m3, volume_category)
SELECT id, id, 'Komora Główna', chamber_type, material_type_id, min_operating_temp, max_operating_temp, volume_m3, volume_category
FROM cooling_devices;

-- 4. Kopiowanie historii audytu w celu zachowania pełnej zgodności z FDA 21 CFR Part 11
INSERT INTO cooling_chambers_aud (id, rev, revtype, cooling_device_id, chamber_name, chamber_type, material_type_id, min_operating_temp, max_operating_temp, volume_m3, volume_category)
SELECT id, rev, revtype, id, 'Komora Główna', chamber_type, material_type_id, min_operating_temp, max_operating_temp, volume_m3, volume_category
FROM cooling_devices_aud;

-- 5. Usunięcie klucza obcego i starych kolumn z tabeli cooling_devices
ALTER TABLE cooling_devices DROP CONSTRAINT fk_cooling_device_material;
ALTER TABLE cooling_devices DROP COLUMN chamber_type;
ALTER TABLE cooling_devices DROP COLUMN material_type_id;
ALTER TABLE cooling_devices DROP COLUMN min_operating_temp;
ALTER TABLE cooling_devices DROP COLUMN max_operating_temp;
ALTER TABLE cooling_devices DROP COLUMN volume_m3;
ALTER TABLE cooling_devices DROP COLUMN volume_category;

-- 6. Usunięcie starych kolumn z tabeli audytowej cooling_devices_aud
ALTER TABLE cooling_devices_aud DROP COLUMN chamber_type;
ALTER TABLE cooling_devices_aud DROP COLUMN material_type_id;
ALTER TABLE cooling_devices_aud DROP COLUMN min_operating_temp;
ALTER TABLE cooling_devices_aud DROP COLUMN max_operating_temp;
ALTER TABLE cooling_devices_aud DROP COLUMN volume_m3;
ALTER TABLE cooling_devices_aud DROP COLUMN volume_category;
