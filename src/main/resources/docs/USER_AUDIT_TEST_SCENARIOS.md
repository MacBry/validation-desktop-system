# Scenariusze Testowe - Moduł Audytu Użytkownika (21 CFR Part 11)

## 1. Testy Jednostkowe (Unit Tests)
Celem jest weryfikacja logiki transformacji danych i porównywania pól w `AuditService`.

| ID | Scenariusz | Oczekiwany Rezultat |
|:---|:---|:---|
| UT-AUD-01 | Porównanie użytkowników z identycznymi danymi | Lista zmian (`UserAuditDto`) powinna być pusta. |
| UT-AUD-02 | Porównanie użytkowników z różnym adresem e-mail | Lista zmian powinna zawierać wpis dla pola "Email" ze starą i nową wartością. |
| UT-AUD-03 | Porównanie użytkowników z różnymi rolami | Lista zmian powinna zawierać wpis dla pola "Uprawnienia" z listą nazw ról. |
| UT-AUD-04 | Mapowanie timestampu z rewizji | Metoda powinna poprawnie konwertować long (ms) na `LocalDateTime`. |

## 2. Testy Integracyjne (Integration Tests)
Weryfikacja współpracy z bazą danych i Hibernate Envers.

| ID | Scenariusz | Oczekiwany Rezultat |
|:---|:---|:---|
| IT-AUD-01 | Tworzenie nowej rewizji przy zmianie statusu | Po zmianie `enabled` na `true`, w tabeli `users_aud` powinien pojawić się rekord, a `AuditService` powinien go zwrócić jako MODYFIKACJA. |
| IT-AUD-02 | Śledzenie autora zmiany | Po zapisaniu zmiany przez użytkownika "admin1", pole `modifiedBy` w audycie musi zawierać "admin1". |
| IT-AUD-03 | Audyt Many-to-Many (Role) | Dodanie roli `ROLE_QA` musi wygenerować rewizję, a historia audytu musi wykazać zmianę w polu "Uprawnienia". |
| IT-AUD-04 | Spójność danych historycznych | Pobranie historii dla usuniętego/starego użytkownika musi zwracać poprawne dane bazowe (zgodnie z migracją baseline V8). |

## 3. Testy Systemowe / UI (Manual & Automated)
Weryfikacja wyświetlania danych w interfejsie JavaFX.

| ID | Scenariusz | Oczekiwany Rezultat |
|:---|:---|:---|
| SYS-AUD-01 | Wyświetlanie okna audytu | Kliknięcie "Historia Audytu" w Panelu Admina otwiera modalne okno z poprawnymi danymi wybranego użytkownika. |
| SYS-AUD-02 | Sortowanie historii | Najnowsze zmiany (najwyższe numery rewizji) muszą znajdować się na samej górze tabeli. |
| SYS-AUD-03 | Czytelność ról | Role w tabeli muszą być wyświetlane jako nazwy (np. `[ROLE_USER, ROLE_QA]`), a nie jako surowe ID czy obiekty. |
| SYS-AUD-04 | Obsługa pustej historii | Dla nowo utworzonego użytkownika (bez zmian) widoczny jest tylko wpis "UTWORZENIE". |

## 4. Testy Historii Haseł (Password History)

| ID | Scenariusz | Oczekiwany Rezultat |
|:---|:---|:---|
| UT-PASS-01 | Próba zmiany na hasło obecne | System wyrzuca wyjątek/błąd: "Hasło zostało już użyte". |
| IT-PASS-01 | Zmiana hasła 5-krotna | W tabeli `user_password_history` powinno znajdować się 5 różnych hashów. |
| IT-PASS-02 | Próba powrotu do hasła sprzed 3 rewizji | Błąd walidacji - hasło znajduje się w Top 5. |
| IT-PASS-03 | Powrót do hasła sprzed 6 rewizji | Sukces - hasło wypadło z Top 5. |
| SYS-PASS-01 | Wyświetlanie błędu w UI (Profil) | Po wpisaniu starego hasła jako nowego, etykieta błędu wyświetla komunikat z serwisu. |

