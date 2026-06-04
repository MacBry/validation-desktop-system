# Dokumentacja Techniczna: Urządzenia Wielokomorowe i Słownik Materiałów

Dokumentacja techniczna i implementacyjna zrefaktoryzowanego modułu ewidencji urządzeń chłodniczych (jedno- i wielokomorowych) w standardzie GxP/FDA 21 CFR Part 11 w aplikacji **VCC Desktop**.

---

## 1. Schemat Bazy Danych (Flyway Migration)

Struktura tabel została zaimplementowana w migracji **[V17__Cooling_Chamber_Refactoring.sql](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/resources/db/migration/V17__Cooling_Chamber_Refactoring.sql)**. Przebudowano pierwotny model jednokomorowy w model relacyjny **1:N (jedno urządzenie chłodnicze posiada wiele komór)**.

### 1.1. Tabele Operacyjne
#### Tabela `cooling_devices` (Fizyczne urządzenia / Aktywa)
Reprezentuje fizyczne urządzenie chłodnicze stojące w laboratorium (np. dwukomorowa chłodziarko-zamrażarka). Wszystkie parametry metrologiczne i operacyjne zostały przeniesione do tabeli komór podrzędnych.
*   `id` (BIGINT, AUTO_INCREMENT): PK
*   `inventory_number` (VARCHAR(50)): Unikalny numer inwentarzowy fizycznego urządzenia, index, not null
*   `name` (VARCHAR(200)): Nazwa własna/model urządzenia, not null
*   `status` (VARCHAR(30)): Status urządzenia — `ACTIVE` (Aktywne) | `INACTIVE` (Nieaktywne) | `DECOMMISSIONED` (Wyłączone z użytku), not null, default: `ACTIVE` *(dodane migracją V23)*
*   `department_id` (BIGINT): FK do `departments(id)`, not null
*   `laboratory_id` (BIGINT): FK do `laboratories(id)`, nullable

#### Tabela `cooling_chambers` (Komory chłodnicze)
Przechowuje metrologiczne parametry poszczególnych komór wchodzących w skład urządzenia chłodniczego.
*   `id` (BIGINT, AUTO_INCREMENT): PK
*   `cooling_device_id` (BIGINT): FK do `cooling_devices(id)`, not null, ON DELETE CASCADE
*   `chamber_name` (VARCHAR(100)): Nazwa komory (np. "Komora Chłodziarki", "Komora Zamrażarki"), not null
*   `chamber_type` (VARCHAR(30)): Typ komory (Enum, np. `FRIDGE`, `FREEZER`, `LOW_TEMP_FREEZER`), not null
*   `material_type_id` (BIGINT): FK do `material_types(id)`, słownik przechowywanych substancji, nullable
*   `min_operating_temp` (DOUBLE): Dolny zakres temperatury roboczej komory
*   `max_operating_temp` (DOUBLE): Górny zakres temperatury roboczej komory
*   `volume_m3` (DOUBLE): Pojemność komory w metrach sześciennych (m³)
*   `volume_category` (VARCHAR(10)): Kategoria kubatury PDA TR-64 (Enum: `SMALL`, `MEDIUM`, `LARGE`), not null

#### Tabela `material_types` (Słownik materiałów)
Przechowuje słownik kategorii materiałów (np. krew pełna, szczepionki, osocze).
*   `id` (BIGINT, AUTO_INCREMENT): PK
*   `name` (VARCHAR(100)): Unikalna nazwa handlowa/klasyfikacyjna, not null
*   `description` (VARCHAR(500)): Opis normatywny
*   `min_storage_temp` (DOUBLE): Dolna granica temperatury bezpiecznego przechowywania
*   `max_storage_temp` (DOUBLE): Górna granica temperatury bezpiecznego przechowywania
*   `activation_energy` (DECIMAL(10, 4)): Energia aktywacji ($E_a$ w kJ/mol) używana do obliczeń MKT
*   `standard_source` (VARCHAR(255)): Standard źródłowy (np. Farmakopea)
*   `application` (VARCHAR(255)): Przeznaczenie aplikacyjne
*   `active` (BOOLEAN): Status aktywności (default: `TRUE`)

