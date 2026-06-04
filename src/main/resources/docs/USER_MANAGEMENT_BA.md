# Analiza Biznesowa (BA) - Moduł Zarządzania Użytkownikami (Desktop)

Niniejszy dokument stanowi analizę biznesową modułu uwierzytelniania i autoryzacji dla aplikacji Validation System w wersji Desktop. Bazuje on na istniejącej logice aplikacji webowej, jednak został odpowiednio okrojony i dostosowany do specyfiki lokalnego, jednowątkowego środowiska (JavaFX + lokalna baza MySQL).

Celem modułu jest zapewnienie zgodności z rygorystycznymi standardami bezpieczeństwa (GMP Annex 11, 21 CFR Part 11) w zakresie kontroli dostępu do danych walidacyjnych.

---

## 1. Model Użytkownika (User)

Użytkownik w systemie reprezentuje fizyczną osobę korzystającą z aplikacji. Każde działanie (np. zatwierdzenie protokołu walidacji) musi być przypisane do konkretnego konta.

**Kluczowe atrybuty:**
*   `username` (unikalny identyfikator logowania)
*   `password` (hasło, musi być przechowywane w formie zahaszowanej - BCrypt)
*   `firstName`, `lastName` (dane personalne na potrzeby raportów i podpisów)
*   `email`, `phone` (dane kontaktowe)

**Statusy konta:**
*   `enabled` (Czy konto jest aktywne. Zdezaktywowane konto nie pozwala na logowanie).
*   `locked` / `lockedUntil` (Tymczasowa blokada wynikająca z przekroczenia prób logowania).
*   `accountExpired` (Możliwość zdefiniowania daty końcowej ważności konta, np. dla audytorów zewnętrznych).
*   `credentialsExpired` (Blokada użycia hasła po upływie czasu - wymaga odświeżenia).

---

## 2. Polityka Haseł (Password Policy) - Wymóg GMP

System musi bezwzględnie wymuszać dobre praktyki zarządzania hasłami:
1.  **Wygasanie haseł**: Hasła mają określony czas ważności (`passwordExpiryDays` - domyślnie 90 dni). Po tym czasie użytkownik nie zaloguje się bez wcześniejszej zmiany hasła. Atrybut `passwordExpiresAt` steruje tą datą.
2.  **Wymuszona zmiana hasła (`mustChangePassword`)**: Jeśli Administrator (SUPER_ADMIN) tworzy konto lub resetuje komuś hasło, nadaje mu status "must_change". Użytkownik przy pierwszym poprawnym logowaniu widzi ekran wymuszający ustawienie własnego hasła, zanim aplikacja dopuści go do głównego menu.
3.  **Blokada po błędnych próbach**: System śledzi liczbę nieudanych logowań (`failedLoginAttempts`). Po określonej liczbie prób (np. 3 lub 5), konto jest automatycznie blokowane na określony czas.

---

## 3. Autoryzacja i Uprawnienia (RBAC - Role-Based Access Control)

Dostęp do poszczególnych modułów i akcji (np. podpisywanie planu walidacji, dodawanie urządzeń) oparty jest na przypisanych rolach:
*   Użytkownik może posiadać wiele ról (`ManyToMany` z encją `Role`).
*   **Przykładowe Role biznesowe:** `SUPER_ADMIN` (zarządzanie użytkownikami, konfiguracja systemu), `QA` (Zapewnienie Jakości - zatwierdzanie planów), `USER` (operator - generowanie draftów i raportów).
*   **Cache uprawnień:** System przewiduje opcjonalny, szczegółowy podział uprawnień trzymany w formacie JSON (`permissions_cache_json`), na wypadek gdyby same role były niewystarczające.

---

## 4. Audyt i Śledzenie Zmian (Audit Trail)

*   Encja Użytkownika (`User`) musi być w pełni audytowana za pomocą mechanizmu (np. Hibernate Envers). 
*   Każda zmiana uprawnień, reset hasła czy zmiana nazwiska przez Administratora generuje wpis w historii (kto, kiedy, co zmienił). 
*   *Uwaga biznesowa:* O ile dla aplikacji webowej zapisywaliśmy `LoginHistory` (z adresami IP), dla aplikacji Desktopowej z lokalną bazą logowanie wystarczy zawęzić do historii wejść/wyjść z programu.

---

## 5. Uproszczenia względem aplikacji Webowej (Co odrzucamy)

Ponieważ jest to aplikacja typu "Rich Client" (Desktop), pozbywamy się całego bagażu specyficznego dla protokołu HTTP i przeglądarek:
*   **[POMINIĘTE] Ochrona CSRF (Cross-Site Request Forgery)** - Aplikacja desktopowa nie używa ciasteczek sesyjnych w przeglądarce, więc ataki CSRF tu nie istnieją. Klasa `CsrfViolationAudit` staje się zbędna.
*   **[POMINIĘTE] CSP (Content Security Policy Nonce Filter)** - Zbędne, JavaFX sam renderuje swój interfejs i nie jest podatny na ataki Cross-Site Scripting (XSS) ze złośliwych skryptów wstrzykniętych w HTML.
*   **[POMINIĘTE] Tokeny Resetu Hasła (PasswordResetToken)** - W systemie bez serwera pocztowego (standalone desktop), odzyskiwanie konta polega na tym, że administrator systemu ręcznie przypisuje nowe, tymczasowe hasło i wymusza jego zmianę (flaga `mustChangePassword`).
*   **[POMINIĘTE] Session Fixation** - Uwierzytelnianie następuje lokalnie, aplikacja przechowuje w pamięci obiekt (np. obiekt zalogowanego usera w Singletonie lub kontekście Spring Security), nie ma identyfikatorów sesji `JSESSIONID` przesyłanych przez sieć.

---
**Podsumowanie:** Moduł ten zachowuje cały "szkielet" prawno-regulacyjny z systemu webowego (wygasanie haseł, role, audyt), jednak zostaje oczyszczony z technicznych mechanizmów zabezpieczających aplikacje webowe przed atakami przeglądarkowymi.
