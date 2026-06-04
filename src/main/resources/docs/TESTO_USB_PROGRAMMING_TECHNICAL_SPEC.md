# Specyfikacja Techniczna: Protokół Programowania USB Testo 174T

## 1. Architektura Komunikacji Niskopoziomowej
Implementacja komunikacji z rejestratorem Testo 174T za pośrednictwem interfejsu dokującego opiera się na wymianie predefiniowanych pakietów (ramek) binarnych HEX (Endpoint IN: 0x82, Endpoint OUT: 0x02).

System VCC Desktop zintegruje klasę `TestoProgrammingController` (Java), wykorzystując sprawdzony strumień JNA do interakcji ze sterownikami USB.

## 2. Sekwencja Programowania (Handshake & Params)
Aby rejestrator przyjął nowe nastawy, niezbędne jest wykonanie ścisłej, autoryzacyjnej sekwencji ramek:
1.  **Hardware Reset:** Wysłanie `0xF0`
2.  **Initialize (Hello):** `AB 01 0D 00 00 02`
3.  **Get Device Info:** `AB 30 00 02 0B 37` (Zwraca S/N urządzenia)
4.  **Get Current Status:** `AB 31 00 42 1B 66`
5.  **Metadata Dump:** 8x ramki zrzucające metadane konfiguracyjne z banku `0x0100` (`AB 33 ...`)
6.  **Metadata Write:** 8x nadpisujące wyczyszczone metadane (`AB 63 ...`)
7.  **MCU Refresh:** `0xF0` + zapytanie o status.
8.  **SET PARAMS (Crucial Payload):** `AB 61 00 42 1B [27-bajtowy Payload] [Checksum]`
9.  **Finalize:** Trzykrotne wysłanie ramki zamykającej `AB 01 0B 00 00 04`.

## 3. Struktura Payloadu Konfiguracyjnego (Komenda `AB 61`)
Właściwy pakiet programujący (33 bajty) zawiera kluczowe nastawy pomiarowe rejestratora. Format danych wewnątrz to Big-Endian.

### Mapa 27-bajtowego Payloadu:
| Offset | Rozmiar | Typ / Format | Opis | Przykład HEX |
| :--- | :--- | :--- | :--- | :--- |
| `[0-1]` | 2B | stałe | Flaga początkowa i długość (0x1B = 27B) | `04 1B` |
| `[2-3]` | 2B | `uint16 BE` | Interwał w minutach | `00 B4` (180 min) |
| `[4-5]` | 2B | `uint16 BE` | Liczba pomiarów do wykonania (Count) | `00 28` (40) |
| `[6-8]` | 3B | `uint24 BE` | **Start Delay** (minuty od daty wgrania) | `00 06 77` (1655) |
| `[9-15]` | 7B | BCD Time | **Program Date** w formacie UTC (Rok, Mies, Dzień, Godz, Min, Sek) | `07 EA 05 13 05 19 00` |
| `[16-18]` | 3B | Zera | Zarezerwowane wypełnienie puste | `00 00 00` |
| `[19-20]` | 2B | `int16 BE` | Górny próg alarmu temperatury (Wartość * 10) | `00 50` (8.0°C) |
| `[21-22]` | 2B | `int16 BE` | Dolny próg alarmu temperatury (Wartość * 10) | `00 14` (2.0°C) |
| `[23-24]` | 2B | `int16 BE` | Maksymalny limit sondy / pomocniczy (* 10) | `03 E8` (100.0°C) |
| `[25-26]` | 2B | Zera | Zarezerwowane wypełnienie puste | `00 00` |

## 4. Logika Konwersji Czasów (Start Delay Algorithm)
Loger Testo **nie przyjmuje wprost zaprogramowanej daty i godziny startu pierwszego pomiaru**. Wbudowany układ RTC logera odlicza czas do pierwszego pomiaru w oparciu o opóźnienie liczone od momentu podpięcia kabla USB.

**Algorytm wykorzystywany w Java:**
1.  Użytkownik wybiera docelową lokalną datę startu (np. strefa czasowa "Europe/Warsaw").
2.  Java konwertuje lokalną datę do `ZonedDateTime` w `ZoneOffset.UTC` (Zmienna: `startTimeUTC`).
3.  Zapisanie dokładnego bieżącego momentu komunikacji z komputerem (Zmienna: `programDateUTC`).
4.  Wyliczenie delty w minutach: `startDelay = Duration.between(programDateUTC, startTimeUTC).toMinutes()`.
5.  Zasilenie payloadu wyliczonym `startDelay` (bajty 6-8) oraz `programDateUTC` (bajty 9-15).

## 5. Reverse-Engineered Checksum Algorithm
Urządzenie ignoruje paczkę (zwracając timeout lub `AB E1 NACK`), jeżeli ostatni bajt nie zgadza się ze strukturalną sumą kontrolną.
*   **Algorytm:** XOR wszystkich 32 bajtów ramki (od bajtu pierwszego `AB` do ostatniego bajtu payloadu `00`), a następnie poddanie otrzymanego wyniku jednorazowej operacji **XOR z wartością stałą-maską `0xA5`**.

```java
// Wzór implementacyjny w Javie
byte calculateChecksum(byte[] fullFrameWithoutChecksum) {
    byte checksum = 0;
    for (byte b : fullFrameWithoutChecksum) {
        checksum ^= b;
    }
    return (byte) (checksum ^ 0xA5);
}
```

## 6. Integracja Klas do Głównego Projektu
1.  Skopiowanie modułu protokołu (np. interfejsu `ITestoUSBDevice` oraz `TestoProgrammingController`) do warstwy systemowej, odpowiedzialnej za sterowniki.
2.  Zbudowanie wrappera aplikacyjnego (wstrzykiwanego Serwisu `TestoProgrammingService`), który udostępni metodę abstrakcyjną `boolean programDevice(ProgrammingParams params)`.
3.  Upięcie tego pod dedykowany widok kontrolera GUI wywoływany w procesie Rewalidacji lub globalnie w zarządzaniu sprzętem pomiarowym.