---

### 1.2. Spójność Audytowa (Hibernate Envers)

Zgodnie z rygorystycznymi wymaganiami **FDA 21 CFR Part 11 / GMP / GxP**, każda operacja zapisu, modyfikacji i usunięcia fizycznego urządzenia lub jego komór jest automatycznie rejestrowana w tabelach audytowych:
*   `cooling_devices_aud` — audyt parametrów ogólnych (nazwa, numer inwentarzowy, dział, pracownia, **status** — kolumna dodana migracją V23).
*   `cooling_chambers_aud` — audyt parametrów technicznych i metrologicznych na poziomie pojedynczych komór.

Obie tabele powiązane są kluczem obcym z systemową tabelą `revinfo` (konfigurowaną przez `UserRevisionListener`), rejestrującą dokładnego autora zmiany (`modified_by`) i znacznik czasowy rewizji.

---

## 2. Implementacja Warstwy Modelu (Domain Layer)

### 2.1. Model Encji `CoolingDevice.java`
Zaimplementowano relację `@OneToMany` z kaskadowością pełną i automatycznym usuwaniem sierot (`orphanRemoval = true`), wymuszając zachłanne dociąganie kolekcji komór (`FetchType.EAGER`), co eliminuje problemy `LazyInitializationException` podczas renderowania widoków oraz generowania audytów:
```java
@OneToMany(mappedBy = "coolingDevice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
@Audited
private List<CoolingChamber> chambers = new ArrayList<>();
```

Dla zapewnienia integralności powiązań dwukierunkowych w pamięci, klasa udostępnia metody pomocnicze:
```java
public void addChamber(CoolingChamber chamber) {
    chambers.add(chamber);
    chamber.setCoolingDevice(this);
}

public void removeChamber(CoolingChamber chamber) {
    chambers.remove(chamber);
    chamber.setCoolingDevice(null);
}
```

### 2.2. Model Encji `CoolingChamber.java`
Reprezentuje pojedynczą komorę z wbudowaną logiką kalkulacyjną **PDA TR-64** na podstawie objętości:
*   **Klasa S** (pojemność $\le 2\text{ m}^3$) – mapowanie do 9 punktów pomiarowych.
*   **Klasa M** (pojemność od 2 do $20\text{ m}^3$) – mapowanie do 15 punktów pomiarowych.
*   **Klasa L** (pojemność $> 20\text{ m}^3$) – mapowanie do 27 punktów pomiarowych.

---

## 3. Logika Biznesowa i Algorytm Audytu (Service Layer)

### 3.1. Przetwarzanie i Zapis (`CoolingDeviceServiceImpl.java`)
Podczas zapisu fizycznego urządzenia serwis w pętli przechodzi przez wszystkie powiązane komory:
1. Ustawia powiązanie dwukierunkowe.
2. Automatycznie wylicza kategorię kubatury PDA TR-64 (`chamber.updateVolumeCategoryFromVolume()`).
3. Zapisuje kaskadowo całą strukturę transakcyjnie.

### 3.2. Skonsolidowany Audit Trail (`AuditService.java`)
Aby zapewnić czytelną dla audytora formę historii, system podczas generowania historii fizycznego urządzenia (`CoolingDevice`) wykonuje skonsolidowany algorytm różnicowy (diff):
1. **Różnice urządzenia fizycznego:** Porównuje pola ogólne (Nazwa urządzenia, Dział, Pracownia).
2. **Różnice kolekcji komór:** Dynamicznie porównuje kolekcję komór w rewizji $N$ oraz $N-1$:
    *   **Dodanie komory:** Zwraca wpis o dodaniu nowej komory o zadanej nazwie.
    *   **Usunięcie komory:** Zwraca wpis o usunięciu komory.
    *   **Zmiana parametrów komory:** Generuje precyzyjne rekordy różnicowe z prefiksem komory, np. pole: `Komora (Zamrażarka) - Temp Min`, wartość stara: `-20.0`, wartość nowa: `-80.0`.

