-- V10__User_Session_Tracking.sql (wariant H2)
-- Dodanie pól do śledzenia aktywnych sesji użytkowników.
-- Różnica vs MySQL: H2 nie wspiera wielokrotnego ADD COLUMN w jednym ALTER.

ALTER TABLE `users` ADD COLUMN `session_token` VARCHAR(255) NULL;
ALTER TABLE `users` ADD COLUMN `last_activity` DATETIME NULL;
