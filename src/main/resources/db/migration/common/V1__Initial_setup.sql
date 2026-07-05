-- V1__Initial_setup.sql
-- Inicjalizacja schematu bazy danych dla aplikacji desktopowej

CREATE TABLE test_entity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO test_entity (name) VALUES ('Początkowy wpis testowy');
