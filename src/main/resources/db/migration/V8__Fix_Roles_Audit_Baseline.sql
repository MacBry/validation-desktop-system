-- V8__Fix_Roles_Audit_Baseline.sql
-- Envers potrzebuje, aby encje Role istniały w tabeli roles_aud dla każdej rewizji, w której są używane.
-- Ponieważ zmiany użytkowników zaczęły się od niskich numerów rewizji (np. 22), musimy zapewnić 
-- wpisy w roles_aud z numerem rewizji mniejszym lub równym tym zmianom.

-- Próbujemy wstawić rekord o rev=1 jako punkt zerowy (jeśli nie istnieje)
INSERT IGNORE INTO `revinfo` (`rev`, `revtstmp`, `modified_by`) 
VALUES (1, 0, 'SYSTEM_INITIAL');

-- Kopiujemy role do audytu przypisując im rev=1, co uczyni je "widocznymi" dla wszystkich przyszłych rewizji
INSERT IGNORE INTO `roles_aud` (`id`, `rev`, `revtype`, `name`, `description`, `created_date`)
SELECT `id`, 1, 0, `name`, `description`, `created_date` FROM `roles`;
