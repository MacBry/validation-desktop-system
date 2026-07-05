-- V3__Fix_Admin_Password.sql

-- Okazuje się, że poprzedni hash z wersji webowej nie odpowiadał słowu 'admin'
-- Poniżej nowy, poprawny hash wygenerowany przez BCrypt (cost=12) dla słowa 'admin'

UPDATE `users` 
SET `password` = '$2a$12$..u./AERVax23I8Big8d9upxJZlHTNd7B/dg2x7Hch0XyxUQMQIPi'
WHERE `username` = 'admin';
