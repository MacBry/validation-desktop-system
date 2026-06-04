# Specyfikacja Implementacyjna (Technical Specification) - Zarządzanie Użytkownikami

**Dokument gotowy do audytu (Audit-Ready)**  
**Moduł:** Security / User Management (Desktop Edition)  
**Zgodność Regulacyjna:** Wymagania 21 CFR Part 11 (Electronic Records, Electronic Signatures) oraz EU GMP Annex 11.

---

## 1. Cel Dokumentu
Niniejszy dokument precyzuje techniczne szczegóły implementacji systemu uwierzytelniania, autoryzacji oraz śledzenia zmian (Audit Trail) w module zarządzania użytkownikami dla aplikacji Validation System Desktop (Single-Instance JavaFX). Służy jako referencja architektoniczna oraz podkład dla audytorów jakości.

## 2. Architektura Bezpieczeństwa (Security Architecture)

Aplikacja desktopowa integruje silnik **Spring Security (wersja 3.2.x)** z graficznym interfejsem **JavaFX**. Ze względu na specyfikę "Rich Client", odrzucono tradycyjne filtry sieciowe (HTTP Security Filter Chain, filtry CSRF, mechanizmy ciasteczek).

**Proces Uwierzytelniania:**
1. Ekran `LoginController` pobiera identyfikator użytkownika (`username`) oraz `password`.
2. Hasło jest przekazywane do obiektu `AuthenticationManager` udostępnianego przez Spring Security.
3. System weryfikuje skrót kryptograficzny algorytmem **BCrypt (koszt: 12)** poprzez `DaoAuthenticationProvider`.
4. Po weryfikacji, obiekt zalogowanego użytkownika (`UserDetails`) zapisywany jest w `SecurityContextHolder`. Cała dalsza interakcja w aplikacji pobiera kontekst autoryzacyjny bezpośrednio z tego statycznego zasobnika w pamięci lokalnej (RAM).

### 2.1. Architektura Warstwy Serwisu (Service Layer Architecture)

Od maja 2026 warstwa serwisu została refaktoryzowana z monolitycznej klasy `UserService` (316 linii) na cztery wyspecjalizowane serwisy zgodnie z zasadą Single Responsibility Principle (SRP):

#### **UserManagementService** (41 linii)
- **Odpowiedzialność:** Operacje CRUD i zarządzanie profilami użytkowników
- **Metody:**
  - `getAllUsers()` - pobieranie listy wszystkich użytkowników
  - `getAllUsersByDepartment(Department)` - filtrowanie użytkowników po dziale
  - `updateUserProfile(User)` - aktualizacja danych osobowych
  - `updateUserLocation(User, Laboratory)` - zmiana przypisanej pracowni
- **Zależności:** UserRepository, DepartmentRepository, LaboratoryRepository
- **Transakcje:** @Transactional dla operacji zapisu

#### **UserPasswordService** (88 linii)
- **Odpowiedzialność:** Wszystkie operacje związane z hasłami
- **Metody:**
  - `changeUserPassword(User, oldPassword, newPassword)` - zmiana hasła z weryfikacją starego
  - `changePasswordWithOld(String username, String oldPassword, String newPassword)` - zmiana przez użytkownika
  - `resetPassword(User)` - reset hasła przez administratora
  - `isPasswordInHistory(User, rawPassword)` - sprawdzanie historii haseł
- **Helper Method:** `updatePasswordWithAging(User, encodedPassword)` - enkapsulacja logiki haszowania i wygasania
- **Zależności:** UserRepository, PasswordEncoder (BCrypt), PasswordHistoryRepository
- **Logika:** Walidacja siły hasła, ścieżka historii, automatyczne wygasanie co 90 dni

#### **UserAuthenticationService** (85 linii)
- **Odpowiedzialność:** Uwierzytelnianie, zarządzanie sesjami i blokady kont
- **Metody:**
  - `incrementFailedLoginAttempts(User)` - inkrementacja licznika nieudanych logowań
  - `resetFailedLoginAttempts(User)` - resetowanie licznika po udanym logowaniu
  - `registerSession(User)` - zarejestrowanie aktywnej sesji użytkownika
  - `updateLastLogin(User)` - aktualizacja czasu ostatniego logowania
  - `clearSession(User)` - wyczyszczenie sesji
  - `updateActivity(User)` - aktualizacja aktywności użytkownika
  - `isAlreadyLoggedIn(User)` - sprawdzanie, czy użytkownik jest zalogowany
  - `checkAccountExpiration(User)` - weryfikacja wygaśnięcia konta
- **Stałe:**
  - `MAX_FAILED_ATTEMPTS = 5` - limit nieudanych prób logowania
  - `LOCK_DURATION_MINUTES = 15` - czas blokady konta
  - `SESSION_TIMEOUT_MINUTES = 30` - timeout sesji
- **Zależności:** UserRepository, LoginHistoryRepository, SessionRepository
- **Logika:** Brute-force protection z automatyczną blokaką, śledzenie sesji

#### **UserAccountService** (90 linii)
- **Odpowiedzialność:** Zarządzanie statusem konta i rolami
- **Metody:**
  - `activateUser(User)` - aktywacja konta użytkownika
  - `deactivateUser(User)` - deaktywacja konta
  - `lockUser(User)` - zablokowanie konta (security lockout)
  - `unlockUser(User)` - odblokowanie konta
  - `setMustChangePassword(User, boolean)` - wymuszczenie zmiany hasła
  - `updateUserRoles(User, Set<Role>)` - zmiana ról użytkownika
  - `hasRole(User, String roleName)` - sprawdzanie roli
  - `getSuperAdminEmails()` - pobranie emaili super administratorów
  - `getAllRoles()` - zwrócenie dostępnych ról
