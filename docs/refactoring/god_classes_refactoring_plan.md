# Plan Refaktoryzacji "God Classes"

Dokument ten zawiera strategię refaktoryzacji największych klas w projekcie `validation-desktop`, ze szczególnym uwzględnieniem zachowania wstecznej kompatybilności (zabezpieczenie działających funkcjonalności i unikanie natychmiastowego psucia warstwy widoku FXML).

## 1. `TestoRevalidationController` (~1131 linii)

**Problem:** Klasa steruje całym cyklem życia kreatora rewalidacji, zajmując się inicjalizacją UI, obsługą zdarzeń, mapowaniem danych do tabel (Summary, Metrological, Stats) oraz wywoływaniem usług statystycznych.

**Strategia Refaktoryzacji:**
1. **Rozbicie na mniejsze kontrolery widoku (Sub-controllers)**
   - W pliku `testo_revalidation.fxml` każda większa zakładka (np. *Analiza Metrologiczna*, *Testowanie Hipotez*) może zostać wydzielona do osobnego pliku `.fxml` i dołączona za pomocą `<fx:include source="tab_metrological.fxml" fx:id="metrologicalTab" />`.
   - Główny `TestoRevalidationController` będzie posiadał wstrzyknięte instancje sub-kontrolerów (np. `@FXML private MetrologicalTabController metrologicalTabController;`) i będzie pełnił jedynie rolę koordynatora (Fasady) przekazującego im kontekst (np. obiekt `RevalidationSession`).
   - *Zgodność wsteczna:* Refaktoryzację można przeprowadzać zakładka po zakładce, nie psując całości.
2. **Ekstrakcja logiki tabel do klas `TableHelper` / `TableBinder`**
   - W dużej mierze już rozpoczęto ten proces (np. `TestoRevalidationTableHelper`). Należy przenieść tam *wszystkie* definicje `setCellValueFactory` oraz obsługę zdarzeń kliknięć w komórki.
3. **Delegacja logiki domenowej (Event Handlers)**
   - Operacje takie jak walidacja formularza przed zapisem, przygotowanie danych do PDF/Word powinny być delegowane do `RevalidationOrchestratorService`, pozostawiając w kontrolerze jedynie reagowanie na błędy (wyświetlanie alertów).

---

## 2. `TestoRevalidationWordService` (~676 linii) oraz `RevalidationReportPdfRenderer` (~626 linii)

**Problem:** Klasy te to monolityczne generatory raportów, które tworzą całe dokumenty w jednej długiej sekwencji metod. Łączą logikę biznesową z formatowaniem niskopoziomowym (np. ustawianie marginesów, tworzenie komórek tabel).

**Strategia Refaktoryzacji:**
1. **Wzorzec Budowniczego (Builder) / Strategii (Strategy) dla Sekcji**
   - Zamiast jednej klasy, utwórz klasę `RevalidationWordReportBuilder`, która przyjmuje interfejsy `DocumentSectionRenderer`.
   - Każda logiczna sekcja dokumentu otrzymuje własną klasę (tzw. Single Responsibility Principle).
     - `WordHeaderSectionRenderer`
     - `WordMetrologicalTableRenderer`
     - `WordStatisticalSummaryRenderer`
     - `WordHypothesisTestingRenderer`
2. **Klasa Kontekstu Raportu**
   - Utworzenie obiektu `ReportContext`, w którym znajdą się dane takie jak `RevalidationSession`, `CompanyInfo`, `DeviceConfig`. Context ten będzie podawany do każdego `SectionRenderer`.
   - *Zgodność wsteczna:* Publiczny kontrakt usługi (np. `generateWordReport(Session session)`) pozostaje identyczny. Zmienia się jedynie implementacja wewnętrzna usługi (delegate do Buildera).

---

## 3. `TestoReadController` (~529 linii)

**Problem:** Klasa odpowiadająca za wczytywanie plików z rejestratorów Testo i mapowanie ich na obiekty. Obsługuje zarówno UI (paski postępu, okna dialogowe), jak i parsowanie danych CSV/Excel.

**Strategia Refaktoryzacji:**
1. **Całkowite wydzielenie Parsowania (Reader Service)**
   - Utworzenie bezstanowych usług `TestoCsvReaderService` oraz `TestoExcelReaderService`, które zwracają listę gotowych serii pomiarowych, a jedynym parametrem wejściowym jest `File` lub `InputStream`.
2. **Wydzielenie logiki mapowania pozycji (Channel Mapping)**
   - Klasa `PositionMapperService` powinna zająć się logiką dopasowywania wczytanych kanałów do siatki (GridPosition).
3. **Zadania w tle (Concurrency/Task API)**
   - Wczytywanie plików i ich mapowanie powinno być zaimplementowane poprzez klasę rozszerzającą `javafx.concurrent.Task`, z której kontroler jedynie odczytuje `progressProperty()` i `messageProperty()`. Zminimalizuje to kod związany z aktualizacją UI i `Platform.runLater()`.
   - *Zgodność wsteczna:* Dotychczasowe wywołania z `MainController` do `TestoReadController` pozostają nienaruszone. Interfejs wizualny się nie zmienia.

---

## Podsumowanie Kolejności Działań (Roadmapa)

1. **Faza 1 (Szybkie zwycięstwa / Niski Ryzyko):** 
   - Wydzielenie logiki konfiguracji wszystkich tabel i wykresów z `TestoRevalidationController` do odpowiednich `Helperów`.
2. **Faza 2 (Renderowanie Raportów):** 
   - Refaktoryzacja klas Word/PDF na podejście sekcyjne (`SectionRenderer`). Ułatwi to w przyszłości dodawanie nowych wymagań (np. raportowanie nowych norm ISO).
3. **Faza 3 (Modularne UI):** 
   - Cięcie `testo_revalidation.fxml` na mniejsze sub-pliki za pomocą `<fx:include>`. Utworzenie dedykowanych małych kontrolerów (np. `HypothesisTabController`).

Powyższe zmiany gwarantują zwiększenie czytelności kodu i testowalności przy zerowym ryzyku uszkodzenia aktualnych metod, ponieważ interfejsy zewnętrzne klas pozostaną z zachowaniem wstecznej kompatybilności.
