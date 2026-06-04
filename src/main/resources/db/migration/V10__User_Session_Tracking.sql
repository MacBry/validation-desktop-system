-- V10__User_Session_Tracking.sql
-- Dodanie pól do śledzenia aktywnych sesji użytkowników

ALTER TABLE `users` 
ADD COLUMN `session_token` VARCHAR(255) NULL,
ADD COLUMN `last_activity` DATETIME NULL;
