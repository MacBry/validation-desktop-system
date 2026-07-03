# Dokumentacja Projektu Validation Desktop

Ten folder służy do przechowywania dokumentacji wewnętrznej, instrukcji użytkownika (User Manuals) oraz dokumentacji architektonicznej (np. wymagań GAMP 5) dla aplikacji w wersji Desktop.

## Struktura Dokumentacji
Zalecamy podział na:
- **Techniczna** - instrukcje budowy, opisy baz danych.
- **Biznesowa** - wymagania, procesy (np. OQ/PQ).
- **Walidacyjna** - plany testów, specyfikacje wymagań.

## 🚀 Najnowsze Modernizacje (Maj 2026)

### Refaktoryzacja Architektury (God Classes Elimination & GUI Decoupling)
Przeprowadzono kompleksową refaktoryzację trzech głównych klas o nadmiernych odpowiedzialnościach, stosując wzorce Service, DTO oraz pomocników UI (Helpers). Dodatkowo, całkowicie odsprzężono bibliotekę JavaFX od warstwy `@Service` Springa, umożliwiając niezależne testowanie jednostkowe i integracyjne w trybie headless.

**Całkowity Wpływ:** 1,131 → 615 linii w refaktoryzowanych klasach bazowych (-45%), 8 nowych serwisów + 3 pomocników UI, 7 DTOs, 100% backward compatibility, wszystkie 75 testów przechodzi pomyślnie.

📁 **[Pełna dokumentacja refaktoryzacji w katalogu ./refactor/](refactor/README.md)**

Trzy fazy refaktoryzacji:

- **Phase 1: UserService** (316 → 138 linii, -56%)
  - Rozbicie na 4 serwisy: UserManagementService, UserPasswordService, UserAuthenticationService, UserAccountService
  - Facade Pattern dla zachowania 100% kompatybilności wstecznej
  - 27 testów jednostkowych przechodzi pomyślnie

- **Phase 2: DashboardController** (437 → 246 linii, -44%)
  - Zastosowanie 4 nowych serwisów: DashboardStatisticsService, DashboardChartService, AccessLogService, DashboardSecurityService
  - Stworzenie pomocnika UI: AccessLogsTableHelper (całkowite odsprzężenie JavaFX z serwisów)
  - 7 nowych DTO do transportu danych

- **Phase 3: CoolingDeviceController** (378 → 231 linii, -39%)
  - Nowy serwis logiczny: CoolingDeviceSecurityService (bez zależności JavaFX)
  - Stworzenie pomocników UI: CoolingDeviceTableHelper, CoolingDeviceCellFactoryHelper (wstrzykiwanie funkcyjne statusów rewalidacji)

**Szczegóły techniczne w dokumentach architektonicznych poniżej:**

### Główne Dokumenty Techniczne
- **[Specyfikacja Modernizacji 2026](SYSTEM_MODERNIZATION_2026.md)** - Szczegółowy opis zmian w architekturze, technologii (Java 21 LTS), dynamicznym dashboardzie z wykresami kołowymi oraz refaktoryzacji komponentów UI.
- **[Analiza Biznesowa: Urządzenia Chłodnicze](COOLING_DEVICE_BA.md)** - Wymagania metrologiczne (PDA TR-64), kubatury, klasy czujników oraz słownik kategorii przechowywanych materiałów.
- **[Specyfikacja Techniczna: Urządzenia Chłodnicze](COOLING_DEVICE_TECHNICAL_SPEC.md)** - Architektura danych, integracja z Hibernate Envers, migracja Flyway V16, architektura kontrolera oraz serwisy UI.
- **[Specyfikacja Techniczna: Zarządzanie Użytkownikami](USER_MANAGEMENT_TECHNICAL_SPEC.md)** - Architektura bezpieczeństwa, serwisy użytkowników (po refaktoryzacji) oraz kontrola dostępu.
- **[Klasyfikacja Ekskursji: Analiza Biznesowa](BA_PROPAGATION_AWARE_EXCURSION_CLASSIFIER.md)** - Klasyfikacja przestrzenna anomalii termicznych na podstawie wektora propagacji ciepła w siatce czujników.
- **[Klasyfikacja Ekskursji: Specyfikacja Implementacji](IMPL_PROPAGATION_AWARE_EXCURSION_CLASSIFIER.md)** - Ważona regresja opóźnień reakcji czujników i walidacja krzyżowa z konfiguracją źródła nawiewu.
- **[Klasyfikacja Ekskursji: Plan i Scenariusze Testowe](TEST_PROPAGATION_AWARE_EXCURSION_CLASSIFIER.md)** - Scenariusze testowe weryfikacji algorytmu regresji dla różnych geometrii propagacji.
