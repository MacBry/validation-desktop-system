-- IMPL-EXC002 §2.4: Deklaracja źródła nawiewu/ewaporatora per komora
-- dla klasyfikacji ekskursji na podstawie wektora propagacji ciepła.

ALTER TABLE cooling_chambers ADD COLUMN airflow_source_preset VARCHAR(30) NOT NULL DEFAULT 'REAR_WALL';
ALTER TABLE cooling_chambers ADD COLUMN custom_airflow_positions VARCHAR(500) DEFAULT NULL;

-- Tabela audytowa (Hibernate Envers)
ALTER TABLE cooling_chambers_aud ADD COLUMN airflow_source_preset VARCHAR(30);
ALTER TABLE cooling_chambers_aud ADD COLUMN custom_airflow_positions VARCHAR(500);
