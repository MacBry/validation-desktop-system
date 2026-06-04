# Validation System - Desktop Edition

System do walidacji procesów przechowywania oraz warunków transportu (mapowanie temperatur i wilgotności). Aplikacja oparta na JavaFX i Spring Boot z interfejsem AtlantaFX.

Aplikacja została przygotowana do bezpiecznej publikacji w repozytorium GitHub (usunięto z kodu wrażliwe dane, hasła oraz klucze API).

## ⚙️ Wymagania i Uruchomienie

Przed uruchomieniem aplikacji należy skonfigurować parametry połączenia z bazą danych oraz dane serwera SMTP przy użyciu pliku środowiskowego `.env`.

### 1. Klonowanie i Konfiguracja Środowiska
1. Skopiuj plik szablonu konfiguracyjnego [.env.example](.env.example) jako `.env` w głównym katalogu projektu (`validation-desktop/`):
   ```bash
   cp .env.example .env
   ```
2. Otwórz plik `.env` i uzupełnij go o własne dane logowania do bazy MySQL, ścieżkę do narzędzia `mysqldump` oraz klucze SMTP (np. Brevo):
   * **`DB_PASSWORD`**: Hasło do Twojej lokalnej bazy MySQL.
   * **`MYSQL_DUMP_PATH`**: Pełna ścieżka do narzędzia `mysqldump.exe` (potrzebne do generowania kopii zapasowych bazy danych z poziomu panelu administratora).
   * **`SMTP_PASSWORD`**: Hasło / Klucz API do SMTP w celu wysyłki raportów i powiadomień.

> [!WARNING]
> Plik `.env` zawiera wrażliwe dane i **nie powinien** nigdy trafić do systemu kontroli wersji. Został on automatycznie dodany do pliku `.gitignore`.

### 2. Domyślne Dane Administratora (Wymóg GxP)
Migracje bazy danych (Flyway) automatycznie tworzą domyślne konto administratora systemu:
* **Login:** `admin`
* **Hasło:** `admin`

> [!IMPORTANT]
> **Zalecenie GxP:** Ze względów bezpieczeństwa należy **bezzwłocznie zmienić domyślne hasło administratora** przy pierwszym logowaniu do systemu. Aplikacja automatycznie wymusi zmianę hasła ze względu na politykę bezpieczeństwa (Inactivity timeout & Password history).

### 3. Kompilacja i Uruchomienie
Aplikację można skompilować i uruchomić za pomocą Mavena:
```bash
# Kompilacja i testy
mvn clean test

# Uruchomienie aplikacji
mvn spring-boot:run
```

## 🛠️ Stack Technologiczny
* **UI**: JavaFX 21 + AtlantaFX (motyw PrimerLight)
* **Backend**: Spring Boot 3.x (Spring Data JPA)
* **Baza danych**: MySQL 8.x + H2 (do testów jednostkowych/integracyjnych)
* **Migracje**: Flyway
* **Raportowanie**: Integracja z logerami temperatury Testo 184
