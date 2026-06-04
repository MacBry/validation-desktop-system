# Scenariusze Testowe - Moduł Zarządzania Użytkownikami

**Uwaga (maj 2026):** Od maja 2026 `UserService` został refaktoryzowany na cztery wyspecjalizowane serwisy:
- `UserManagementService` - zarządzanie profilami i użytkownikami
- `UserPasswordService` - operacje związane z hasłami
- `UserAuthenticationService` - uwierzytelnianie i sesje
- `UserAccountService` - zarządzanie statusem konta i rolami

Scenariusze testowe poniżej odnoszą się do tych serwisów (wcześniej były częścią `UserService`). Wszystkie testy są wyeksekutowane (27 testów przechodzi pomyślnie).

## 1. Testy Jednostkowe (Unit Tests)

### 1.1. UserService — Zarządzanie Kontami

| ID | Scenariusz | Oczekiwany Wynik | REQ |
|----|-----------|------------------|-----|
| UT-01 | `getAllUsers()` — baza zawiera 3 użytkowników | Zwrócona lista ma rozmiar 3 | REQ-06 |
| UT-02 | `activateUser()` — użytkownik z `enabled=false` | Pole `enabled` zmienia się na `true`, wysyłany jest email aktywacyjny | REQ-07 |
| UT-03 | `deactivateUser()` — użytkownik z `enabled=true` | Pole `enabled` zmienia się na `false`, wysyłany jest email o zablokowaniu | REQ-07 |
| UT-04 | `updateUserRoles()` — nadanie roli `ROLE_USER` | Rola zostaje dodana do kolekcji ról użytkownika | REQ-08 |
| UT-05 | `setMustChangePassword()` — flaga na `true` | Pole `mustChangePassword` zmienia się na `true` | REQ-09 |
| UT-06 | `setMustChangePassword()` — flaga na `false` | Pole `mustChangePassword` zmienia się na `false` | REQ-09 |

### 1.2. UserService — Hasła i Bezpieczeństwo

| ID | Scenariusz | Oczekiwany Wynik | REQ |
|----|-----------|------------------|-----|
| UT-07 | `changeUserPassword()` — poprawne hasło | Hasło zahashowane, `mustChangePassword` = `false` | REQ-13 |
| UT-08 | `resetPassword()` — email istnieje w bazie | Hasło zmienione, `mustChangePassword` = `true`, email wysłany, zwraca `true` | REQ-19, REQ-20 |
| UT-09 | `resetPassword()` — email nie istnieje | Zwraca `false`, żaden email nie jest wysyłany | REQ-19 |
| UT-10 | `changePasswordWithOld()` — stare hasło poprawne | Hasło zmienione, zwraca `true` | REQ-16 |
| UT-11 | `changePasswordWithOld()` — stare hasło błędne | Hasło nie zmienione, zwraca `false` | REQ-16 |

### 1.3. UserService — Blokada Po Nieudanych Logowaniach

| ID | Scenariusz | Oczekiwany Wynik | REQ |
|----|-----------|------------------|-----|
| UT-12 | `incrementFailedLoginAttempts()` — 1. nieudana próba | `failedLoginAttempts` = 1, konto NOT locked | REQ-22 |
| UT-13 | `incrementFailedLoginAttempts()` — 5. nieudana próba | `failedLoginAttempts` = 5, `locked` = `true`, `lockedUntil` ustawiony | REQ-22 |
| UT-14 | `resetFailedLoginAttempts()` — po pomyślnym logowaniu | `failedLoginAttempts` = 0, `locked` = `false`, `lockedUntil` = `null` | REQ-22 |
| UT-15 | `lockUser()` — ręczna blokada przez admina | `locked` = `true`, `lockedUntil` = `null` (blokada bezterminowa) | REQ-23 |
| UT-16 | `unlockUser()` — ręczne odblokowanie | `locked` = `false`, `failedLoginAttempts` = 0 | REQ-23 |

### 1.4. UserService — Profil i LastLogin

| ID | Scenariusz | Oczekiwany Wynik | REQ |
|----|-----------|------------------|-----|
| UT-17 | `updateUserProfile()` — zmiana imienia i e-maila | Pola zaktualizowane w bazie | REQ-15 |
| UT-18 | `updateLastLogin()` — po pomyślnym logowaniu | `lastLogin` ustawiony na bieżący czas | REQ-24 |
| UT-19 | `getSuperAdminEmails()` — 2 adminów w bazie | Zwrócona lista z 2 adresami | — |

### 1.5. Walidacja Siły Hasła

| ID | Scenariusz | Oczekiwany Wynik | REQ |
|----|-----------|------------------|-----|
| UT-20 | Hasło `"Abcdef1!"` (poprawne) | `null` (brak błędu) | REQ-21 |
| UT-21 | Hasło `"abc"` (za krótkie) | Komunikat o minimalnej długości | REQ-21 |
| UT-22 | Hasło `"abcdefgh1!"` (brak wielkiej litery) | Komunikat o wielkiej literze | REQ-21 |
| UT-23 | Hasło `"ABCDEFGH1!"` (brak małej litery) | Komunikat o małej literze | REQ-21 |
| UT-24 | Hasło `"Abcdefgh!"` (brak cyfry) | Komunikat o cyfrze | REQ-21 |
| UT-25 | Hasło `"Abcdefgh1"` (brak znaku specjalnego) | Komunikat o znaku specjalnym | REQ-21 |

