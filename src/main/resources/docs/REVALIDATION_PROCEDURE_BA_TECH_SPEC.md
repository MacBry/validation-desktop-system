# Specyfikacja Biznesowa i Techniczna: Procedura Rewalidacji Komór Chłodniczych GxP
## Kreator Mapowania Rozkładu Temperatur (Multi-Sensor Validation Wizard)

Niniejszy dokument opisuje architekturę biznesową (BA), szczegółowe przypadki użycia, model stanów oraz specyfikację techniczną implementacji nowego modułu kreatora rewalidacji w aplikacji desktopowej VCC (JavaFX / Spring Boot). Zawiera wbudowane wsparcie dla zaawansowanej symulacji sprzętowo-bazodanowej w warunkach braku podłączonej kołyski USB.

---

## 1. Analiza Biznesowa (BA)

### 1.1. Kontekst Walidacyjnego GxP
W farmacji i laboratoriach medycznych urządzenia chłodnicze (lodówki, zamrażarki, inkubatory) podlegają okresowej rewalidacji termicznej. Głównym celem jest udowodnienie, że w każdym punkcie komory chłodniczej temperatura mieści się w dopuszczalnym zakresie (np. $+2.0^\circ\text{C}$ do $+8.0^\circ\text{C}$ dla lodówek aptecznych). 

Mapowanie wykonuje się za pomocą zestawu niezależnych rejestratorów temperatury (np. Testo 174T, Testo 184T) rozmieszczonych w komorze chłodniczej według określonej geometrii trójwymiarowej. Zgodnie z wytycznymi PDA Technical Report No. 64 oraz normą DIN 12880, standardowy układ pomiarowy dla komór chłodniczych wykorzystuje **maksymalnie 8 sensorów** rozmieszczonych na dwóch poziomach.

### 1.2. Model Siatki Pozycjonowania (8 Narożników Komory)
Układ sensorów wewnątrz komory odwzorowuje dwupoziomową siatkę narożnikową:
*   **Poziom Górny (Góra - 2x2):**
    1.  *Góra - Przód-Lewy (G-PL)*
    2.  *Góra - Przód-Prawy (G-PP)*
    3.  *Góra - Tył-Lewy (G-TL)*
    4.  *Góra - Tył-Prawy (G-TP)*
*   **Poziom Dolny (Dół - 2x2):**
    5.  *Dół - Przód-Lewy (D-PL)*
    6.  *Dół - Przód-Prawy (D-PP)*
    7.  *Dół - Tył-Lewy (D-TL)*
    8.  *Dół - Tył-Prawy (D-TP)*

Metrolog fizycznie umieszcza rejestratory w komorze w narożnikach (wykorzystuje maksymalnie 8 dostępnych pozycji), uruchamia rejestrację, a po zakończeniu czasu próbkowania zbiera urządzenia i podłącza je po kolei do komputera celem zgrania danych.

### 1.3. Krok po Kroku (Atomic User Journey)
Nowy moduł działa jako wieloetapowy kreator (Wizard), prowadzący użytkownika za rękę w sposób uniemożliwiający popełnienie błędu jakościowego:

1.  **ETAP 1: Wybór Celu Walidacji**
    *   Metrolog wyszukuje i wybiera z bazy danych urządzenie chłodnicze (`CoolingDevice`) na podstawie numeru inwentarzowego.
    *   Wskazuje konkretną komorę chłodniczą (`CoolingChamber`) podlegającą badaniu.
    *   System wczytuje z bazy parametry komory: min/max temperaturę pracy, objętość oraz klasyfikację PDA TR-64, sugerując zalecaną liczbę punktów.
2.  **ETAP 2: Przypisanie Sensorów (Interaktywny Panel Siatki)**
    *   System wyświetla graficzny schemat komory (dwie siatki 2x2).
    *   Użytkownik klika na wybraną komórkę siatki (np. *Góra - Przód-Lewy*).
    *   Panel importu oferuje **dwa tryby wgrania serii pomiarowej**:

        **Tryb 1 — Testo 174T (USB):**
        Wkłada rejestrator Testo 174T do kołyski i klika **"🔌 Odczytaj z kołyski USB (174T)"**.
        *   System komunikuje się z kołyską przez Python USB Bridge.
        *   **Zintegrowany Silnik Metrologicznej Symulacji:** W przypadku braku fizycznego sprzętu, system wyświetla zapytanie o przejście w tryb symulacji metrologicznej lodówki.
        *   **Automatyczna Rejestracja w Bazie Danych:** Dla symulowanej pozycji `X`, silnik generuje S/N `SN-174-T00X-SIM`, tworząc pełną ewidencję w bazie danych.

        **Tryb 2 — Testo 184T (Import PDF):**
        Klika **"📄 Importuj raport PDF (184T)"** i wybiera plik raportu PDF wygenerowanego przez oprogramowanie Testo.
        *   System uruchamia mostek Python (`testo_184_reader_bridge.py`), który parsuje plik PDF i zwraca serie pomiarowe w standardowym JSON.
        *   **Rygor GxP — Blokada S/N:** Numer seryjny odczytany z PDF musi figurować w ewidencji VCC (`ThermoRecorder`). W przeciwnym razie import zostaje **zablokowany** z komunikatem błędu.
        *   **Uwaga:** Format PDF Testo 184T nie zawiera informacji o stanie baterii — pole "Stan baterii" wyświetla wartość **N/D (import PDF)**.

    *   Komórka na siatce zmienia kolor na **zielony**, wyświetlając model i S/N rejestratora oraz status wzorcowania.
    *   Metrolog powtarza krok dla kolejnych rejestratorów (maksymalnie 8 razy).
