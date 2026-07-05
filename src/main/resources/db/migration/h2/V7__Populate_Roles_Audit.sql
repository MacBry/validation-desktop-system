-- V7__Populate_Roles_Audit.sql (wariant H2)
-- Tworzymy bazową rewizję dla ról, aby Envers mógł je poprawnie odczytać w historii.
-- Różnica vs MySQL: UNIX_TIMESTAMP() w H2 zwraca INT — mnożenie *1000 przepełnia zakres;
-- używamy jawnego CAST na BIGINT.
INSERT INTO `revinfo` (`revtstmp`, `modified_by`)
VALUES (CAST(UNIX_TIMESTAMP(NOW()) AS BIGINT) * 1000, 'SYSTEM_BASELINE');

SET @last_rev = LAST_INSERT_ID();

INSERT INTO `roles_aud` (`id`, `rev`, `revtype`, `name`, `description`, `created_date`)
SELECT `id`, @last_rev, 0, `name`, `description`, `created_date` FROM `roles`;