---

## 4. Architektura Interfejsu Użytkownika (JavaFX UI)

### 4.1. Architektura Warstwy Kontrolera (Controller Layer Architecture - od maja 2026)

Warstwa prezentacji została refaktoryzowana z monolitycznego `CoolingDeviceController` (378 linii) na wyspecjalizowane klasy pomocnicze UI (helpers) oraz czysty serwis Spring Security:

#### **CoolingDeviceTableHelper** (107 linii) [Helper UI]
- **Odpowiedzialność:** Konfiguracja tabel i filtrowanie danych (klasa pomocnicza w pakiecie `com.mac.bry.desktop.controller.helper`)
- **Metody:**
  - `setupMasterTable()` - konfiguracja kolumn tabeli głównej (numer, nazwa, dział, liczba komór)
  - `setupDetailTable()` - konfiguracja kolumn tabeli szczegółów komór
  - `setupFilters()` - inicjalizacja pól wyszukiwania i kombo-boxów
  - `applyFilters()` - predykaty filtrowania po tekście i typie komory
- **Logika:** Wiązania wartości kolumn (CellValueFactory), zarządzanie filtrowaniem z użyciem `FilteredList`

#### **CoolingDeviceCellFactoryHelper** (84 linie) [Helper UI]
- **Odpowiedzialność:** Tworzenie niestandardowych komórek tabeli z stylizacją (klasa pomocnicza w pakiecie `com.mac.bry.desktop.controller.helper`)
- **Metody:**
  - `setupVolumeCategoryCell()` - renderowanie kategorii kubatury PDA TR-64 jako kolorowe tagi (Klasa S/M/L)
  - `setupRevalidationStatusCell()` - renderowanie statusu walidacji GxP z ikonami (✅/❌/⚠️). 
- **Zależności:** Całkowity brak zależności od Springa. Używa interfejsu funkcyjnego `Function<Long, String> statusProvider` do przekazywania statusów z `TestoRevalidationService`.

#### **CoolingDeviceSecurityService** (22 linie) [Spring Service]
- **Odpowiedzialność:** Logiczna kontrola dostępu oznaczona jako `@Service` (bez jakichkolwiek importów `javafx.*`)
- **Metody:**
  - `isUserAdmin()` - sprawdzenie uprawnień administracyjnych (ROLE_SUPER_ADMIN lub ROLE_DEPT_ADMIN)
- **Zależności:** SecurityContextHolder

#### **CoolingDeviceController (Refactored)** (231 linii, wcześniej 378)
- **Odpowiedzialność:** Orchestracja, obsługa akcji użytkownika, zarządzanie dialogami
- **Metody CRUD:**
  - `handleAddNewDevice()` - otwieranie kreatora nowego urządzenia
  - `handleEditDevice(CoolingDevice)` - edycja istniejącego urządzenia
  - `handleViewDevice(CoolingDevice)` - przeglądanie szczegółów (read-only)
  - `handleDeleteDevice(CoolingDevice)` - usuwanie urządzenia z potwierdzeniem
  - `handleShowAudit(CoolingDevice)` - otwieranie ścieżki audytu
- **Pozostałe metody:**
  - `initialize()` - delegacja inicjalizacji do Helperów UI i Security Service
  - `setupActionsColumn()` - konfiguracja kolumny akcji (pozostaje w kontrolerze)
  - `setupSelectionListener()` - obsługa zaznaczenia wiersza
  - `handleRefresh()` - odświeżenie danych z bazy
- **Zależności:** CoolingDeviceService, TestoRevalidationService, CoolingDeviceSecurityService, ApplicationContext
- **Korzyści:** Zmniejszenie z 378 do 231 linii (-39%), czysta separacja logiki Springa od wątku UI JavaFX.

