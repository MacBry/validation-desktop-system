-- ============================================================
-- V28: Warstwa Interpretacji Reżimów Pracy (DP-001 Faza 1)
-- Tabela segmentów pomiarowych wykrywanych algorytmicznie
-- lub adnotowanych przez operatora (human-in-the-loop).
-- ============================================================

CREATE TABLE measurement_segments (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    series_id       BIGINT          NOT NULL,
    from_timestamp  DATETIME(3)     NOT NULL COMMENT 'Początek segmentu (czas lokalny hosta)',
    to_timestamp    DATETIME(3)     NOT NULL COMMENT 'Koniec segmentu (czas lokalny hosta)',
    type            VARCHAR(30)     NOT NULL COMMENT 'SegmentType: EQUILIBRATION|STEADY_STATE|DEFROST|DOOR_EVENT|SETPOINT_CHANGE|EXCURSION|NORMAL_USE',
    confidence      DOUBLE          NULL     COMMENT 'Pewność detekcji algorytmicznej [0.0–1.0]; NULL dla DetectionSource=OPERATOR',
    source          VARCHAR(20)     NOT NULL DEFAULT 'ALGORITHM' COMMENT 'DetectionSource: ALGORITHM|OPERATOR',
    note            VARCHAR(500)    NULL     COMMENT 'Notatka operatora lub hipoteza przyczynowa',
    confirmed_by    VARCHAR(100)    NULL     COMMENT 'Login operatora, który zatwierdził segment (human-in-the-loop)',
    confirmed_at    DATETIME(3)     NULL     COMMENT 'Timestamp zatwierdzenia',
    accepted        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '0 = odrzucony przez operatora, wykluczony z obliczeń',
    created_date    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_seg_series
        FOREIGN KEY (series_id) REFERENCES thermo_measurement_series(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_seg_series  (series_id),
    INDEX idx_seg_type    (type),
    INDEX idx_seg_time    (from_timestamp, to_timestamp),
    INDEX idx_seg_accepted (accepted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Segmenty reżimów pracy wykryte przez algorytm OLS+CUSUM lub adnotowane przez operatora (DP-001)';

-- Tabela audytowa Hibernate Envers
CREATE TABLE measurement_segments_aud (
    id              BIGINT      NOT NULL,
    rev             INT         NOT NULL,
    revtype         TINYINT     NULL,
    series_id       BIGINT      NULL,
    from_timestamp  DATETIME(3) NULL,
    to_timestamp    DATETIME(3) NULL,
    type            VARCHAR(30) NULL,
    confidence      DOUBLE      NULL,
    source          VARCHAR(20) NULL,
    note            VARCHAR(500) NULL,
    confirmed_by    VARCHAR(100) NULL,
    confirmed_at    DATETIME(3) NULL,
    accepted        TINYINT(1)  NULL,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_seg_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Tabela audytowa Envers dla measurement_segments';
