# Analiza Biznesowa (BA) - Integracja Kopii Zapasowych z Chmurą Backblaze B2 (Cold Storage)

## 1. Cel Biznesowy i Kontekst Operacyjny
System **VCC Desktop App** przetwarza krytyczne dane pomiarowe i walidacyjne podlegające rygorom GxP. Przechowywanie kopii zapasowych bazy danych wyłącznie na lokalnym dysku stacji roboczej (lub w sieci lokalnej) wiąże się z ryzykami takimi jak:
*   Fizyczne zniszczenie sprzętu (pożar, zalanie, kradzież).
*   Zaszyfrowanie danych przez złośliwe oprogramowanie (Ransomware).
*   Przypadkowe lub celowe usunięcie plików kopii przez personel.

W celu spełnienia wymagań **GAMP 5** oraz **FDA 21 CFR Part 11** w zakresie bezpieczeństwa i niezmienności danych, planuje się rozszerzenie systemu o wysyłanie kopii zapasowych do zewnętrznego repozytorium chmurowego. Wybór padł na **Backblaze B2 Cloud Storage** (w modelu S3-Compatible Object Storage) ze względu na:
1.  **Darmową przestrzeń** do 10 GB na zawsze (całkowicie pokrywającą zapotrzebowanie aplikacji).
2.  **Mechanizm Object Lock (WORM - Write Once, Read Many)**, gwarantujący odporność kopii na modyfikację i usunięcie.

---

## 2. Wymagania Regulacyjne GxP i Bezpieczeństwa

### 2.1. Niezmienność Danych (Object Lock / WORM)
*   **REQ-B2-GxP-01:** Przesłane do Backblaze B2 pliki backupu muszą mieć włączoną ochronę **Object Lock** w trybie zgodności (*Compliance Mode*).
*   **REQ-B2-GxP-02:** Okres retencji (blokady usunięcia) plików w chmurze musi być ustawiony na **minimum 30 dni**. W tym czasie żaden użytkownik (w tym Administrator systemu) nie może zmodyfikować ani usunąć pliku kopii zapasowej. Zabezpiecza to dane przed skutkami ataków typu ransomware oraz błędów ludzkich.

### 2.2. Poufność i Szyfrowanie (Data Encryption)
*   **REQ-B2-GxP-03:** Transmisja danych pomiędzy aplikacją desktopową a serwerami Backblaze B2 musi odbywać się przez szyfrowany protokół **HTTPS (TLS 1.3)**.
*   **REQ-B2-GxP-04:** Pliki backupu po stronie chmury muszą być szyfrowane przy użyciu algorytmu **AES-256 (Server-Side Encryption - SSE-B2)**.

### 2.3. Rozliczalność i Audytowalność (Audit Trail)
*   **REQ-B2-GxP-05:** Każda próba przesłania backupu do Backblaze B2 (udana lub nieudana) musi być rejestrowana w systemowej tabeli `access_logs`.
*   **REQ-B2-GxP-06:** Dziennik zdarzeń w chmurze (Backblaze B2 Cloud Trail / B2 Reports) musi być aktywowany, aby audytorzy zewnętrzni mogli zweryfikować spójność operacji na plikach bezpośrednio w panelu administracyjnym chmury.

---

## 3. Wymagania Funkcjonalne

### 3.1. Przepływ Automatycznej Synchronizacji (Cloud Sync)
*   **REQ-B2-01:** System po pomyślnym wykonaniu lokalnego backupu (automatycznego lub cyklicznego) uruchamia w osobnym wątku asynchroniczne zadanie wysyłki pliku SQL do zasobnika (Bucket) Backblaze B2.
*   **REQ-B2-02:** W przypadku braku połączenia internetowego, system ponawia próbę wysłania pliku (Retry Mechanism) w odstępach 5-minutowych (maksymalnie 3 próby). Jeśli połączenie nadal nie zostanie nawiązane, system zapisuje ostrzeżenie w logach i przechodzi w stan oczekiwania na kolejny zaplanowany backup (nie blokując działania głównego programu).
*   **REQ-B2-03:** System wysyła do chmury wyłącznie pliki, które pomyślnie przeszły lokalną weryfikację spójności (kod wyjścia `mysqldump` = 0).

### 3.2. Zarządzanie Poświadczeniami (Credentials Management)
*   **REQ-B2-04:** Poświadczenia do API Backblaze B2 (Key ID, Application Key, Endpoint, Bucket Name) nie mogą być zapisane w kodzie aplikacji (hardcoded).
*   **REQ-B2-05:** Parametry te muszą być wczytywane z konfiguracji środowiskowej (`application.yml`) lub zmiennych systemowych systemu operacyjnego stacji roboczej.

### 3.3. Monitorowanie i Panel Administratora
*   **REQ-B2-06:** W zakładce "Kopie Zapasowe" Panelu Administratora (dla roli `ROLE_SUPER_ADMIN`) musi znajdować się sekcja statusu synchronizacji chmurowej:
    *   Status połączenia z Backblaze B2 (Połączono / Brak połączenia).
    *   Data i czas ostatniej pomyślnej wysyłki do chmury.
    *   Nazwa ostatnio zsynchronizowanego pliku.

---

## 4. Analiza Kosztów i Limitów (Backblaze B2)
Integracja opiera się na bezpłatnym pakiecie taryfowym Backblaze B2:

| Pozycja | Limit darmowy (B2 Free Tier) | Szacowane zużycie VCC (przy 350 KB / backup) | Koszt szacowany |
| :--- | :--- | :--- | :--- |
| **Przestrzeń (Storage)** | 10 GB | ~250 MB rocznie (przy backupach 2x dziennie i retencji 365 dni) | **$0.00** (zostaje 9.75 GB wolnego limitu) |
| **Transfer wychodzący (Egress)** | 3 GB / dzień | Bliski 0 (pobieranie plików następuje tylko w przypadku awarii) | **$0.00** |
| **Operacje klasy A (List, etc.)** | 2500 / dzień | ~10 / dzień | **$0.00** |
| **Operacje klasy B (Upload / Put)** | 2500 / dzień | 2-5 / dzień | **$0.00** |

---

## 5. Kamienie Milowe Przyszłej Implementacji
Jeżeli zapadnie decyzja o wdrożeniu tego modułu, implementacja przebiegać będzie według następujących kroków:
1.  **Konfiguracja Chmury:** Założenie konta firmowego Backblaze, utworzenie prywatnego zasobnika (Bucket) z włączoną opcją *Object Lock* oraz wygenerowanie kluczy API o ograniczonym dostępie (tylko zapis/odczyt do jednego bucketu).
2.  **Biblioteka Klienta:** Dodanie zależności `software.amazon.awssdk:s3` do pliku `pom.xml` (Backblaze B2 obsługuje standardowe API AWS S3 SDK).
3.  **Implementacja serwisu Java:** Utworzenie klasy `BackblazeB2SyncService` odpowiedzialnej za wysyłanie plików w osobnym wątku executor pool.
4.  **Aktualizacja GUI:** Dodanie wskaźników statusu chmury w panelu administratora.
5.  **Walidacja:** Przeprowadzenie testów symulacji odcięcia sieci oraz próby usunięcia zablokowanego obiektu (test odporności na modyfikacje).
