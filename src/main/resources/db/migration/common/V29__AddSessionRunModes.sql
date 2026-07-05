-- ============================================================
-- V29: Tryb runu (RunMode) powiązany z serią pomiarową
-- Operator deklaruje tryb przed uruchomieniem sesji.
-- Wpływa na politykę werdyktu i prezentację raportu (DP-001).
-- ============================================================

CREATE TABLE session_run_modes (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    series_id    BIGINT      NOT NULL UNIQUE COMMENT 'Powiązanie 1:1 z serią pomiarową',
    run_mode     VARCHAR(20) NOT NULL DEFAULT 'CHARACTERIZATION'
                             COMMENT 'RunMode: QUALIFICATION|CHARACTERIZATION|MONITORING',
    declared_by  VARCHAR(100) NULL    COMMENT 'Login operatora, który zadeklarował tryb',
    declared_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_runmode_series
        FOREIGN KEY (series_id) REFERENCES thermo_measurement_series(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_runmode_series (series_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Tryb runu (kwalifikacja/charakteryzacja/monitoring) deklarowany przez operatora (DP-001)';

-- Tabela audytowa Hibernate Envers
CREATE TABLE session_run_modes_aud (
    id          BIGINT      NOT NULL,
    rev         INT         NOT NULL,
    revtype     TINYINT     NULL,
    series_id   BIGINT      NULL,
    run_mode    VARCHAR(20) NULL,
    declared_by VARCHAR(100) NULL,
    declared_at DATETIME(3) NULL,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_runmode_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Tabela audytowa Envers dla session_run_modes';