- **Zależności:** UserRepository, RoleRepository, EmailService
- **Integracje:** Wysyła powiadomienia e-mail o aktywacji/deaktywacji

#### **UserService (Facade Pattern)** (138 linii)
- **Odpowiedzialność:** Orchestracja i backward compatibility
- **Architektura:** Facade Pattern dla 100% kompatybilności wstecznej
- **Delegacja:** Wszystkie metody delegują do odpowiednich wyspecjalizowanych serwisów
- **Zależności:** UserManagementService, UserPasswordService, UserAuthenticationService, UserAccountService
- **Uwaga:** Nowy kod powinien używać bezpośrednio wyspecjalizowanych serwisów; UserService utrzymywany dla istniejącego kodu

**Korzyści Refaktoryzacji:**
- ✅ Zmniejszenie UserService z 316 do 138 linii (-56%)
- ✅ Każdy serwis ma jedno jasno zdefiniowane zadanie
- ✅ Łatwiejsze testowanie - każdy serwis testowany niezależnie
- ✅ Lepsze wykorzystanie - serwisy mogą być używane przez inne komponenty
- ✅ Brak zmian w istniejącym kodzie - pełna backward compatibility

## 3. Struktura Danych i Migracje (Flyway)

Inicjalizacja i kontrola wersji schematu bazy danych jest zarządzana poprzez zautomatyzowane migracje **Flyway**. Struktura składa się z następujących tabel:

*   `users` – Przechowuje poświadczenia logowania, dane osobowe i statusy zabezpieczeń. Hasła nigdy nie są przechowywane w postaci otwartej (Clear Text).
*   `roles` / `user_roles` – Implementacja kontroli dostępu opartej na rolach (RBAC).
*   `password_history` – Rejestruje hashe historycznych haseł w celu weryfikacji przy zmianie (zapobiega cykliczności używania tego samego hasła).
*   `login_history` – Tablica przechowująca zdarzenia wejścia do aplikacji.

*(Migracje bazują na pliku `V2__Security_Schema.sql`, co gwarantuje pełną powtarzalność i weryfikowalność tworzenia bazy danych od zera podczas uruchamiania instalatora na środowisku końcowym).*

## 4. Zgodność z 21 CFR Part 11 (Elektroniczne Poświadczenia)

System wdraża rygorystyczne mechanizmy ochrony poświadczeń elektronicznych, narzucone z poziomu architektury bazodanowej i kodu Javy:

### 4.1. Polityka wygasania haseł (Password Aging)
*   **Wymóg:** `users.password_expires_at`. Hasła wygasają domyślnie co 90 dni (`passwordExpiryDays`).
*   **Mechanizm:** Zablokowanie głównego UI aplikacji. Przy próbie logowania po przekroczeniu tej daty, użytkownik zmuszony jest podać nowe hasło do autoryzacji w specjalnym interfejsie.

### 4.2. Polityka wymuszonej zmiany (Forced Change)
*   Użytkownik z przypisaną flagą `must_change_password = TRUE` (np. wygenerowany przez Administratora po raz pierwszy) nie uzyska dostępu do systemu operacyjnego aplikacji, dopóki nie zmieni hasła. W starym modelu Web odpowiadał za to `ForcedPasswordChangeFilter`, w aplikacji desktopowej odpowiada za to stan ładowania widoku (blokada na kontrolerze `LoginController`).

### 4.3. Zabezpieczenie przed atakiem siłowym (Brute Force Lockout)
*   **Zmienne:** `failed_login_attempts` oraz `locked_until`.
*   Z każdym błędnym logowaniem inkrementowany jest licznik prób (w oddzielnej transakcji bazodanowej). Po przekroczeniu dopuszczalnego limitu aplikacja automatycznie blokuje możliwość logowania na określony przedział czasowy, nawet w przypadku poprawnego wprowadzenia loginu.

## 5. Ścieżka Audytu (Audit Trail)

Audyt systemu został zrealizowany na dwóch poziomach: operacyjnym (Logi) oraz strukturalnym (Hibernate Envers).

### 5.1. Hibernate Envers (Zgodność z GMP)
*   Każda klasa encji (np. `User`) oznaczona jest adnotacją `@Audited`.
*   Hibernate automatycznie tworzy tabele lustrzane (np. `users_AUD`) oraz tabelę centralną z numerami rewizji `REVINFO`.
*   Żadna zmiana atrybutu (np. dezaktywacja użytkownika, modyfikacja `failed_login_attempts`) nie nadpisuje historycznego zapisu bezpowrotnie – zawsze wprowadzana jest inkrementacyjna modyfikacja w tabeli audytowej. Tych wpisów nie da się zmodyfikować ani usunąć przez aplikację biznesową.

### 5.2. Log Operacji (`audit_log`)
Została przeniesiona tabela aplikacyjna wspierająca zrzucanie logów dla bezpośrednich interakcji systemowych:
*   Zapis danych w postaci JSON (wartości stare `old_value_json` / nowe `new_value_json`).
*   Odnotowanie nazwy zalogowanego użytkownika (z `SecurityContextHolder`).
*   Rejestrowanie czasu operacji na serwerze na poziomie pojedynczych sekund (Timestamp).

## 6. Procedury Awaryjne i Bezpieczeństwo Instancji Lokalnej

1.  **Szyfrowanie połączenia z bazą:** Połączenie sterownika JDBC odbywa się z zalecaną konfiguracją autoryzacyjną MySQL. 
2.  **Brak sesji sieciowej:** Wdrożenie Desktop całkowicie eliminuje podatności na przejęcie identyfikatora sesji, co oznacza pełne spełnienie założeń architektury zamkniętej (Closed System) według klasyfikacji regulacyjnej dla oprogramowania zintegrowanego z oprzyrządowaniem laboratoryjnym.
