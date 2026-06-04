-- V2__Security_Schema.sql

SET NAMES utf8mb4;

-- 1. USERS
CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(50) UNIQUE NOT NULL,
    `email` VARCHAR(100) UNIQUE NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `enabled` BOOLEAN NOT NULL DEFAULT TRUE,
    `locked` BOOLEAN NOT NULL DEFAULT FALSE,
    `account_expired` BOOLEAN NOT NULL DEFAULT FALSE,
    `credentials_expired` BOOLEAN NOT NULL DEFAULT FALSE,
    `failed_login_attempts` INT NOT NULL DEFAULT 0,
    `locked_until` DATETIME NULL,
    `first_name` VARCHAR(100) NULL,
    `last_name` VARCHAR(100) NULL,
    `phone` VARCHAR(20) NULL,
    `created_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_date` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    `last_login` DATETIME NULL,
    `must_change_password` BOOLEAN NOT NULL DEFAULT FALSE,
    `password_changed_at` DATETIME NULL,
    `password_expires_at` DATETIME NULL,
    `password_expiry_days` INT DEFAULT 90,
    `created_by` BIGINT NULL,
    INDEX `idx_username` (`username`),
    INDEX `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. ROLES
CREATE TABLE IF NOT EXISTS `roles` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name` VARCHAR(50) UNIQUE NOT NULL,
    `description` VARCHAR(255) NULL,
    `created_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. USER_ROLES
CREATE TABLE IF NOT EXISTS `user_roles` (
    `user_id` BIGINT NOT NULL,
    `role_id` BIGINT NOT NULL,
    `granted_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `granted_by` BIGINT NULL,
    PRIMARY KEY (`user_id`, `role_id`),
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`role_id`) REFERENCES `roles`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. PASSWORD_HISTORY
CREATE TABLE IF NOT EXISTS `password_history` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `created_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. PASSWORD_RESET_TOKENS
CREATE TABLE IF NOT EXISTS `password_reset_tokens` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `token` VARCHAR(255) UNIQUE NOT NULL,
    `expires_date` DATETIME NOT NULL,
    `used` BOOLEAN NOT NULL DEFAULT FALSE,
    `created_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. AUDIT_LOG (Aplikacyjny)
CREATE TABLE IF NOT EXISTS `audit_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NULL,
    `username` VARCHAR(50) NULL,
    `entity_type` VARCHAR(100) NOT NULL,
    `entity_id` BIGINT NOT NULL,
    `action` VARCHAR(50) NOT NULL,
    `old_value_json` JSON NULL,
    `new_value_json` JSON NULL,
    `timestamp` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `user_agent` VARCHAR(500) NULL,
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. LOGIN_HISTORY
CREATE TABLE IF NOT EXISTS `login_history` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NULL,
    `username` VARCHAR(50) NOT NULL,
    `login_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `ip_address` VARCHAR(45) NULL,
    `success` BOOLEAN NOT NULL,
    `failure_reason` VARCHAR(255) NULL,
    FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. REVINFO (Hibernate Envers)
CREATE TABLE IF NOT EXISTS `revinfo` (
    `rev` INT PRIMARY KEY AUTO_INCREMENT,
    `revtstmp` BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. users_AUD (Tabela audytowa dla Users)
CREATE TABLE IF NOT EXISTS `users_aud` (
    `id` BIGINT NOT NULL,
    `rev` INT NOT NULL,
    `revtype` TINYINT,
    `username` VARCHAR(50),
    `email` VARCHAR(100),
    `password` VARCHAR(255),
    `enabled` BOOLEAN,
    `locked` BOOLEAN,
    `account_expired` BOOLEAN,
    `credentials_expired` BOOLEAN,
    `failed_login_attempts` INT,
    `locked_until` DATETIME,
    `first_name` VARCHAR(100),
    `last_name` VARCHAR(100),
    `phone` VARCHAR(20),
    `created_date` DATETIME,
    `updated_date` DATETIME,
    `last_login` DATETIME,
    `must_change_password` BOOLEAN,
    `password_changed_at` DATETIME,
    `password_expires_at` DATETIME,
    `password_expiry_days` INT,
    `created_by` BIGINT,
    PRIMARY KEY (`id`, `rev`),
    FOREIGN KEY (`rev`) REFERENCES `revinfo`(`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- INITIAL DATA
INSERT IGNORE INTO `roles` (`name`, `description`) VALUES
('ROLE_SUPER_ADMIN', 'Super Administrator'),
('ROLE_QA', 'Zapewnienie Jakości (Quality Assurance)'),
('ROLE_USER', 'Użytkownik standardowy');

INSERT IGNORE INTO `users` (`username`, `email`, `password`, `enabled`, `first_name`, `last_name`, `created_date`) VALUES 
('admin', 'admin@local', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIpEvKBqe2', TRUE, 'System', 'Admin', NOW());

INSERT IGNORE INTO `user_roles` (`user_id`, `role_id`, `granted_date`)
SELECT u.`id`, r.`id`, NOW() FROM `users` u CROSS JOIN `roles` r
WHERE u.`username` = 'admin' AND r.`name` = 'ROLE_SUPER_ADMIN';
