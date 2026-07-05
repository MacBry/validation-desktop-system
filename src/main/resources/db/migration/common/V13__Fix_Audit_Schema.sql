-- V13__Fix_Audit_Schema.sql

-- Dodanie brakujących kolumn do tabel audytowych
ALTER TABLE departments_aud ADD COLUMN created_date TIMESTAMP;
ALTER TABLE laboratories_aud ADD COLUMN created_date TIMESTAMP;
