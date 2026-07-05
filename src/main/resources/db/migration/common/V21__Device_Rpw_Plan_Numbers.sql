-- V21: Device Rpw Plan Numbers
-- Author: macie - Deepmind coding agent

CREATE TABLE IF NOT EXISTS validation_plan_numbers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    `year` INT NOT NULL,
    plan_number INT NOT NULL,
    cooling_device_id BIGINT NOT NULL,
    CONSTRAINT fk_vpn_device FOREIGN KEY (cooling_device_id) REFERENCES cooling_devices(id) ON DELETE CASCADE,
    CONSTRAINT unique_plan_year UNIQUE (cooling_device_id, `year`)
);

CREATE TABLE IF NOT EXISTS validation_plan_numbers_aud (
    id BIGINT NOT NULL,
    rev INTEGER NOT NULL,
    revtype TINYINT,
    `year` INT,
    plan_number INT,
    cooling_device_id BIGINT,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_vpn_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
