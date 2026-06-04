# Specyfikacja Techniczna - Struktura Organizacyjna

## 1. Konfiguracja YAML (`application.yml`)

Dane jednostki głównej będą przechowywane w sekcji `app.organization`:

```yaml
app:
  organization:
    name: "Regionalne Centrum Krwiodawstwa i Krwiolecznictwa w Poznaniu"
    short-name: "RCKiK Poznań"
    abbreviation: "RCK"
    address: "ul. Marcelińska 44, 60-354 Poznań"
```

## 2. Model Danych (JPA Entities)

### 2.1. Encja `Department`
- `id` (Long, PK)
- `name` (String, length=255, nullable=false)
- `abbreviation` (String, length=20, unique=true, nullable=false)
- `description` (String, columnDefinition="TEXT")
- Adnotacja: `@Audited` (Hibernate Envers)

### 2.2. Encja `Laboratory`
- `id` (Long, PK)
- `name` (String, length=255, nullable=false)
- `abbreviation` (String, length=20, unique=true, nullable=false)
- `department_id` (FK -> `Department.id`, nullable=false)
- Adnotacja: `@Audited` (Hibernate Envers)

### 2.3. Rozszerzenie encji `User`
Dodanie powiązania:
- `laboratory_id` (FK -> `Laboratory.id`, nullable=true)
- `department_id` (FK -> `Department.id`, nullable=true) - jako fallback, jeśli użytkownik jest przypisany do całego działu.

## 3. Schemat Bazy Danych (Flyway)

Nowa migracja `V13__Organization_Schema.sql`:
1. Utworzenie tabeli `departments` i `departments_aud`.
2. Utworzenie tabeli `laboratories` i `laboratories_aud`.
3. Dodanie kolumn `department_id` oraz `laboratory_id` do tabeli `users` oraz `users_aud`.

## 4. Warstwa Serwisowa

- `OrganizationService`: Serwis do zarządzania działami i pracowniami.
- `OrganizationConfig`: Klasa `@ConfigurationProperties` do mapowania danych z YAML.

## 5. Integracja z UI

1. **Panel Administratora**: Dodanie nowej zakładki "Struktura" do zarządzania listą działów i pracowni.
2. **Edycja Użytkownika**: Dodanie pól ComboBox do wyboru Działu i Pracowni.
3. **Filtrowanie**: Możliwość filtrowania użytkowników po dziale/pracowni.
## 6. Audyt (Hibernate Envers)
- Pola `department` i `laboratory` w encji `User` są objęte audytem (`@Audited`).
- `AuditService`: Zmodyfikowano metodę `compareFields`, aby porównywała historyczne wartości działu i pracowni.
- Wyświetlanie: Historyczne nazwy jednostek są prezentowane w oknie "Historia Audytu" użytkownika.