**Korzyści Refaktoryzacji:**
- ✅ Zmniejszenie kontrolera z 378 do 231 linii (-39%)
- ✅ Logika konfiguracji tabel i filtrów wyekstrahowana do Helpera UI
- ✅ Niestandardowe style komórek izolowane w Helperze komórek z użyciem wtrysku funkcyjnego
- ✅ Całkowite wyczyszczenie warstwy serwisowej Springa z bibliotek `javafx.*`
- ✅ Kontroler skupia się wyłącznie na orchestracji, CRUD i dialogach
- ✅ Wszystkie serwisy biznesowe mogą być uruchamiane i testowane w środowisku headless (bez GUI)

### 4.2. Główny Widok Ewidencji (Master-Detail)
Główny ekran (`cooling_devices.fxml` / `CoolingDeviceController.java`) został zrealizowany w układzie **Master-Detail** z użyciem kontrolki `SplitPane`:
*   **Górny panel (Master):** Tabela fizycznych urządzeń z kolumnami: *Numer Inwentarzowy*, *Nazwa urządzenia*, *Dział / Pracownia*, *Liczba komór* oraz kolumną akcji.
*   **Dolny panel (Detail):** Reaguje na kliknięcie wiersza w tabeli górnej. Dynamicznie wczytuje powiązaną kolekcję komór, prezentując ich szczegółowe parametry: *Nazwę komory*, *Typ chłodniczy*, *Zakres temperatur*, *Objętość*, *Klasyfikację PDA TR-64* (w formie estetycznego Tag-Badge) oraz *Przechowywany materiał*.

### 4.3. Kreator / Dialog Zarządzania
Zarządzanie urządzeniem (`cooling_device_dialog.fxml` / `CoolingDeviceDialogController.java`) umożliwia edycję metadanych urządzenia oraz dynamiczne zarządzanie listą komór (Dodaj/Edytuj/Usuń) w pamięci operacyjnej (poprzez `ObservableList`). 
*   **Zasada Walidacji Urządzenia:** Zapis urządzenia bez zdefiniowanej przynajmniej jednej komory chłodniczej jest **zablokowany** na poziomie interfejsu oraz warstwy serwisu (wyświetlany jest elegancki komunikat o błędzie).
*   **Dialog Edycji Komory:** Wywołanie przycisku "+ Dodaj" lub "Edytuj" otwiera wyskakujący modal konfiguracji pojedynczej komory (`CoolingChamberDialogController`), który w czasie rzeczywistym waliduje zakresy temperatur i oblicza klasyfikację PDA TR-64.

---

## 5. Status Walidacji i Kwalifikacji GxP Komory

Status kwalifikacji komory chłodniczej jest wyliczany w locie (m.in. na dashboardzie oraz w widoku procedur) na podstawie przypisanych do niej historycznych serii pomiarowych (`ThermoMeasurementSeries`).

### 5.1. Reguły Wyliczania Statusu Kwalifikacji:
1.  **Brak kwalifikacji:**
    *   Komora chłodnicza nie posiada w bazie danych żadnej zarejestrowanej serii pomiarowej (`ThermoMeasurementSeries`).
2.  **Ostrzeżenie GxP (Wymagana rewalidacja / brak spójności):**
    *   Komora posiada serie pomiarowe, ale przynajmniej jeden z rejestratorów użytych w tych seriach **nie posiada ważnego świadectwa wzorcowania** (certyfikat wygasł lub nie został wprowadzony w bazie).
3.  **Zatwierdzone GxP (Status prawidłowy):**
    *   Komora posiada powiązane serie pomiarowe, a wszystkie użyte rejestratory w tych seriach posiadają **aktualne i ważne świadectwa wzorcowania** (`Calibration.isValid()`).

Algorytm ten gwarantuje natychmiastowe wykrycie uchybień jakościowych w nadzorze temperatury bez konieczności ręcznego przeszukiwania archiwów wzorcowań przez audytora.
