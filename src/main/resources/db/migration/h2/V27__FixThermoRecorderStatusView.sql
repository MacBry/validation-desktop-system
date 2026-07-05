-- (wariant H2: DATEDIFF z jednostka) Naprawa widoku v_thermo_recorder_status po usunięciu kolumny model z tabeli thermo_recorders
CREATE OR REPLACE VIEW v_thermo_recorder_status AS
SELECT 
    tr.id as recorder_id,
    tr.serial_number,
    trm.name as model,
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
JOIN thermo_recorder_models trm ON tr.model_id = trm.id
LEFT JOIN (
    SELECT c1.* FROM calibrations c1
    INNER JOIN (
        SELECT thermo_recorder_id, MAX(calibration_date) as max_date
        FROM calibrations
        GROUP BY thermo_recorder_id
    ) c2 ON c1.thermo_recorder_id = c2.thermo_recorder_id AND c1.calibration_date = c2.max_date
) c ON tr.id = c.thermo_recorder_id;
