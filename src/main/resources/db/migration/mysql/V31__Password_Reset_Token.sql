-- V31__Password_Reset_Token.sql
-- Jednorazowy token resetu hasła: w bazie przechowywany jest wyłącznie skrót (SHA-256) tokenu
-- oraz jego czas ważności. Jawny token nigdy nie jest zapisywany.

ALTER TABLE `users`
ADD COLUMN `password_reset_token_hash` VARCHAR(64) NULL,
ADD COLUMN `password_reset_token_expires_at` DATETIME NULL;