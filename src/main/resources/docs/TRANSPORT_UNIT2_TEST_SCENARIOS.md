# Test Plan / Scenarios: Logika GxP i Analiza Pomiarów (Jednostka 2)

Scenariusze testów jednostkowych i integracyjnych dla algorytmów weryfikacji pomiarów transportowych.

---

## Scenariusz 1: Prawidłowe Kadrowanie Serii (Trim Range) i Wykluczenie Przekroczeń Skrajnych

### Warunki początkowe (Setup):
1. Seria pomiarowa `ThermoMeasurementSeries` zawierająca 10 punktów pomiarowych co 10 minut:
   * P1 (08:00): 15.0°C (rejestrator w biurze - przekroczenie limitu lodówki 2-8°C)
   * P2 (08:10): 12.0°C (rejestrator w drodze do auta)
   * P3 (08:20): 5.0°C  (zamknięcie komory - start transportu)
   * P4 (08:30): 4.5°C
   * P5 (08:40): 4.2°C
   * P6 (08:50): 4.8°C
   * P7 (09:00): 5.1°C  (otwarcie komory - koniec transportu)
   * P8 (09:10): 10.5°C (wyjęcie rejestratora, rozładunek)
2. Założone limity temperatury trasy: **2.0°C do 8.0°C**.
3. Kadrowanie: Start = `08:20` (P3), Stop = `09:00` (P7).

### Kroki testowe:
1. Uruchom metodę `validateSeries` serwisu `TransportValidationService`.

### Oczekiwany wynik (Pass Criteria):
* Status walidacji: **SUCCESS** (Zaliczona).
* Wyliczona temperatura minimalna: **4.2°C** (próbka P5).
* Wyliczona temperatura maksymalna: **5.1°C** (próbka P7).
* Przekroczenia z P1, P2 oraz P8 zostały całkowicie zignorowane, ponieważ znajdowały się poza zakresem kadrowania.
* Liczba przeanalizowanych pomiarów: **5** (od P3 do P7).

---

## Scenariusz 2: Wyznaczenie Czasu Podtrzymania Temperatury (Hold-Time)

### Warunki początkowe (Setup):
1. Sesja transportu FFP (maksymalna dopuszczalna temperatura: **-18.0°C**).
2. Pomiary po odłączeniu zasilania (awaria o godz. `10:00`):
   * 10:00: -24.0°C
   * 10:10: -22.5°C
   * 10:20: -21.0°C
   * 10:30: -19.5°C
   * 10:40: -17.8°C (pierwsze przekroczenie limitu -18.0°C)
   * 10:50: -15.2°C

### Kroki testowe:
1. Wywołaj metodę `calculateHoldTimeMinutes` przekazując czas awarii `10:00` i limit `-18.0`.

### Oczekiwany wynik (Pass Criteria):
* Zwrócona wartość czasu podtrzymania: **40 minut** (czas od 10:00 do 10:40).
* Algorytm prawidłowo zidentyfikował pierwszą próbkę przekraczającą limit (P5, -17.8°C).
