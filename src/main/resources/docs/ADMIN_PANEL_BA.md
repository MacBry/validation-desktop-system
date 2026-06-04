# Analiza Biznesowa (BA) - Panel Administratora i Akceptacja Użytkowników

## 1. Cel Biznesowy
Celem biznesowym wdrożenia jest zabezpieczenie dostępu do systemu przed nieautoryzowanymi użytkownikami oraz zapewnienie pełnej kontroli nad nadawaniem uprawnień. System zyska "Panel Administratora", który pozwoli z jednego miejsca zarządzać wszystkimi kontami w aplikacji. Ze względu na specyfikę systemów walidacyjnych (GAMP 5, 21 CFR Part 11), proces rejestracji musi być rygorystycznie kontrolowany.

## 2. Wymagania Funkcjonalne

### 2.1. Proces Rejestracji i Akceptacji
*   **REQ-01**: Nowo zarejestrowane konto domyślnie posiada status nieaktywny (`enabled = false`).
*   **REQ-02**: Nowo zarejestrowane konto nie posiada przypisanych żadnych ról systemowych.
*   **REQ-03**: Po zakończeniu rejestracji użytkownik widzi komunikat informujący, że konto oczekuje na akceptację.
*   **REQ-03a**: System waliduje unikalność loginu oraz adresu e-mail. Próba rejestracji z już istniejącym adresem e-mail lub loginem kończy się wyświetleniem stosownego komunikatu błędu.
*   **REQ-03b**: System waliduje, czy wszystkie wymagane pola formularza rejestracji (login, e-mail, hasło) są wypełnione.
*   **REQ-04**: Użytkownik nieaktywny otrzymuje stosowny komunikat przy próbie logowania.

### 2.2. Panel Administratora
*   **REQ-05**: Panel jest dostępny i widoczny wyłącznie dla użytkowników z rolą `ROLE_SUPER_ADMIN`.
*   **REQ-06**: Administrator widzi tabelę wszystkich zarejestrowanych kont z ich podstawowymi parametrami (ID, Nazwa, Status aktywności, Aktualne Role).
*   **REQ-07**: Administrator może ręcznie Aktywować lub Dezaktywować dowolne konto.
*   **REQ-08**: Administrator może zarządzać rolami konta z poziomu interfejsu (dodawanie/odbieranie np. `ROLE_USER`, `ROLE_QA`).
*   **REQ-09**: Administrator ma opcję zaznaczenia wymuszenia zmiany hasła (`mustChangePassword`) przy kolejnym logowaniu danego użytkownika.
*   **REQ-10**: Administrator może przeglądać audyt dla danego użytkownika (szczegóły w kolejnych etapach).

### 2.3. Wymuszenie Zmiany Hasła (User Side)
*   **REQ-11**: Jeśli flaga `mustChangePassword` jest włączona, po pomyślnym zalogowaniu (poprawny login i hasło) użytkownik nie zostaje dopuszczony do głównego menu aplikacji.
*   **REQ-12**: Użytkownik zostaje przekierowany do specjalnego widoku, który wymusza ustawienie nowego hasła (dwukrotne podanie).
*   **REQ-13**: Po udanej zmianie hasła, nowa wartość zostaje zahashowana, flaga `mustChangePassword` usunięta z bazy, a użytkownik pomyślnie zalogowany do ekranu głównego.

### 2.4. Informacje o Koncie, Edycja Profilu i Wylogowanie
*   **REQ-14**: W głównym oknie aplikacji znajduje się panel profilu wyświetlający dane zalogowanego użytkownika (imię, nazwisko, adres e-mail oraz przypisane role).
*   **REQ-15**: Z poziomu panelu bocznego, użytkownik ma możliwość przejścia do formularza "Edytuj profil", w którym może zmienić swoje dane osobowe (imię, nazwisko, telefon, adres e-mail) oraz zmienić swoje hasło uwierzytelniające.
*   **REQ-16**: Zmiana hasła przez użytkownika wymaga podania obecnego (starego) hasła ze względów bezpieczeństwa.
*   **REQ-17**: W panelu bocznym znajduje się dedykowany przycisk wylogowania, który czyści kontekst bezpieczeństwa i przenosi z powrotem do ekranu logowania.

### 2.5. Reset Hasła (Hasło Tymczasowe)
*   **REQ-18**: Użytkownik z poziomu okna logowania może zażądać resetu hasła podając swój adres e-mail.
*   **REQ-19**: Jeśli adres istnieje, system generuje losowe hasło tymczasowe, nadpisuje nim obecne hasło i ustawia flagę `mustChangePassword` na `true`.
*   **REQ-20**: Hasło tymczasowe jest wysyłane na podany adres e-mail za pośrednictwem serwisu SMTP Brevo.

