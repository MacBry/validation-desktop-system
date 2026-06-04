# Specyfikacja Modernizacji Systemu VCC Desktop (Maja 2026)

Dokument podsumowuje ostatnio zaimplementowane usprawnienia technologiczne, wizualne i funkcjonalne w aplikacji **Validation Desktop** (Wersja 2.15.0).

---

## 1. Migracja Technologiczna na Java 21 LTS

W celu zapewnienia najwyższej wydajności, bezpieczeństwa oraz dostępu do najnowszych API (takich jak Virtual Threads, ulepszone Pattern Matching), platforma została przeniesiona na najnowszą wersję LTS:
*   **Wersja Java:** Podniesiona z Java 17 do **Java 21 LTS (Temurin)**.
*   **Wersja JavaFX:** Zaktualizowana do wersji **21.0.2** w celu zapewnienia pełnej kompatybilności.
*   **Narzędzie Deweloperskie (`Switch-Java`):** Skonfigurowano skrypty PowerShell automatyzujące natychmiastowe przełączanie zmiennych środowiskowych (`Switch-Java21` / `Switch-Java17`) na maszynie deweloperskiej.

---

## 2. Dynamiczny Dashboard - Architektura (od maja 2026)

### 2.0. Refaktoryzacja Architektury DashboardController

Warstwa interfejsu dashboardu została refaktoryzowana z monolitycznego `DashboardController` (437 linii) na wyspecjalizowane serwisy i obiekty transferu danych (DTO):

#### **Obiekty Transferu Danych (DTOs)**
- **RecorderStatistics** - statystyki rejestratorów (aktywne, w kalibracji, nieaktywne)
- **CalibrationStatistics** - status kalibracji (ważne, wygasające, przeterminowane)
- **UserStatistics** - status kont użytkowników (aktywne, zablokowane)
- **DeviceStatistics** - statystyki urządzeń chłodniczych (komory, status walidacji)
- **UsbStatistics** - operacje USB (odczyty, programowanie)
- **DashboardStatistics** - agregat wszystkich statystyk (pojedyncze zapytanie)

#### **DashboardStatisticsService** (152 linie)
- **Odpowiedzialność:** Obliczanie wszystkich statystyk dashboardu
- **Metody:**
  - `calculateAllStatistics()` - pojedynczy punkt wejścia dla wszystkich obliczeń
  - `calculateRecorderStatistics()` - statystyki rejestratorów
  - `calculateCalibrationStatistics()` - status ważności kalibracji (okno 30 dni)
  - `calculateUserStatistics()` - status kont użytkowników
  - `calculateDeviceAndChamberStatistics()` - walidacja GxP komór
  - `calculateUsbOperationStatistics()` - liczby odczytów i programowań
- **Logika:** Wszystkie operacje @Transactional(readOnly = true)

#### **DashboardChartService** (94 linie)
- **Odpowiedzialność:** Budowanie wszystkich wykresów
- **Metody:**
  - `buildRecordersPieChart()` - wykres kołowy statusów rejestratorów
  - `buildCalibrationsPieChart()` - wykres kołowy statusów kalibracji
  - `buildUsersPieChart()` - wykres kołowy statusów kont
  - `buildUsbActivityChart()` - wykres słupkowy operacji USB (7 dni)
- **Dane:** Historyczne dane z AccessLog, filtrowanie po datach

#### **AccessLogsTableService** (78 linii)
- **Odpowiedzialność:** Konfiguracja tabeli i formatowanie logów dostępu
- **Metody:**
  - `setupAccessLogsTable()` - wiązanie kolumn i kolumny czasopisu
  - `loadAndFormatLogs()` - ładowanie ostatnich logów (domyślnie 5)
- **Stylizacja:** Kolorowe wiersze - fiolet (USB_PROGRAMMING), zielony (USB_READING), czerwony (FAILED)

#### **DashboardSecurityService** (76 linii)
- **Odpowiedzialność:** Kontrola dostępu opartej na rolach dla dashboardu
- **Metody:**
  - `isUserAdmin()` - sprawdzenie roli SUPER_ADMIN/DEPT_ADMIN
  - `applyRoleBasedVisibility()` - ukrycie elementów dla użytkowników bez uprawnień
- **Logika:** Dla non-admin: ukrywanie kart użytkowników/struktury, reconfiguracja layoutu
- **Zależności:** SecurityContextHolder

#### **DashboardController (Refactored)** (~120 linii, wcześniej 437)
- **Odpowiedzialność:** Orchestracja, inicjalizacja widoku
- **Metody:**
  - `initialize()` - delegacja do setupHeader(), setupAndLoadStatistics(), table/security setup
  - `setupHeader()` - powitanie, role, data, czas
  - `setupAndLoadStatistics()` - pobranie statystyk i aktualizacja UI
  - `updateStatisticsLabels()` - mapowanie DTO na etykiety
  - `buildCharts()` - budowanie i ustawianie danych wykresów
  - `updateGxPAlert()` - wyświetlenie alertu jeśli są problemy GxP
- **Zależności:** DashboardStatisticsService, DashboardChartService, AccessLogsTableService, DashboardSecurityService
- **Korzyści:** Zmniejszenie z 437 do ~120 linii (-73%), czysty kod, pełna delegacja

