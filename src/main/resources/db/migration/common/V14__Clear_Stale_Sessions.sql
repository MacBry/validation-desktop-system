-- V14__Clear_Stale_Sessions.sql
-- Czyszczenie wszystkich aktywnych sesji, aby umożliwić ponowne logowanie po awarii/restarcie aplikacji
UPDATE users SET session_token = NULL, last_activity = NULL;