3.  **ETAP 3: Podgląd Skompilowanej Sesji**
    *   Wizualna lista podsumowująca wszystkie wgrane kanały, numery świadectw wzorcowania, daty ich ważności oraz liczbę odczytanych próbek.
    *   System wykonuje testy spójności (czy wszystkie serie mają identyczny interwał próbkowania (3h), liczbę próbek (40) oraz ten sam czas startu).
4.  **ETAP 4: Zintegrowana Kompilacja PDF**
    *   Po kliknięciu **"📊 Generuj Raport PDF"** system scala dane z bazy z danymi zgranymi z USB / Symulacji w jeden spójny dokument walidacyjny.

---

## 2. Specyfikacja Techniczna

### 2.1. Model Stanu Sesji (In-Memory State)
Stan sesji rewalidacji w trakcie pracy kreatora przechowywany jest w pamięci operacyjnej w dedykowanej klasie modelowej:

```java
package com.mac.bry.desktop.model;

import lombok.Data;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class RevalidationSession {
    private CoolingDevice coolingDevice;
    private CoolingChamber coolingChamber;
    private Map<GridPosition, PositionData> assignedPositions = new HashMap<>();

    public enum GridPosition {
        TOP_FRONT_LEFT("Góra - Przód-Lewy"),
        TOP_FRONT_RIGHT("Góra - Przód-Prawy"),
        TOP_BACK_LEFT("Góra - Tył-Lewy"),
        TOP_BACK_RIGHT("Góra - Tył-Prawy"),
        BOTTOM_FRONT_LEFT("Dół - Przód-Lewy"),
        BOTTOM_FRONT_RIGHT("Dół - Przód-Prawy"),
        BOTTOM_BACK_LEFT("Dół - Tył-Lewy"),
        BOTTOM_BACK_RIGHT("Dół - Tył-Prawy");

        private final String label;
        GridPosition(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Data
    public static class PositionData {
        private String serialNumber;
        private String model;
        private ThermoRecorder recorder; // Rekord z ewidencji w bazie danych
        private Calibration latestCalibration; // Aktywne świadectwo wzorcowania z bazy
        private List<ThermoMeasurementPoint> measurements; // Seria pomiarowa (np. 40 punktów)
    }
}
```

### 2.2. Układ Graficzny UI (JavaFX / FXML)
Nowy widok `testo_revalidation.fxml` korzysta z biblioteki stylów systemowych, zapewniając estetykę klasy premium.

*   **Wizard Controller (`TestoRevalidationController.java`):**
    *   Nawigacja oparta na ukrytym `TabPane`. Przejścia "Dalej" / "Wstecz" są obsługiwane programistycznie z walidacją poprawności danych każdego kroku.
    *   **Siatka mapowania (GridPane):** Wykorzystuje komponenty JavaFX `Button` stylizowane dynamicznie za pomocą pseudo-klas CSS (`:active`, `:success`).
    *   **Asynchroniczność:** Odczyt USB korzysta z wątku `javafx.concurrent.Task` i paska postępu, co zapewnia płynność UI.

### 2.3. Wyszukiwanie Metadanych w Bazie Danych (Integracja JPA)
W momencie wgrania danych przez USB (lub wygenerowania w trybie symulacji), kontroler automatycznie odpytuje serwisy Spring Boot:
```java
// Wyszukanie rejestratora po numerze seryjnym pobranym z USB / Symulacji
Optional<ThermoRecorder> recorderOpt = thermoRecorderService.findBySerialNumber(serialNo);
if (recorderOpt.isPresent()) {
    ThermoRecorder recorder = recorderOpt.get();
    Calibration latestCal = recorder.getLatestCalibration();
    
    // Walidacja daty ważności świadectwa wzorcowania
    if (latestCal != null && latestCal.isValid()) {
        positionData.setLatestCalibration(latestCal);
        positionData.setRecorder(recorder);
    } else {
        // Ostrzeżenie GxP o braku lub wygaśnięciu świadectwa
        showGxpWarning("Rejestrator posiada przeterminowane świadectwo wzorcowania!");
    }
} else {
    // W trybie rzeczywistym: Blokada GxP
    showGxpError("Rejestrator o S/N: " + serialNo + " nie figuruje w bazie danych!");
}
```

