# Backlog Rozwojowy - Moduł Zarządzania Użytkownikami i Audytu

Poniższa lista zawiera zidentyfikowane luki funkcjonalne i techniczne, których wdrożenie podniesie standard bezpieczeństwa aplikacji do poziomu systemów medycznych klasy GxP / 21 CFR Part 11.

## 1. Bezpieczeństwo i Kontrola Dostępu
- [x] **Historia Haseł (Password History)**: 
    - [x] Uniemożliwienie ponownego użycia ostatnich 5 haseł przez użytkownika.
    - [x] Wymaga stworzenia tabeli `user_password_history`.
    - [x] Implementacja walidacji w serwisie i obsługa błędów w UI.
    - [x] Testy integracyjne potwierdzające działanie.
- [x] **Automatyczne Wylogowanie (Inactivity Timeout)**: 
    - [x] Implementacja globalnego monitora zdarzeń w JavaFX (`InactivityMonitor`).
    - [x] Automatyczne zamknięcie sesji i powrót do ekranu logowania po 15 minutach (konfigurowalne).
    - [x] Integracja z `MainController` i obsługa resetowania timera przy aktywności.
- [x] **Blokada Jednoczesnych Logowań (Concurrent Session Control)**: 
    - [x] Dodanie śledzenia `session_token` i `last_activity` w bazie danych.
    - [x] Walidacja statusu logowania w `LoginController` (blokowanie podwójnych sesji).
    - [x] Mechanizm Heartbeat w `InactivityMonitor` odświeżający aktywność w bazie co 5 minut.
    - [x] Obsługa "martwych sesji" (timeout 30 min w bazie).
- [x] **Wygaśnięcie Konta (Account Inactivity Expiration)**:
    - [x] Automatyczna dezaktywacja kont nieużywanych przez ponad 90 dni.
    - [x] Walidacja daty ostatniego logowania przy próbie dostępu.
    - [x] Konfiguracja globalna w `application.yml`.

## 2. Rozszerzony Audyt i Raportowanie
- [x] **Audyt Zdarzeń Dostępu (Access Logging)**: 
    - [x] Rejestracja każdego udanego logowania, nieudanej próby oraz wylogowania w dedykowanej tabeli `access_logs`.
    - [x] Widok logów w Panelu Administratora (zakładka "Logi Dostępu").
- [x] **Eksport Historii Audytu**: 
    - [x] Przycisk "Eksport PDF" i "Eksport CSV" w widoku historii.
    - [x] Generowanie raportów PDF przy użyciu OpenPDF i CSV przy użyciu OpenCSV.
- [x] **Logowanie Błędów Krytycznych**:
    - [x] Centralny mechanizm logowania prób nieautoryzowanego dostępu jako `SECURITY_ALARM`.

## 3. Funkcjonalność Panelu Administratora
- [x] **Wyszukiwanie i Filtrowanie**: 
    - [x] Dodanie pola wyszukiwarki (po loginie, nazwisku, e-mail).
    - [x] Filtry statusu: Aktywni / Nieaktywni / Zablokowani.
    - [x] Implementacja `FilteredList` (dynamiczne filtrowanie bez przeładowania).
- [x] **Masowe Działania (Bulk Actions)**: 
    - [x] Możliwość zaznaczenia wielu użytkowników (SelectionMode.MULTIPLE).
    - [x] Zbiorcza dezaktywacja i blokowanie kont.
    - [x] Symulacja masowych powiadomień e-mail.

## 4. Podpis Elektroniczny (Compliance)
- [ ] **Electronic Signature Workflow**: 
    - Stworzenie mechanizmu "Zatwierdź podpisem", który wymaga od użytkownika ponownego wpisania hasła przed wykonaniem krytycznej operacji (np. zatwierdzenie wyników walidacji).
    - Powiązanie podpisu z Audit Trail.

## 5. Utrzymanie i Infrastruktura
- [ ] **Integracja z Active Directory (LDAP/AD)**: 
    - Umożliwienie logowania poświadczeniami domenowymi RCKiK Poznań.
- [x] **Automatyczny Backup Bazy**: 
    - [x] Mechanizm `DatabaseBackupService` wykonujący zrzut bazy co 12h.
    - [x] Rotacja plików (retencja 14 dni).
    - [x] Automatyczne tworzenie folderu `backups/db`.