**Korzyści Refaktoryzacji:**
- ✅ Zmniejszenie kontrolera z 437 do ~120 linii (-73%)
- ✅ Obliczenia statystyk wyekstrahowane do dedykowanego serwisu
- ✅ Budowanie wykresów centralizowane w serwisie
- ✅ Formatowanie tabel oddzielone w serwisie
- ✅ Kontrola dostępu centralizowana w Security Service
- ✅ DTO zapewnia typową bezpieczność transferu danych
- ✅ Każdy serwis testowany niezależnie

## 2. Dynamiczny Dashboard (Pulpit Menedżerski)

Całkowicie przeprojektowano ekran startowy aplikacji, przekształcając go z prostego tekstu powitalnego w zaawansowane centrum monitorowania procesów:
*   **KPI Cards (Dwuwierszowy układ 4x2 z rolami):**
    *   **Urządzenia Pomiarowe:** Statystyki rejestratorów ogółem oraz aktywnych i w kalibracji.
    *   **Status Metrologiczny:** Liczba przeterminowanych i wygasających świadectw wzorcowania rejestratorów.
    *   **Urządzenia Chłodnicze:** Łączna liczba obiektów w spisie (lodówek, zamrażarek, cieplarek).
    *   **Komory i Walidacja GxP:** Ogólna liczba komór wraz z podziałem na statusy: Zatwierdzone GxP (ważna kalibracja rejestratorów), Ostrzeżenia GxP (wygasła kalibracja w sesji) oraz Brak kwalifikacji.
    *   **Konta Użytkowników:** Liczba kont aktywnych i zablokowanych (Locked) - widoczne dla administratorów.
    *   **Struktura Organizacyjna:** Łączna liczba działów oraz pracowni/laboratoriów - widoczne dla administratorów.
    *   **Operacje Testo USB:** Łączna statystyka odczytanych i zaprogramowanych urządzeń USB. Karta ta automatycznie rozciąga się na 2 kolumny (dla administratorów) lub na pełne 4 kolumny (100% szerokości) w dolnym wierszu dla zwykłych użytkowników, zachowując doskonałą symetrię UI.
*   **Wykresy i Trendy (Wykresy Kołowe oraz Wykres Słupkowy USB):**
    *   **Trzy Wykresy Kołowe (Pie Charts):** Stosunek Statusów Rejestratorów, Podział Metrologiczny Wzorcowań oraz Status Kont (dla administratorów).
    *   **Wykres Słupkowy USB (Bar Chart):** Pokazuje trendy aktywności USB w podziale na odczyty i programowanie z ostatnich 7 dni.
    *   *Stylizacja:* Autorskie, kontrastujące kolory (Emerald Green, Amber Yellow, Slate Grey, Rose Red, Royal Purple) wdrożone w `style.css`.
*   **Moduł Powiadomień GxP (Alert Box):**
    *   Czerwony pasek ostrzegawczy pod powitaniem. Automatycznie alarmuje, jeśli w systemie wykryto przeterminowane/wygasające wzorcowania rejestratorów lub komory chłodnicze ze statusem "Ostrzeżenie GxP".
*   **Usprawniona Nawigacja (ScrollPane):**
    *   Cały widok dashboardu został opakowany w pionowy kontener `ScrollPane` (`fitToWidth="true"`, `hbarPolicy="NEVER"`). Dzięki temu użytkownicy na dowolnej rozdzielczości ekranu mogą płynnie przewinąć dashboard, aby dotrzeć do sekcji **Ostatnie 5 zdarzeń w systemie**.

---

## 3. Nowy Formularz Logowania (Premium Redesign)

Wdrożono nowoczesny standard UI/UX poprawiający odbiór aplikacji przez użytkownika przy pierwszym kontakcie:
*   **Floating Card Layout:** Centralna, zaokrąglona karta formularza o stałych wymiarach **450x560px** z głębokim, aksamitnym cieniem.
*   **Gradientowe Tło:** Tło głównego okna to zaawansowany gradient przejścia od jasnego popielu do stalowego błękitu (`#f8fafc` do `#cbd5e1`).
*   **Ujednolicone Kontrolki:** Dynamiczny wskaźnik blokady konta (cooldown) w pełni spójny ze stylem AtlantaFX.

---

## 4. Dodawanie Użytkownika z Panelu Administratora

Uzupełniono brakujące ogniwo w module bezpieczeństwa, wprowadzając kreator nowego użytkownika:
*   **Przycisk Akcji:** Dedykowany przycisk `+ Nowy Użytkownik` w prawym górnym rogu panelu użytkowników.
*   **Kreator Użytkownika (`user_dialog.fxml`):**
    *   Zaawansowana walidacja unikalności loginu/emaila.
    *   Dynamiczny indykator siły hasła.
    *   Wybór struktury organizacyjnej (Działy/Pracownie).
    *   Bezpieczne szyfrowanie hasła w locie (`BCryptPasswordEncoder`).

---

## 5. Solid Facelift (Ujednolicenie Spójności UI)

Wyeliminowano niespójności graficzne w całej aplikacji:
*   **Przyciski Akcji:** Zamiana wszystkich przycisków ramkowych (outlined) w tabelach (rejestratory, historia wzorcowań, punkty pomiarowe) na przyciski w pełni wypełnione kolorem (solid).
*   **Przycisk PDF:** Skan świadectwa w kolumnie "Skan PDF" został zmieniony z małej, czerwonej, pustej kropki na estetyczny, solidny przycisk z wyraźnym tekstem **"Podgląd PDF"** na czerwonym tle.