### 1.6. EmailService — Wysyłka Powiadomień

| ID | Scenariusz | Oczekiwany Wynik | REQ |
|----|-----------|------------------|-----|
| UT-26 | `sendPasswordResetEmail()` | `JavaMailSender.send()` wywołany z poprawnym tematem i treścią | REQ-20 |
| UT-27 | `sendAccountActivatedEmail()` | `JavaMailSender.send()` wywołany z poprawnym tematem | — |
| UT-28 | `sendAccountDeactivatedEmail()` | `JavaMailSender.send()` wywołany z poprawnym tematem | — |
| UT-29 | `sendNewUserAdminNotification()` — lista adminów pusta | `JavaMailSender.send()` NIE wywołany | — |
| UT-30 | `sendNewUserAdminNotification()` — 2 adminów | `JavaMailSender.send()` wywołany, odbiorca to tablica 2 adresów | — |

---

## 2. Testy Integracyjne (Integration Tests)

| ID | Scenariusz | Oczekiwany Wynik | REQ |
|----|-----------|------------------|-----|
| IT-01 | Rejestracja użytkownika → zapis do bazy | Rekord istnieje w DB z `enabled=false` i brak ról | REQ-01, REQ-02 |
| IT-02 | Aktywacja konta → ponowne logowanie | Użytkownik może się zalogować po aktywacji | REQ-07 |
| IT-03 | Reset hasła → logowanie tymczasowym hasłem | Logowanie powodzi się, `mustChangePassword` = `true` | REQ-19 |
| IT-04 | Zmiana ról → weryfikacja w SecurityContext | Po ponownym logowaniu nowe role widoczne w `getAuthorities()` | REQ-08 |

---

## 3. Testy Manualne (UI / Acceptance Tests)

| ID | Scenariusz | Kroki | Oczekiwany Wynik | REQ |
|----|-----------|-------|------------------|-----|
| MT-01 | Rejestracja z pustymi polami | 1. Otwórz zakładkę Rejestracja 2. Pozostaw pola puste 3. Kliknij "Zarejestruj" | Komunikat "Login jest wymagany!" | REQ-03b |
| MT-02 | Rejestracja z duplikatem e-maila | 1. Zarejestruj konto z emailem X 2. Spróbuj ponownie z tym samym emailem | Komunikat "Podany adres e-mail jest już zarejestrowany" | REQ-03a |
| MT-03 | Rejestracja ze słabym hasłem | 1. Wypełnij formularz, hasło = "abc" 2. Kliknij "Zarejestruj" | Komunikat o minimalnych wymaganiach hasła | REQ-21 |
| MT-04 | Logowanie konta nieaktywnego | 1. Zarejestruj nowe konto 2. Spróbuj zalogować się | Komunikat o oczekiwaniu na administratora | REQ-04 |
| MT-05 | Brute-force — cooldown przycisku | 1. Wpisz błędne hasło 2. Obserwuj przycisk "Zaloguj się" | Przycisk zablokowany na 3s z odliczaniem | REQ-25 |
| MT-06 | Brute-force — blokada konta | 1. Wpisz błędne hasło 5 razy | Komunikat o zablokowaniu konta | REQ-22 |
| MT-07 | Brute-force — licznik prób | 1. Wpisz błędne hasło 3 razy | Komunikat "Pozostało prób: 2" | REQ-26 |
| MT-08 | Wymuszenie zmiany hasła | 1. Admin zaznacza `mustChangePassword` 2. Użytkownik loguje się | Przekierowanie do formularza zmiany hasła | REQ-11, REQ-12 |
| MT-09 | Edycja profilu | 1. Zaloguj się 2. Kliknij "Edytuj Profil" 3. Zmień imię 4. Zapisz | Komunikat "Profil zaktualizowany" | REQ-15 |
| MT-10 | Zmiana hasła (błędne stare) | 1. Edytuj profil 2. Wpisz złe stare hasło 3. Zapisz | Komunikat "Obecne hasło jest nieprawidłowe" | REQ-16 |
| MT-11 | Panel Admina — aktywacja konta | 1. Zaloguj się jako admin 2. Aktywuj konto 3. Zapisz | Status zmieniony, użytkownik otrzymuje email | REQ-07 |
| MT-12 | Panel Admina — blokada/odblokowanie | 1. Zaznacz checkbox "Locked" 2. Zapisz 3. Odznacz 4. Zapisz | Konto zablokowane/odblokowane w bazie | REQ-23 |
| MT-13 | Wylogowanie | 1. Kliknij "Wyloguj" | Powrót do ekranu logowania, kontekst wyczyszczony | REQ-17 |
| MT-14 | Email — nowa rejestracja | 1. Zarejestruj konto | Super Admini otrzymują maila z danymi nowego użytkownika | — |
| MT-15 | Email — reset hasła | 1. Przejdź do "Reset Hasła" 2. Wpisz email 3. Kliknij | Email z hasłem tymczasowym na skrzynce | REQ-20 |
