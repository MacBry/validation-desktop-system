-- V12__Organization_Schema.sql

-- 1. Tabela Działów
CREATE TABLE departments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    abbreviation VARCHAR(20) NOT NULL UNIQUE,
    description TEXT,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE departments_aud (
    id BIGINT NOT NULL,
    rev INT NOT NULL,
    revtype TINYINT,
    name VARCHAR(255),
    abbreviation VARCHAR(20),
    description TEXT,
    PRIMARY KEY (id, rev)
);

-- 2. Tabela Pracowni
CREATE TABLE laboratories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    department_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    abbreviation VARCHAR(20) NOT NULL UNIQUE,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lab_dept FOREIGN KEY (department_id) REFERENCES departments(id)
);

CREATE TABLE laboratories_aud (
    id BIGINT NOT NULL,
    rev INT NOT NULL,
    revtype TINYINT,
    department_id BIGINT,
    name VARCHAR(255),
    abbreviation VARCHAR(20),
    PRIMARY KEY (id, rev)
);

-- 3. Rozszerzenie tabeli użytkowników
ALTER TABLE users ADD COLUMN department_id BIGINT;
ALTER TABLE users ADD COLUMN laboratory_id BIGINT;
ALTER TABLE users ADD CONSTRAINT fk_user_dept FOREIGN KEY (department_id) REFERENCES departments(id);
ALTER TABLE users ADD CONSTRAINT fk_user_lab FOREIGN KEY (laboratory_id) REFERENCES laboratories(id);

ALTER TABLE users_aud ADD COLUMN department_id BIGINT;
ALTER TABLE users_aud ADD COLUMN laboratory_id BIGINT;

-- 4. Dodanie nowej roli
INSERT INTO roles (name) VALUES ('ROLE_DEPT_ADMIN');

-- 5. Przykładowe dane (Baseline)
INSERT INTO departments (name, abbreviation) VALUES ('Dział Laboratoryjny', 'DL');
INSERT INTO departments (name, abbreviation) VALUES ('Dział Ekspedycji', 'DE');

INSERT INTO laboratories (department_id, name, abbreviation) VALUES (1, 'Pracownia Immunologii', 'IMM');
INSERT INTO laboratories (department_id, name, abbreviation) VALUES (1, 'Pracownia Wirusologii', 'WIR');
INSERT INTO laboratories (department_id, name, abbreviation) VALUES (2, 'Pracownia Wydawania', 'EXP');
