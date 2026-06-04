-- V9__Password_History.sql
-- Tabela do przechowywania historii haseł użytkowników (wymóg 21 CFR Part 11)

CREATE TABLE `user_password_history` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `created_date` DATETIME NOT NULL,
    CONSTRAINT `fk_password_history_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Opcjonalnie: Inicjalizacja historii obecnymi hasłami (jako punkt wyjścia)
INSERT INTO `user_password_history` (`user_id`, `password_hash`, `created_date`)
SELECT `id`, `password`, NOW() FROM `users`;
