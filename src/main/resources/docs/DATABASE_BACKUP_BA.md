# Analiza Biznesowa (BA) - System Kopia Zapasowych i Bezpiecznych Migracji Bazy Danych

## 1. Cel Biznesowy i Kontekst GxP
Głównym celem biznesowym wdrożenia jest zabezpieczenie integralności, spójności i dostępności danych pomiarowych i walidacyjnych w systemie **VCC Desktop App**. System przechowuje krytyczne dane GxP (Good Practice) dotyczące kwalifikacji komór chłodniczych i rewalidacji okresowej urządzeń. Utrata lub niezamierzona modyfikacja tych danych w wyniku nieudanej migracji bazy danych na środowisku produkcyjnym stanowi krytyczne ryzyko biznesowe i walidacyjne.

Zgodnie z regulacjami **FDA 21 CFR Part 11** oraz zaleceniami **GAMP 5**:
*   **Integralność danych (Data Integrity):** Każda zmiana struktury bazy danych musi być w pełni audytowalna, powtarzalna i kontrolowana.
*   **Ciągłość działania i odtwarzanie (Disaster Recovery):** System musi posiadać mechanizmy pozwalające na natychmiastowe odtworzenie spójnego stanu danych sprzed awarii.
*   **Zabezpieczenie przed uszkodzeniem (Fail-Safe):** Wprowadzenie zmian w strukturze bazy danych (migracje Flyway) musi być poprzedzone bezwarunkowym i udanym wykonaniem kopii zapasowej. W przypadku błędu backupu, migracja nie może się rozpocząć.

---

## 2. Analiza Obecnego Stanu (As-Is)
W obecnej architekturze systemu zaimplementowano dwa kluczowe elementy:
1.  **Flyway Migrations:** Zarządzanie wersjami schematu bazy danych (pliki SQL w `db/migration`). Migracje są uruchamiane automatycznie przy starcie aplikacji Spring Boot (`FlywayConfig.java`).
2.  **DatabaseBackupService.java:** Klasa realizująca cykliczny backup bazy danych (co 12 godzin) za pomocą zewnętrznego narzędzia `mysqldump` oraz czyszczenie archiwów starszych niż 14 dni.

**Zidentyfikowany krytyczny punkt ryzyka (Gap):**
Proces migracji Flyway i automatycznego backupu nie są ze sobą zsynchronizowane. Jeśli przy starcie aplikacji Flyway wykryje oczekujące migracje schematu i przystąpi do ich wykonywania, a migracja zakończy się błędem (lub spowoduje niepożądane modyfikacje danych produkcyjnych), system nie posiada gwarancji, że tuż przed tym momentem został wykonany aktualny punkt przywracania. Ręczne odtwarzanie z backupu sprzed kilku godzin wiąże się z bezpowrotną utratą danych wprowadzonych od tamtej pory.

---

## 3. Wymagania Funkcjonalne (To-Be)

### 3.1. Automatyczny Backup przed Migracją (Pre-migration Backup)
*   **REQ-BKP-01:** System przed rozpoczęciem jakichkolwiek migracji Flyway musi sprawdzić, czy w katalogu migracji znajdują się nowe, oczekujące skrypty SQL (status `PENDING`).
*   **REQ-BKP-02:** Jeżeli wykryto oczekujące migracje, system musi automatycznie wywołać procedurę kopii zapasowej bazy danych do dedykowanego katalogu (np. `backups/db`).
*   **REQ-BKP-03:** Jeśli nie ma oczekujących migracji, aplikacja powinna pominąć ten krok i uruchomić się standardowo (unikamy spowolnienia startu i niepotrzebnego generowania pustych backupów przy każdym uruchomieniu).

### 3.2. Blokada Bezpieczeństwa (Fail-Safe Lock)
*   **REQ-BKP-04:** Kopia zapasowa przed migracją musi zakończyć się statusem **SUKCES** (kod wyjścia `mysqldump` równy 0).
*   **REQ-BKP-05:** W przypadku jakiegokolwiek błędu podczas tworzenia kopii zapasowej (np. brak narzędzia `mysqldump` w systemie, brak wolnego miejsca na dysku, błędne poświadczenia bazy danych, błąd I/O):
    *   Proces migracji Flyway musi zostać **bezwzględnie zablokowany**.
    *   Kontekst Spring Boot musi zostać **zatrzymany** z czytelnym komunikatem błędu w logach systemowych.
    *   Aplikacja nie może dopuścić do uruchomienia schematu bazodanowego ani interfejsu użytkownika JavaFX.

