# Plan Implementacji - Moduł Zarządzania Użytkownikami i Bezpieczeństwa

## 1. Etapy Prac (Phase 1: Fundamenty) ✅
- [x] **Model Danych i Baza**:
    - [x] Rozszerzenie tabeli `users` o pola: `first_name`, `last_name`, `phone`, `must_change_password`, `failed_login_attempts`, `locked_until`.
    - [x] Migracja Flyway V2 (Schema Security).
- [x] **Logika Biznesowa (`UserService`)**:
    - [x] Implementacja `UserDetailsManager`.
    - [x] Funkcje: blokada konta, reset hasła, wymuszanie zmiany hasła.
    - [x] Powiadomienia e-mail (`EmailService`).
- [x] **Zabezpieczenia**:
    - [x] Walidacja siły hasła (Regexp).
    - [x] Mechanizm Brute-force (blokada na 15 min po 5 próbach).

## 2. Etapy Prac (Phase 2: UI i Audyt) ✅
- [x] **Interfejs Użytkownika (JavaFX)**:
    - [x] Panel Administratora (`admin_panel.fxml`) - tabela, edycja statusu, przypisywanie ról.
    - [x] Edycja Profilu (`user_profile.fxml`) - zmiana danych i hasła przez użytkownika.
- [x] **Logowanie zdarzeń (Audit Trail)**:
    - [x] Konfiguracja Hibernate Envers i RevisionListener (Migracje V4-V8).
    - [x] Widok historii zmian dla każdego użytkownika (Przycisk "Historia Audytu").
    - [x] Szczegóły zmian: co, kiedy i przez kogo zostało zmienione.
    Szczegóły: [USER_AUDIT_IMPLEMENTATION_PLAN.md](USER_AUDIT_IMPLEMENTATION_PLAN.md)

## 3. Szczegóły Techniczne UI
1.  **Główny Kontroler Nawigacji (`MainController`)**:
    *   Przycisk "Panel Administratora" ładujący widok zarządzania (przycisk widoczny tylko po weryfikacji roli w `SecurityContextHolder`).
    *   Stworzenie panelu profilu na dole nawigacji bocznej wyświetlającego imię, nazwisko, e-mail i role użytkownika.
    *   Przycisk "Edytuj Profil" ładujący widok `user_profile.fxml` w centrum.
    *   Przycisk "Wyloguj" wbudowany w panel boczny, powiązany z metodą `handleLogout` w kontrolerze głównym.
2.  **Widok Panelu (`admin_panel.fxml`)**:
    *   Tabela (`TableView`) wyświetlająca kolekcję obiektów `User`.
    *   Panele właściwości (np. z prawej strony) pozwalające na edycję zaznaczonego użytkownika.
    *   CheckBox'y generowane dynamicznie na podstawie tabeli `roles` z bazy danych.
    *   Przycisk "Zapisz Zmiany" delegujący zmiany do `UserAccountService` (lub `UserManagementService` w zależności od typu zmian).
3.  **Kontroler Panelu (`AdminPanelController`)**:
    *   Podpięcie FXML, obsługa zaznaczeń w `TableView`, przesyłanie zmian do serwisów.
    *   **Od maja 2026:** Delegacja do wyspecjalizowanych serwisów zamiast monolitycznego `UserService`:
        - `UserManagementService` dla aktualizacji profilu
        - `UserAccountService` dla zmian roli i statusu konta
        - `UserAuthenticationService` dla resetowania blokad
        - `UserPasswordService` dla resetowania haseł

## 4. Wykonanie i Testowanie ✅
*   **Testy Jednostkowe (30)**: ✅ Zaimplementowano w `UserServiceTest`, `EmailServiceTest`, `PasswordValidationTest`. Pokrycie logiki biznesowej, walidacji i wysyłki e-mail.
*   **Testy Integracyjne (4)**: ✅ Zaimplementowano w `UserManagementIntegrationTest` (ActiveProfile: test, H2 Database).
*   **Weryfikacja manualna**: ✅ Zgodnie z listą scenariuszy w `USER_MANAGEMENT_TEST_SCENARIOS.md`.
*   **Rezultat**: Wszystkie testy (34/34) przechodzą pomyślnie (`mvn test`).

*   **Scenariusz wzorcowy**:
    1. Rejestracja nowego użytkownika -> powiadomienie e-mail do adminów.
    2. Admin aktywuje konto w Panelu Administratora -> powiadomienie e-mail do użytkownika.
    3. Logowanie użytkownika -> wymuszona zmiana hasła (jeśli wymagana).
    4. Reset hasła -> wysłanie hasła tymczasowego mailem.
    5. Brute-force -> blokada przycisku UI i blokada konta po 5 próbach.
    6. Zmiana danych/ról -> zapis w audycie (Envers).
