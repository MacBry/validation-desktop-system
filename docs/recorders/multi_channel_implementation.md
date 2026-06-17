# Plan Implementacji (Implementation Plan) – Rejestratory wielokanałowe

Poniższy dokument opisuje zmiany programistyczne i architektoniczne konieczne do obsłużenia rejestratorów wielokanałowych z różnymi certyfikatami wzorcowania przypisanymi per kanał.

## 1. Modyfikacje Bazy Danych (Data Layer)

### 1.1 Nowa encja: `ThermoRecorderModel`
- Tabela bazodanowa: `thermo_recorder_models`
- Zastępuje trzymanie nazw modeli w zwykłym polu tekstowym. Umożliwia łatwą kontrolę przez administratora.
- **Pola**:
  - `id` (Long, PK, Auto Increment)
  - `name` (String, Unique, NonNull) np. "Testo 176 T4"
  - `channelCount` (Integer, NonNull, default 1)
  - `defaultResolution` (BigDecimal, NonNull)
  - `active` (Boolean) - ukrywanie wycofanych modeli.

### 1.2 Modyfikacja `ThermoRecorder`
- Zmiana definicji `String model` na relację `@ManyToOne(fetch = FetchType.EAGER)` do `ThermoRecorderModel`.
- Modyfikacja `getResolution()` – pobieranie precyzji domyślnej bezpośrednio ze skojarzonego modelu sprzętu, zamiast switch-case z hardkodowanych wartości.

### 1.3 Modyfikacja `Calibration`
- Dodanie pola `@Column(name = "channel_number", nullable = false) private Integer channelNumber = 1;`
- Każde świadectwo wzorcowania dodawane do urządzenia 1-kanałowego będzie domyślnie posiadało kanał = 1. Dla wielokanałowych wymuszony zostanie wybór.

### 1.4 Modyfikacja `ThermoMeasurementSeries` i `PositionData`
- W `ThermoMeasurementSeries` dodanie pola `@Column(name = "channel_number", nullable = false) private Integer channelNumber = 1;`
- W klasie DTO stanu sesji `RevalidationSession.PositionData` rozbudowanie pól o `Integer channelNumber` dla wierności w widoku podsumowania.

## 2. Migracje Bazodanowe (Flyway / SQL Script)
Rozwinięcie schematu bez utraty danych (Zero-Downtime Data Migration):
1. **CREATE TABLE** `thermo_recorder_models`.
2. Zasilenie `thermo_recorder_models` wartościami unikalnymi wyciągniętymi ze starej kolumny `thermo_recorders.model`. Kanały zasilone z wartości 1 (poza modelami w których nazwie występuje jawnie "T4", "T3" co posłuży jako tymczasowa heurystyka – o ile to bezpieczne, dla pewności default 1 dla wszystkich obecnych).
3. Dodanie klucza obcego `model_id` do tabeli `thermo_recorders`.
4. Wykonanie UPDATE dopasowującego String do ID.
5. Usunięcie starej kolumny `model` z tabeli `thermo_recorders`.
6. Dodanie kolumn `channel_number` do tabel `calibrations` i `thermo_measurement_series` z domyślną wartością 1.

## 3. Zmiany w Logice Biznesowej (Service Layer)

### 3.1 `ThermoRecorderService`
- Zmiana metodyki weryfikacji świadectw wzorcowania. Zamiast logiki `getLatestCalibration()` dla całego loggera, zbudowanie API w postaci `getLatestCalibrationForChannel(int channelNumber)`.

### 3.2 `TestoRevalidationService`
- Rozbudowanie metod walidacji `PositionData`. Sesja przy przypisywaniu urządzenia na narożnik musi dostać informację o wybranym kanale. Test musi udowodnić, że przypisanie 2x Kanału 1 z tego samego fizycznego urządzenia (Ten sam SN) rzuci wyjątek logiczny (Business Exception), podczas gdy przypisanie Kanału 1 do narożnika G-L-T i Kanału 2 do G-P-T jest operacją prawidłową.

## 4. Zmiany w UI (Presentation Layer)

### 4.1 Słownik Modeli
- Dodanie nowej pozycji w menu bocznym (Navbar) do otwierania panelu `ModelDictionaryView.fxml`. Widok umożliwi CRUD dla encji `ThermoRecorderModel`.

### 4.2 Okno dodawania Rejestratora
- Zmiana z TextInput na `ComboBox<ThermoRecorderModel>` ładowany z bazy w oknie `ThermoRecorderDialogController`.

### 4.3 Kreator Sesji Rewalidacji (Wizard)
- W etapie **Przypisz sprzęt**, na wierszu zawierającym pozycję sprzętu, gdy używamy wielokanałowego sprzętu (badane pod kątem właściwości wybranego `channelCount` powiązanego z urządzeniem), odblokowanie Spinnera/ComboBoxa pozwalającego wskazać numer kanału.

### 4.4 Moduł importu
- Zgodnie z decyzją, właściwy mechanizm integracji parsowania pliku `CSV/Hex` zostaje tymczasowo zawieszony do analizy Wireshark. Mimo to backend będzie gotowy by przyjąć pozycję dla danego kanału. W interfejsie po załadowaniu pliku dla urządzenia wielokanałowego, system poprosi o uściślenie dla którego kanału wrzucamy dany punkt pomiarowy.
