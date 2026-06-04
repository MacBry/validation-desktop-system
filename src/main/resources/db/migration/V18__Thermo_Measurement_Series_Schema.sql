-- V18: Thermo Measurement Series and Points Schema
-- Author: macie - Deepmind coding agent

DROP TABLE IF EXISTS thermo_measurement_points_aud;
DROP TABLE IF EXISTS thermo_measurement_points;
DROP TABLE IF EXISTS thermo_measurement_series_aud;
DROP TABLE IF EXISTS thermo_measurement_series;

CREATE TABLE IF NOT EXISTS thermo_measurement_series (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    thermo_recorder_id BIGINT NOT NULL,
    cooling_chamber_id BIGINT NOT NULL,
    battery_level_percent INT NOT NULL,
    logging_interval_minutes INT NOT NULL,
    measurements_count INT NOT NULL,
    programming_time_utc DATETIME NOT NULL,
    start_delay_minutes INT NOT NULL,
    first_measurement_time_utc DATETIME NOT NULL,
    first_measurement_time_local DATETIME NOT NULL,
    imported_at DATETIME NOT NULL,
    imported_by VARCHAR(100) NOT NULL,
    raw_hex_dump LONGTEXT NOT NULL,
    CONSTRAINT fk_series_recorder FOREIGN KEY (thermo_recorder_id) REFERENCES thermo_recorders(id),
    CONSTRAINT fk_series_chamber FOREIGN KEY (cooling_chamber_id) REFERENCES cooling_chambers(id)
);

CREATE TABLE IF NOT EXISTS thermo_measurement_series_aud (
    id BIGINT NOT NULL,
    rev INT NOT NULL,
    revtype TINYINT,
    thermo_recorder_id BIGINT,
    cooling_chamber_id BIGINT,
    battery_level_percent INT,
    logging_interval_minutes INT,
    measurements_count INT,
    programming_time_utc DATETIME,
    start_delay_minutes INT,
    first_measurement_time_utc DATETIME,
    first_measurement_time_local DATETIME,
    imported_at DATETIME,
    imported_by VARCHAR(100),
    raw_hex_dump LONGTEXT,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_series_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);

CREATE TABLE IF NOT EXISTS thermo_measurement_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    series_id BIGINT NOT NULL,
    measurement_index INT NOT NULL,
    timestamp_local DATETIME NOT NULL,
    raw_celsius DOUBLE NOT NULL,
    CONSTRAINT fk_point_series FOREIGN KEY (series_id) REFERENCES thermo_measurement_series(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS thermo_measurement_points_aud (
    id BIGINT NOT NULL,
    rev INT NOT NULL,
    revtype TINYINT,
    series_id BIGINT,
    measurement_index INT,
    timestamp_local DATETIME,
    raw_celsius DOUBLE,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_thermo_point_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
