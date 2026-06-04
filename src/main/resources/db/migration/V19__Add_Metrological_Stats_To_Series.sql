-- V19: Add Metrological and GxP Statistical Columns to Measurement Series
-- Author: macie - Deepmind coding agent

ALTER TABLE thermo_measurement_series
    ADD COLUMN min_temperature DOUBLE NULL,
    ADD COLUMN max_temperature DOUBLE NULL,
    ADD COLUMN avg_temperature DOUBLE NULL,
    ADD COLUMN median_temperature DOUBLE NULL,
    ADD COLUMN std_deviation DOUBLE NULL,
    ADD COLUMN variance DOUBLE NULL,
    ADD COLUMN cv_percentage DOUBLE NULL,
    ADD COLUMN mkt_temperature DOUBLE NULL,
    ADD COLUMN percentile_5 DOUBLE NULL,
    ADD COLUMN percentile_95 DOUBLE NULL,
    ADD COLUMN total_time_in_range_minutes BIGINT NULL,
    ADD COLUMN total_time_out_of_range_minutes BIGINT NULL,
    ADD COLUMN violation_count INT NULL,
    ADD COLUMN max_violation_duration_minutes BIGINT NULL,
    ADD COLUMN trend_coefficient DOUBLE NULL,
    ADD COLUMN adjusted_trend_coefficient DOUBLE NULL,
    ADD COLUMN spike_count INT NULL,
    ADD COLUMN drift_classification VARCHAR(50) NULL,
    ADD COLUMN expanded_uncertainty DOUBLE NULL;

ALTER TABLE thermo_measurement_series_aud
    ADD COLUMN min_temperature DOUBLE NULL,
    ADD COLUMN max_temperature DOUBLE NULL,
    ADD COLUMN avg_temperature DOUBLE NULL,
    ADD COLUMN median_temperature DOUBLE NULL,
    ADD COLUMN std_deviation DOUBLE NULL,
    ADD COLUMN variance DOUBLE NULL,
    ADD COLUMN cv_percentage DOUBLE NULL,
    ADD COLUMN mkt_temperature DOUBLE NULL,
    ADD COLUMN percentile_5 DOUBLE NULL,
    ADD COLUMN percentile_95 DOUBLE NULL,
    ADD COLUMN total_time_in_range_minutes BIGINT NULL,
    ADD COLUMN total_time_out_of_range_minutes BIGINT NULL,
    ADD COLUMN violation_count INT NULL,
    ADD COLUMN max_violation_duration_minutes BIGINT NULL,
    ADD COLUMN trend_coefficient DOUBLE NULL,
    ADD COLUMN adjusted_trend_coefficient DOUBLE NULL,
    ADD COLUMN spike_count INT NULL,
    ADD COLUMN drift_classification VARCHAR(50) NULL,
    ADD COLUMN expanded_uncertainty DOUBLE NULL;
