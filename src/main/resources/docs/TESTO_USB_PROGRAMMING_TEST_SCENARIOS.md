# Scenariusze Testów (Test Scenarios): Programowanie Testo 174T USB

## Założenia Ogólne
Niniejszy dokument przedstawia wytyczne i formalne scenariusze testowe dla weryfikacji zintegrowanego modułu (TestoProgrammingService) operującego na sprzętowych rejestratorach temperatury. Testy podzielono na jednostkowe (bez udziału sprzętu), integracyjne-symulowane oraz fizyczne testy sprzętowe (Hardware-in-the-Loop, HIL).

---

## 1. Testy Jednostkowe (Unit Tests) / Środowisko Wirtualne

### TS-UNIT-01: Weryfikacja Algorytmu Checksum (XOR + Maska A5)
*   **Opis:** Walidacja czy poprawnie przeliczana jest 1-bajtowa suma kontrolna dla danej komendy `GET_STATUS`.
*   **Dane Wejściowe:** Tablica bajtów `AB 31 00 42 1B`.
*   **Oczekiwany Wynik:** Zwrócona wartość przez kalkulator powinna wynosić dokładnie `0x66` (66 hex).

### TS-UNIT-02: Konwersja czasu na `Start Delay` z przekroczeniem zmiany Czasu (DST)
*   **Opis:** Sprawdzenie, czy system odporny jest na przesunięcie czasu z Zimowego na Letni.
*   **Dane Wejściowe:**
    *   Bieżąca data (Program Date): 29 Marca, godzina 01:00 UTC (02:00 CET).
    *   Docelowa data startu (Start Time Local): 29 Marca, godzina 04:00 (ZMIANA CZASU na CEST - czas lokalny to już CEST, UTC+2).
*   **Oczekiwany Wynik:** Wyliczony interwał `Start Delay` wynosi `60 minut` (01:00 UTC -> 02:00 UTC), a nie np. 120 minut z powodu przeliczeń różnicy lokalnego zegara. System testujący w Javie używający klas ZonedDateTime przejdzie poprawnie przez barierę.

### TS-UNIT-03: Walidacja Ekstremalnych Wartości Granic Temperaturowych (Payload)
*   **Opis:** Konwersja limitów temperatur (float na 16-bit Big Endian)
*   **Dane Wejściowe:** Wysłanie limitów: Dolny `-25.5 °C`, Górny `100.5 °C`.
*   **Oczekiwany Wynik:**
    *   `100.5 * 10 = 1005` -> HEX: `03 ED` -> bajty [19: 0x03, 20: 0xED].
    *   `-25.5 * 10 = -255` -> HEX (U2): `FF 01` -> bajty [21: 0xFF, 22: 0x01].

---

## 2. Testy Integracyjne (Symulowane Sterowniki / Mock USB)

### TS-INT-01: Test Przepływu Kompletnego (Handshake -> Program -> Finalize)
*   **Opis:** Symulowanie prawidłowych odpowiedzi sprzętu na zapytania w architekturze Mock.
*   **Kroki:**
    1. Aplikacja uderza w interfejs MockITestoUSBDevice, próbując zaprogramować logger.
    2. Moduł Mock imituje wczesną zwracaną ramkę `AB 30 ...` potwierdzając inicjalizację.
    3. Serwis aplikacyjny wysyła poprawnie wygenerowaną strukturę payloadu 33 bajty `AB 61...`.
    4. Moduł Mock imituje poprawną odpowiedź ACK.
*   **Oczekiwany Wynik:** Serwis zwraca flagę `Success`, w pliku systemowym tworzy się wiersz Audit Trail. Brak wyjątków zablokowania portu COM/USB.

### TS-INT-02: Timeout Komunikacyjny (Symulacja Wypięcia Urządzenia)
*   **Opis:** Weryfikacja odporności na błędy (Exception Handling) podczas przerwanej komunikacji.
*   **Kroki:**
    1. Aplikacja wysyła `AB 31` pytając o status.
    2. Mock przerywa działanie i nie odsyła odpowiedzi w ciągu wymaganego timeout'u (np. 1500 ms).
*   **Oczekiwany Wynik:** Złapany dedykowany błąd `USBTimeoutException` (lub odpowiednik JNA). Komunikat błędów informuje usera "Utracono połączenie z rejestratorem". Wątek UI interfejsu JavaFX wraca do stanu spoczynku bez zablokowania programu.

---

## 3. Testy Sprzętowe (Hardware-in-the-Loop - HIL) - Manualne na produkcji

### TS-HW-01: Faktyczne nadpisanie pamięci EEPROM Testo 174T
*   **Sprzęt:** Rejestrator Testo 174T wpięty do stacji dokującej USB.
*   **Kroki:**
    1. Z poziomu VCC Desktop App wpisanie parametrów (Interwał = 5 min, Pomiarów = 100, Start = Dzisiaj za 30 minut).
    2. Kliknięcie "Programuj urządzenie".
    3. Zakończenie pracy aplikacji VCC Desktop.
    4. Otwarcie oficjalnej aplikacji Comfort Software. Podgląd ustawień urządzenia w panelu fabrycznym.
*   **Oczekiwany Wynik:** Comfort Software wyświetli dokładnie te parametry (Interwał: 5m, Pomiarów: 100, zaplanowana Data Startu zgadza się ze strefą lokalną). Brak komunikatów błędu bazy w sprzęcie.

### TS-HW-02: Wygenerowanie poprawnego cyklu na badanej komorze
*   **Sprzęt:** Oprogramowany przez nas Rejestrator wkładany do testowej zamrażarki (-20°C).
*   **Kroki:**
    1. Programujemy loger z aplikacji VCC Desktop.
    2. Czekamy na wykonanie przez loger swojej pełnej tury (np. 1h).
    3. Wczytujemy z logera z powrotem odczyty przy użyciu VCC Desktop.
*   **Oczekiwany Wynik:** Otrzymana lista wyników nie jest zepsuta (temperatury ok -20, indeksy od 1 do n rosnąco), daty zarejestrowane przez loger są absolutnie zsynchronizowane z faktycznym zaplanowanym Start Time w kroku 1. Rejestrator nie wykazuje anomali czasowych wynikających ze złego zapisu offsetu opóźnienia Start Delay.
