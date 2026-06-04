# Scenariusze Testów (Test Scenarios): Integracja Testo 184T (Programowanie i Odczyt PDF)

## Założenia Ogólne
Niniejszy dokument przedstawia formalne scenariusze testowe do weryfikacji modułów programowania (`Testo184ProgrammingService`) oraz odczytu danych (`Testo184UsbImportService`) rejestratora Testo 184T. Testy podzielono na jednostkowe (Unit Tests), integracyjne (Integration Tests) oraz testy sprzętowe (Hardware-in-the-Loop, HIL).

---

## 1. Testy Jednostkowe (Unit Tests)

### TS-UNIT-01: Weryfikacja Algorytmów Kompensacji Temperatur (value_add_200 i parse_value_minus_200)
*   **Opis:** Walidacja poprawności dodawania i odejmowania offsetu temperatury (przesunięcie o +200.0) w celu wyeliminowania wartości ujemnych z kodowania Hex ASCII.
*   **Dane Wejściowe:**
    *   Próba 1: Temperatura dodatnia `25.5` °C.
    *   Próba 2: Temperatura ujemna `-26.6` °C.
    *   Próba 3: Wartość pusta/brak limitu (np. `None` / `NaN`).
*   **Oczekiwany Wynik:**
    *   Próba 1: Zwrócony ciąg `"225.5"`.
    *   Próba 2: Zwrócony ciąg `"173.4"`.
    *   Próba 3: Zwrócony ciąg `"NaN"`.
    *   Metoda parsująca z odejmowaniem offsetu musi przywrócić pierwotne wartości z dokładnością do jednego miejsca po przecinku.

### TS-UNIT-02: Round-Trip Kodowania i Dekodowania pola `<alldata>`
*   **Opis:** Sprawdzenie, czy konfiguracja zakodowana do ciągu `<alldata>` zostanie zdekodowana z powrotem do identycznego stanu wyjściowego.
*   **Dane Wejściowe:** Obiekt `TestoConfig` z parametrami: interwał = 10 min, tryb startu = 3 (przycisk), alarm_temp_1 = enabled (MAX: 8.0°C), alarm_temp_2 = enabled (MIN: 2.0°C).
*   **Oczekiwany Wynik:** Ciąg znaków wygenerowany przez `AlldataEncoder.encode` po sparsowaniu przez `AlldataDecoder.decode` zwraca strukturę słownikową zawierającą dokładnie te same wartości (interwał: 10, start_mode: 3, alarm_temp_1: 8.0°C, alarm_temp_2: 2.0°C).

### TS-UNIT-03: Dekodowanie Wartości Stałoprzecinkowej Q16.16 z bufora binarnego PDF
*   **Opis:** Walidacja konwersji 4-bajtowej liczby Little-Endian na precyzyjną temperaturę w °C.
*   **Dane Wejściowe:** Surowy blok 4 bajtów z PDF: `7e 95 05 31` (reprezentujący uint32 LE `0x3105957e` = `822457726` dziesiętnie).
*   **Oczekiwany Wynik:** Obliczenie wartości zgodnie ze wzorem zwraca temperaturę **25.4958 °C**.

---

## 2. Testy Integracyjne (Integration Tests)

### TS-INT-01: Generowanie Pliku XML/XDP Konfiguracji
*   **Opis:** Weryfikacja, czy usługa programująca poprawnie tworzy strukturę XML na wskazanym dysku testowym.
*   **Dane Wejściowe:** Wywołanie metody `Testo184ProgrammingService.programDevice` z poprawnymi parametrami oraz ścieżką docelową do tymczasowego folderu testowego.
*   **Oczekiwany Wynik:**
    *   W folderze docelowym powstaje plik `testo 184 configuration_data.xml`.
    *   Plik posiada poprawną strukturę XML (jest parsowalny przez parser DOM/SAX).
    *   Znacznik `<alldata>` zawiera niepusty ciąg zaczynający się od `%%SHENZHEN%%` i kończący terminatorem `%%%%%%%%`.

### TS-INT-02: Obsługa Błędnego lub Uszkodzonego Pliku PDF
*   **Opis:** Weryfikacja odporności systemu na import pliku PDF bez binarnego strumienia danych Testo.
*   **Kroki:**
    1. Użytkownik wskazuje losowy plik PDF (np. artykuł naukowy, pusty plik).
    2. Usługa `Testo184UsbImportService` próbuje zaimportować dane.
*   **Oczekiwany Wynik:** 
    *   System nie ulega awarii (brak nieobsłużonych wyjątków).
    *   Zwracany jest status `ERROR` z komunikatem: "Nie załączono pliku PDF lub plik jest uszkodzony / nie zawiera metadanych Testo".

---

## 3. Testy Sprzętowe (Hardware-in-the-Loop - HIL)

### TS-HW-01: Programowanie Fizycznego Rejestratora Testo 184T3
*   **Sprzęt:** Rejestrator Testo 184 T3 podłączony bezpośrednio do portu USB komputera.
*   **Kroki:**
    1. W aplikacji *Validation Desktop* wybierz opcję programowania Testo 184T.
    2. Wskaż wykrytą literę dysku rejestratora (np. `E:\`).
    3. Ustaw parametry (np. start przyciskiem, interwał 5 minut, alarmy: 2.0°C - 8.0°C).
    4. Kliknij przycisk "Programuj".
*   **Oczekiwany Wynik:**
    *   Aplikacja informuje o sukcesie programowania.
    *   Na dysku rejestratora powstaje plik `testo 184 configuration_data.xml`.
    *   Dioda LED na rejestratorze sygnalizuje przejście w stan czuwania (migająca dioda).

### TS-HW-02: Odczyt i Walidacja Serii Pomiarowej z Raportu PDF
*   **Sprzęt:** Rejestrator Testo 184 T3 z zapisaną sesją pomiarową podłączony do USB.
*   **Kroki:**
    1. Skopiuj automatycznie wygenerowany raport PDF z dysku rejestratora na dysk lokalny komputera.
    2. W aplikacji *Validation Desktop* wybierz opcję importu PDF i wskaż skopiowany raport.
*   **Oczekiwany Wynik:**
    *   Aplikacja poprawnie importuje wszystkie próbki.
    *   Liczba pomiarów zgadza się z danymi na wykresie PDF.
    *   Pomiary w bazie danych posiadają precyzyjną temperaturę (4 miejsca po przecinku) i są poprawnie stemplowane czasem lokalnym (uwzględniając przesunięcie czasu polskiego i DST).
    *   W bazie danych i w Audit Trail zostaje odnotowany fakt importu pomiarów z podaniem numeru seryjnego rejestratora wyciągniętego z PDF.
