-- V31__Password_Reset_Token.sql (wariant H2)
-- Jednorazowy token resetu hasła: w bazie przechowywany jest wyłącznie skrót (SHA-256) tokenu
-- oraz jego czas ważności. Jawny token nigdy nie jest zapisywany.
-- Różnica vs MySQL: H2 nie wspiera wielokrotnego ADD COLUMN w jednym ALTER.

ALTER TABLE `users` ADD COLUMN `password_reset_token_hash` VARCHAR(64) NULL;
ALTER TABLE `users` ADD COLUMN `password_reset_token_expires_at` DATETIME NULL;