## 5. Testy Automatycznego Wylogowania (Inactivity Timeout)

| ID | Scenariusz | Oczekiwany Rezultat |
|:---|:---|:---|
| SYS-TOUT-01 | Bezczynność 15 min | System automatycznie przechodzi do `login.fxml`, tytuł okna zmienia się na "Zaloguj się ponownie". |
| SYS-TOUT-02 | Ruch myszą resetuje czas | Przy ustawionym timeout na 1 min, ruch myszą w 50. sekundzie powoduje, że wylogowanie następuje po kolejnej pełnej minucie (łącznie 1:50). |
| SYS-TOUT-03 | Konfiguracja w locie | Zmiana w `application.yml` na 1 min i restart aplikacji – system wylogowuje po 1 minucie. |

## 6. Testy Blokady Jednoczesnych Logowań (Concurrent Login)

| ID | Scenariusz | Oczekiwany Rezultat |
|:---|:---|:---|
| SYS-CONC-01 | Logowanie na dwóch stacjach | Po zalogowaniu na Komputerze A, próba logowania na te same poświadczenia na Komputerze B kończy się błędem: "Użytkownik jest już zalogowany". |
| SYS-CONC-02 | Wylogowanie zwalnia blokadę | Po kliknięciu "Wyloguj" na Komputerze A, logowanie na Komputerze B przebiega pomyślnie. |
| SYS-CONC-03 | Obsługa awarii (Timeout sesji) | Po "zabiciu" procesu aplikacji na Komputerze A, logowanie na Komputerze B jest możliwe po 30 minutach (czas wygasania sesji w bazie). |

## 7. Testy Wygasania Konta (Account Expiration)

| ID | Scenariusz | Oczekiwany Rezultat |
|:---|:---|:---|
| SYS-EXP-01 | Logowanie po 91 dniach | Użytkownik, który ostatnio logował się 91 dni temu, przy próbie logowania otrzymuje komunikat o wygaśnięciu konta. Konto w bazie zostaje zablokowane (`enabled=false`). |
| SYS-EXP-02 | Odblokowanie przez Admina | Po odblokowaniu konta przez administratora w Panelu Admina, użytkownik może się ponownie zalogować. |

## 8. Testy Audytu i Raportowania (Reporting & Audit)

| ID | Scenariusz | Oczekiwany Rezultat |
|:---|:---|:---|
| SYS-AUD-01 | Rejestracja logowania | Po poprawnym zalogowaniu, w tabeli `access_logs` (widocznej w Panelu Admina) pojawia się wpis `LOGIN_SUCCESS`. |
| SYS-AUD-02 | Rejestracja błędnego hasła | Po wpisaniu złego hasła, w `access_logs` pojawia się `LOGIN_FAILED` z informacją o pozostałych próbach. |
| SYS-AUD-03 | Eksport PDF | Po kliknięciu "Eksport PDF" w historii użytkownika, system generuje plik `.pdf` z poprawnymi danymi rewizji. |
| SYS-AUD-04 | Eksport CSV | Po kliknięciu "Eksport CSV", system generuje plik `.csv` otwieralny w Excelu. |

## 9. Testy Backupu Bazy (Database Backup)

| ID | Scenariusz | Oczekiwany Rezultat |
|:---|:---|:---|
| SYS-BKP-01 | Automatyczny backup | Po uruchomieniu aplikacji, w folderze `backups/db` pojawia się plik `.sql`. Kolejny plik pojawia się po 12h. |
| SYS-BKP-02 | Rotacja plików | Po wygenerowaniu backupów z 15 różnych dni, plik z 1. dnia zostaje usunięty (limit retention-days=14). |
| SYS-BKP-03 | Błąd mysqldump | W przypadku braku narzędzia `mysqldump` w systemie, w logach aplikacji pojawia się błąd, ale aplikacja działa dalej stabilnie. |
