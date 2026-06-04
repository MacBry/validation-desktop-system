-- V5__Audit_Roles.sql

-- Tabela audytowa dla ról
CREATE TABLE IF NOT EXISTS `roles_aud` (
    `id` BIGINT NOT NULL,
    `rev` INT NOT NULL,
    `revtype` TINYINT,
    `name` VARCHAR(50),
    `description` VARCHAR(255),
    PRIMARY KEY (`id`, `rev`),
    FOREIGN KEY (`rev`) REFERENCES `revinfo`(`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabela audytowa dla powiązań użytkownik-rola
CREATE TABLE IF NOT EXISTS `user_roles_aud` (
    `rev` INT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    `revtype` TINYINT,
    PRIMARY KEY (`rev`, `user_id`, `role_id`),
    FOREIGN KEY (`rev`) REFERENCES `revinfo`(`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
