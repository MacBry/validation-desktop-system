-- (wariant H2 — wygenerowany przez scripts/h2_split_alter.py:
--  wielokrotne ADD COLUMN rozdzielone na osobne ALTER TABLE)
-- V19: Add Metrological and GxP Statistical Columns to Measurement Series
-- Author: macie - Deepmind coding agent

ALTER TABLE thermo_measurement_series ADD COLUMN min_temperature DOUBLE NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN max_temperature DOUBLE NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN avg_temperature DOUBLE NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN median_temperature DOUBLE NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN std_deviation DOUBLE NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN variance DOUBLE NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN cv_percentage DOUBLE NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN mkt_temperature DOUBLE NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN percentile_5 DOUBLE NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN percentile_95 DOUBLE NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN total_time_in_range_minutes BIGINT NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN total_time_out_of_range_minutes BIGINT NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN violation_count INT NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN max_violation_duration_minutes BIGINT NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN trend_coefficient DOUBLE NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN adjusted_trend_coefficient DOUBLE NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN spike_count INT NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN drift_classification VARCHAR(50) NULL;
ALTER TABLE thermo_measurement_series ADD COLUMN expanded_uncertainty DOUBLE NULL;

ALTER TABLE thermo_measurement_series_aud ADD COLUMN min_temperature DOUBLE NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN max_temperature DOUBLE NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN avg_temperature DOUBLE NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN median_temperature DOUBLE NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN std_deviation DOUBLE NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN variance DOUBLE NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN cv_percentage DOUBLE NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN mkt_temperature DOUBLE NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN percentile_5 DOUBLE NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN percentile_95 DOUBLE NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN total_time_in_range_minutes BIGINT NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN total_time_out_of_range_minutes BIGINT NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN violation_count INT NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN max_violation_duration_minutes BIGINT NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN trend_coefficient DOUBLE NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN adjusted_trend_coefficient DOUBLE NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN spike_count INT NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN drift_classification VARCHAR(50) NULL;
ALTER TABLE thermo_measurement_series_aud ADD COLUMN expanded_uncertainty DOUBLE NULL;