### 3.3. Dziennik Audytu (Audit Trail)
*   **REQ-BKP-06:** Każda próba wykonania backupu (automatycznego, cyklicznego lub wyzwolonego ręcznie) musi zostać odnotowana w bazie danych w tabeli logów audytowych (`access_logs`).
*   **REQ-BKP-07:** Zapis logu musi zawierać:
    *   Identyfikator zdarzenia / Akcję (np. `DB_BACKUP_AUTO`, `DB_BACKUP_MANUAL`).
    *   Nazwę pliku wynikowego lub szczegóły techniczne błędu w razie niepowodzenia.
    *   Wskazanie autora zdarzenia (dla automatycznych procesów: `SYSTEM`).

### 3.4. Funkcjonalności Panelu Administratora (UI)
*   **REQ-BKP-08:** Użytkownik z uprawnieniami `ROLE_SUPER_ADMIN` musi mieć dostęp do sekcji zarządzania bazą danych w Panelu Administratora.
*   **REQ-BKP-09:** Administrator musi widzieć historię ostatnich kopii zapasowych (lista plików w katalogu backupów, ich rozmiar, data utworzenia).
*   **REQ-BKP-10:** Administrator musi mieć możliwość ręcznego uruchomienia kopii zapasowej "na żądanie" za pomocą przycisku `Stwórz Kopię Zapasową` (uruchamianego asynchronicznie, by nie zamrozić interfejsu JavaFX).

---

## 4. Wykluczenia z zakresu
*   **Automatyczne przywracanie bazy (Restore) z poziomu GUI:** Proces odtwarzania bazy danych (Restore) jest operacją krytyczną i z punktu widzenia bezpieczeństwa GxP powinien być wykonywany wyłącznie przez wykwalifikowany personel IT/Administratorów baz danych bezpośrednio na serwerze (np. przy użyciu narzędzia CLI `mysql`), a nie za pomocą jednego kliknięcia w aplikacji desktopowej. W interfejsie graficznym zostanie umieszczona instrukcja postępowania w przypadku konieczności odtworzenia bazy.
*   **Obsługa backupów w chmurze:** Kopia zapasowa jest realizowana lokalnie lub na zamontowany zasób sieciowy wskazany w pliku konfiguracyjnym. Integracja z zewnętrznymi dostawcami chmurowymi (AWS S3, Azure Blob) nie jest częścią tego etapu.

---

## 5. Macierz Ryzyk i Działań Korygujących

| Identyfikator Ryzyka | Opis Ryzyka | Wpływ na System | Działanie Korygujące / Zabezpieczenie |
| :--- | :--- | :--- | :--- |
| **RSK-BKP-01** | Brak zainstalowanego narzędzia `mysqldump` na stacji roboczej klienta. | Brak możliwości wykonania kopii zapasowej, co przy nowej wersji aplikacji zablokuje jej start. | Walidacja dostępności narzędzia podczas startu. Jeśli brak oczekujących migracji, system przechodzi w stan ostrzeżenia w logach. Jeśli migracje są oczekujące, następuje bezpieczne zatrzymanie startu z jasną instrukcją instalacji narzędzia. |
| **RSK-BKP-02** | Brak wolnego miejsca na dysku na zapisanie pliku SQL. | Uszkodzony (niepełny) plik kopii zapasowej. | System przed migracją weryfikuje poprawność zapisu i kod wyjścia `mysqldump`. Jeśli jest niezerowy, plik jest usuwany, a migracja zostaje przerwana. |
| **RSK-BKP-03** | Przepełnienie dysku przez setki starych plików backupu. | Awaria systemu operacyjnego, brak możliwości zapisu logów. | Mechanizm retencji (Backup Retention) uruchamiany przy każdym backupie, usuwający pliki starsze niż 14 dni. |
| **RSK-BKP-04** | Dostęp nieuprawnionego użytkownika do plików `.sql` zawierających wrażliwe dane. | Naruszenie poufności danych (21 CFR Part 11). | Katalog backupów musi być zabezpieczony uprawnieniami systemu operacyjnego (dostęp tylko dla konta uruchamiającego usługę / aplikację). |
