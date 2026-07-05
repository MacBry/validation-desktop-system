-- V11__Access_Logs_Table.sql
-- Tabela do śledzenia zdarzeń logowania, wylogowania i błędów bezpieczeństwa

CREATE TABLE `access_logs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL,
    `user_id` BIGINT NULL,
    `timestamp` DATETIME NOT NULL,
    `action` VARCHAR(50) NOT NULL, -- LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT, SECURITY_ALARM
    `ip_address` VARCHAR(45) NULL,
    `details` VARCHAR(255) NULL,
    CONSTRAINT `fk_access_logs_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
);
