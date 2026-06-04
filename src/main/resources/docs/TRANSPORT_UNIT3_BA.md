# Business Analysis (BA): Generator Raportów i Paczka ZIP (Jednostka 3)

## 1. Cel Biznesowy
Zwieńczeniem procesu walidacji warunków transportu jest dostarczenie kompletnej dokumentacji GxP do Działu Zapewnienia Jakości (DZJ) w celu ostatecznego zatwierdzenia. 

Aby wyeliminować ręczne przepisywanie danych i zapobiec ryzyku popełnienia błędu ludzkiego, system musi:
1. Dynamicznie wygenerować **Załącznik nr 4 (Raport z walidacji warunków transportu)** w formacie DOCX na podstawie wgranego szablonu.
2. Skompilować paczkę ZIP (archiwum), zawierającą wszystkie dokumenty powiązane z daną trasą.

---

## 2. Dynamiczne Mapowanie Szablonu Załącznika nr 4

Podobnie jak w przypadku Załącznika nr 3, szablon Word DOCX Załącznika nr 4 będzie zawierał wbudowane znaczniki tekstowe:

### 2.1. Metadane Trasy i Urządzenia
* `$dzial$` i `$pracownia$` – Dział i laboratotorium realizujące transport.
* `$CelWalidacji$` – Opis procedury (np. *„Walidacja roczna transportu”*).
* `$nazwaUrządzenia$` lub `$nazwaKomory$` – Nazwa samochodu lub mobilnej lodówki.
* `$numerSeryjnyUrzadzenia$` – Numer inwentarzowy urządzenia transportowego.
* `$trasa$` – Nazwa i kod trasy.
* `$dataTransportu$` – Data przejazdu.

### 2.2. Dane Pomiarowe i Świadectwa
* Tabela rejestratorów (maksymalnie 2 czujniki):
  * `$nrSerRej[idx]$` – Numer seryjny czujnika.
  * `$NrCertRej[idx]$` – Świadectwo wzorcowania.
  * `$TminRej[idx]$`, `$TmaxRej[idx]$`, `$TavgRej[idx]$` – Parametry temperaturowe na trasie.
* `$calculatedHoldTime$` – Wyliczony czas podtrzymania temperatury (Hold-Time) w przypadku testu awarii zasilania (np. *„40 minut”* lub *„Nie dotyczy”*).

### 2.3. Wnioskowanie GxP
* Checkboxy akceptacji: `$tak$` (spełnia kryteria) / `$nie$` (nie spełnia kryteriów).
* `$Wnioski$` – Automatyczna ocena (np. *„W trakcie transportu temperatura utrzymała się w przedziale 2.0°C do 10.0°C. Transport spełnia kryteria akceptacji.”*).
* `$Uwagi$` – Wykaz przekroczeń lub alarmów (jeśli wystąpiły).

---

## 3. Kompilacja Paczki ZIP (Archiwum Walidacji)
Skompilowany pakiet ZIP (paczka rewalidacyjna transportu) musi zostać zapisany pod unikalną nazwą (np. `Paczka_Transport_DEV-CAR-01_TR-NORTH-01_20260525.zip`) i zawierać:
1. **Załącznik nr 4 (DOCX)** – wypełniony raport z walidacji transportu.
2. **Załącznik nr 8 (DOCX)** – graficzny schemat rozmieszczenia czujników w aucie/komorze.
3. **Indywidualne wykresy temperatur (PDF)** – wygenerowane dla każdego użytego rejestratora.
4. **Świadectwa wzorcowania (PDF)** – kopie dokumentów metrologicznych użytych rejestratorów.
5. **Protokół transportu (PDF/PNG)** – opcjonalny załącznik (np. skan papierowej karty drogowej załadowany przez użytkownika).