### 2.6. Bezpieczeństwo Haseł i Blokada Konta
*   **REQ-21**: Hasło musi spełniać minimalne wymagania: co najmniej 8 znaków, wielka litera, mała litera, cyfra oraz znak specjalny. Walidacja obowiązuje przy rejestracji, wymuszeniu zmiany i edycji profilu.
*   **REQ-22**: Po 5 kolejnych nieudanych próbach logowania konto zostaje automatycznie zablokowane na 15 minut.
*   **REQ-23**: Administrator może ręcznie zablokować lub odblokować konto z poziomu Panelu Administratora.
*   **REQ-24**: System rejestruje datę ostatniego logowania każdego użytkownika.
*   **REQ-25**: Po każdej nieudanej próbie logowania przycisk logowania zostaje tymczasowo zablokowany z progresywnie rosnącym opóźnieniem (3s → 6s → 10s → 15s → 30s). Odliczanie jest widoczne na przycisku.
*   **REQ-26**: Gdy do blokady konta pozostały 3 lub mniej prób, system wyświetla informację o liczbie pozostałych prób.
*   **REQ-27**: Każda kluczowa funkcjonalność modułu zarządzania użytkownikami musi być objęta testami automatycznymi (Unit & Integration) zgodnie ze scenariuszami testowymi.
*   **REQ-28**: Historia Haseł: Użytkownik nie może zmienić hasła na żadne z ostatnich 5 haseł używanych w przeszłości. Walidacja ta dotyczy zarówno samodzielnej zmiany hasła, jak i wymuszonej zmiany.
*   **REQ-29**: Automatyczne Wylogowanie: System monitoruje aktywność użytkownika (mysz, klawiatura). Po 15 minutach (konfigurowalne) bezczynności, użytkownik zostaje automatycznie wylogowany i przekierowany do ekranu logowania.
*   **REQ-30**: Blokada Jednoczesnych Logowań: Użytkownik nie może być zalogowany na więcej niż jednej stacji roboczej jednocześnie. Przy próbie drugiego logowania system wyświetla stosowny komunikat.
*   **REQ-31**: Wygaśnięcie Konta: Jeśli użytkownik nie zaloguje się do systemu przez ponad 90 dni, jego konto zostaje automatycznie zablokowane. Odblokowanie wymaga interwencji administratora.
*   **REQ-32**: Rejestracja Logowań i Wylogowań: System zapisuje każde udane zalogowanie, nieudane próby oraz wylogowania w tabeli `access_logs` z metadanymi (czas, IP, username).
*   **REQ-33**: Raport Audytu (PDF/CSV): Administrator może wygenerować raport zmian i dostępów do pliku PDF (nieedytowalny, z podpisem systemowym) lub CSV.
*   **REQ-34**: Alarmy Bezpieczeństwa: Krytyczne błędy (np. próba obejścia zabezpieczeń) są logowane ze statusem alarmu w dzienniku zdarzeń.

## 3. Zgodność z GxP i Part 11
*   Brak przypisywania automatycznych ról oraz proces akceptacji wpisuje się w zasadę "Least Privilege" oraz "Segregation of Duties".
*   Wymuszenie zmiany hasła pozwala administratorom na bezpieczne przekazywanie poświadczeń.
*   **Audyt Zmian (Audit Trail)**: Wszystkie zmiany w statusie konta, danych osobowych oraz uprawnieniach (rolach) są logowane w bazie danych. Historia zmian jest dostępna dla administratora w dedykowanym widoku "Historia Audytu". Każdy wpis zawiera datę, autora zmiany oraz porównanie starej i nowej wartości pola.

## 4. Wykluczenia
*   Moduł definiowania pracowni i grup jest poza zakresem i zostanie wdrożony jako osobna funkcjonalność.

## 5. Zrealizowane elementy (wcześniej wykluczenia)
*   ~~W tym etapie nie przewiduje się automatycznych powiadomień e-mail (w tym powiadomień o akceptacji konta).~~ — **Zrealizowano.** Zintegrowano serwis SMTP Brevo. System wysyła powiadomienia e-mail w czterech scenariuszach: reset hasła, aktywacja konta, zablokowanie konta oraz rejestracja nowego użytkownika (alert do Super Adminów).
