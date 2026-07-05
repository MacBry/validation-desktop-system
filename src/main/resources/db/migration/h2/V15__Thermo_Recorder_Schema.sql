-- V15: Thermo Recorder and Calibration Schema (wariant H2)
-- Roznica vs MySQL: DATEDIFF w H2 ma sygnature (unit, start, end)
-- Author: macie - Deepmind coding agent

CREATE TABLE IF NOT EXISTS thermo_recorders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    serial_number VARCHAR(50) NOT NULL UNIQUE,
    model VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    resolution DECIMAL(4, 3),
    department_id BIGINT NOT NULL,
    laboratory_id BIGINT,
    CONSTRAINT fk_recorder_dept FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_recorder_lab FOREIGN KEY (laboratory_id) REFERENCES laboratories(id)
);

CREATE TABLE IF NOT EXISTS thermo_recorders_aud (
    id BIGINT NOT NULL,
    rev INTEGER NOT NULL,
    revtype TINYINT,
    serial_number VARCHAR(50),
    model VARCHAR(100),
    status VARCHAR(50),
    resolution DECIMAL(4, 3),
    department_id BIGINT,
    laboratory_id BIGINT,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_recorder_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE IF NOT EXISTS calibrations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    thermo_recorder_id BIGINT NOT NULL,
    calibration_date DATE NOT NULL,
    certificate_number VARCHAR(100) NOT NULL,
    valid_until DATE NOT NULL,
    certificate_file_path VARCHAR(500),
    CONSTRAINT fk_calibration_recorder FOREIGN KEY (thermo_recorder_id) REFERENCES thermo_recorders(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS calibrations_aud (
    id BIGINT NOT NULL,
    rev INTEGER NOT NULL,
    revtype TINYINT,
    calibration_date DATE,
    certificate_number VARCHAR(100),
    valid_until DATE,
    certificate_file_path VARCHAR(500),
    thermo_recorder_id BIGINT,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_calibration_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE IF NOT EXISTS calibration_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    calibration_id BIGINT NOT NULL,
    temperature_value DECIMAL(10, 4) NOT NULL,
    systematic_error DECIMAL(10, 4) NOT NULL,
    uncertainty DECIMAL(10, 4) NOT NULL,
    CONSTRAINT fk_point_calibration FOREIGN KEY (calibration_id) REFERENCES calibrations(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS calibration_points_aud (
    id BIGINT NOT NULL,
    rev INTEGER NOT NULL,
    revtype TINYINT,
    temperature_value DECIMAL(10, 4),
    systematic_error DECIMAL(10, 4),
    uncertainty DECIMAL(10, 4),
    calibration_id BIGINT,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_point_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

-- Widok: Status wzorcowania rejestratorów
CREATE OR REPLACE VIEW v_thermo_recorder_status AS
SELECT 
    tr.id as recorder_id,
    tr.serial_number,
    tr.model,
    tr.status as operational_status,
    c.certificate_number as latest_cert,
    c.valid_until,
    DATEDIFF('DAY', CURRENT_DATE, c.valid_until) as days_to_expiry,
    CASE 
        WHEN c.valid_until IS NULL THEN 'NO_CALIBRATION'
        WHEN c.valid_until < CURRENT_DATE THEN 'EXPIRED'
        WHEN DATEDIFF('DAY', CURRENT_DATE, c.valid_until) <= 30 THEN 'EXPIRING_SOON'
        ELSE 'VALID'
    END as calibration_status
FROM thermo_recorders tr
LEFT JOIN (
    SELECT c1.* FROM calibrations c1
    INNER JOIN (
        SELECT thermo_recorder_id, MAX(calibration_date) as max_date
        FROM calibrations
        GROUP BY thermo_recorder_id
    ) c2 ON c1.thermo_recorder_id = c2.thermo_recorder_id AND c1.calibration_date = c2.max_date
) c ON tr.id = c.thermo_recorder_id;