### 2.4. Struktura Zintegrowanego Raportu GxP PDF (OpenPDF / iText)
Kompilacja raportu tworzy jeden nienaruszalny dokument PDF:
1.  **Strona Tytułowa (Metryka Badania):** Zawiera nazwę i numer inwentarzowy urządzenia chłodniczego, typ komory, kubaturę, nazwisko metrologa oraz datę i czas wygenerowania raportu.
2.  **Strona Konfiguracyjna (Metrologia Sensorów):** Tabela przedstawiająca, które rejestratory (model, S/N) zostały przypisane do konkretnych fizycznych pozycji siatki, wraz z numerami świadectw wzorcowania i datami ważności. Jest to kluczowy dowód audytowy spójności metrologicznej.
3.  **Tabela Zintegrowana (Wielostronicowy Wydruk Wyników):** Kolumny tabeli (do 8 kolumn temperatur pomiarowych):
    $$\text{Lp.} \quad | \quad \text{Czas Pomiaru (Lokalny)} \quad | \quad T_1 (^\circ\text{C}) \quad | \quad T_2 \quad | \quad T_3 \quad | \dots | \quad T_8 (^\circ\text{C})$$
    *   Nagłówek tabeli powtarza się u góry każdej strony wydruku (`dataTable.setHeaderRows(1)`).
    *   Wartości są zsynchronizowane wiersz po wierszu na podstawie indeksu czasowego pomiaru.
4.  **Suma Kontrolna SHA-256 (Nienaruszalność GxP):** Na ostatniej stronie generowana jest zbiorcza kryptograficzna suma kontrolna SHA-256 wyliczona z kompletnej macierzy pomiarowej wszystkich wgranych kanałów. Zapobiega to jakimkolwiek próbom fałszowania lub modyfikacji dokumentu.

### 2.5. Model Bazodanowy i Trwałość Danych (Persistence)
W celu zachowania pełnej spójności oraz możliwości rekonstrukcji historycznych badań rewalidacyjnych bez polegania na heurystyce czasu importu, encja `ThermoMeasurementSeries` została rozbudowana o dedykowane pola trwałego przechowywania metadanych sesji:
*   **`revalidation_group_id`** (`revalidation_group_id` VARCHAR(50) NULL): Łączy do 8 serii pomiarowych w jeden nienaruszalny komplet (sesję) rewalidacji komory. Identyfikator ten jest generowany asynchronicznie jako UUID w momencie startu kreatora.
*   **`grid_position`** (`grid_position` VARCHAR(50) NULL): Zapisuje bezpośrednie powiązanie serii pomiarowej z fizyczną pozycją na trójwymiarowej siatce komory chłodniczej (wartość typu enum `GridPosition`, np. `TOP_FRONT_LEFT`).

#### Zapewnienie Kompatybilności Wstecznej (Fallback)
Dla starszych danych (importowanych przed wprowadzeniem zmian), u których wartości te są równe `NULL`:
1.  **Grupowanie na liście procedur:** System grupuje odczyty po parze `coolingChamber.id` + `firstMeasurementTimeLocal`.
2.  **Odtwarzanie pozycji na siatce:** Pozycje sensorów przypisywane są sekwencyjnie według domyślnej kolejności (indeks `modulo 8`).

---

## 3. Etapy Realizacji (Krok po Kroku)

### **Krok 1: Spring Service & State Management**
*   Implementacja modelu `RevalidationSession`.
*   Przygotowanie `TestoRevalidationService` do zarządzania stanem sesji i integracji z JPA.

### **Krok 2: JavaFX FXML & Controller**
*   Stworzenie interfejsu `testo_revalidation.fxml` z interaktywnymi dwupoziomowymi przyciskami pozycjonowania (8 narożników).
*   Zintegrowanie mechanizmu asynchronicznego pobierania danych z USB / Symulacji dla konkretnej pozycji na siatce.

### **Krok 3: PDF Multi-Channel Compiler Engine**
*   Implementacja klasy `TestoRevalidationPdfService` generującej zintegrowany, wielostronicowy dokument GxP PDF.
