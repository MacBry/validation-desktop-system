-- V7__Populate_Roles_Audit.sql
-- Tworzymy bazową rewizję dla ról, aby Envers mógł je poprawnie odczytać w historii
INSERT INTO `revinfo` (`revtstmp`, `modified_by`) 
VALUES (ROUND(UNIX_TIMESTAMP(NOW()) * 1000), 'SYSTEM_BASELINE');

SET @last_rev = LAST_INSERT_ID();

INSERT INTO `roles_aud` (`id`, `rev`, `revtype`, `name`, `description`, `created_date`)
SELECT `id`, @last_rev, 0, `name`, `description`, `created_date` FROM `roles`;
