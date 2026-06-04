-- V20: Add Revalidation Group ID and Grid Position columns to Thermo Measurement Series
-- Author: macie - Deepmind coding agent

ALTER TABLE thermo_measurement_series
    ADD COLUMN revalidation_group_id VARCHAR(50) NULL,
    ADD COLUMN grid_position VARCHAR(50) NULL;

ALTER TABLE thermo_measurement_series_aud
    ADD COLUMN revalidation_group_id VARCHAR(50) NULL,
    ADD COLUMN grid_position VARCHAR(50) NULL;
