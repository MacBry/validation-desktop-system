# Specyfikacja Techniczna: Automatyczne Wypełnianie Protokołu Mapowania (Załącznik nr 7)

## 1. Architektura i Przepływ Danych

Generowanie dokumentu **Załącznika nr 7 (Protokół wykonania mapowania urządzenia)** opiera się na istniejącej klasie [TestoRevalidationWordService.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/TestoRevalidationWordService.java).

```mermaid
graph TD
    A[RevalidationSession (procedureType = MAPPING)] -->|Pobranie danych| B(TestoRevalidationWordService)
    C[Szablon appendix_7_template.docx] -->|Wczytanie XWPFDocument| B
    B -->|Generowanie replacementMap| D[Placeholder Replacement]
    D -->|Zapis DOCX| E[Plik wyjściowy Załącznik nr 7]
```

Dokument jest generowany wyłącznie dla sesji kwalifikacji temperaturowej (5-letniego mapowania komory), w których typ procedury to `GxPProcedureType.MAPPING`.

---

## 2. Szablon i Ścieżka Zasobów

Szablon Word ze znacznikami pobierany jest z zasobów pod ścieżką:
`validation-desktop/src/main/resources/templates/appendix_7_template.docx`

Plik ten został zmigrowany i oczyszczony bezpośrednio z oryginalnego dokumentu dostarczonego przez klienta (`Załącznik nr 7 Protokół wykonania mapowania urządzenia.docx`).

---

## 3. Szczegółowe Mapowanie Znaczników (Replacement Map)

W metodzie `prepareAppendix7Replacements(session)` tworzona jest mapa podmian dla następujących znaczników:

### 3.1. Metadane Ogólne
* `$dział$` $\rightarrow$ `session.getCoolingDevice().getDepartment().getName()`
* `$pracownia$` $\rightarrow$ `session.getCoolingDevice().getLaboratory() != null ? session.getCoolingDevice().getLaboratory().getName() : "—"`
* `$nazwaUrzadzenia$` $\rightarrow$ `session.getCoolingDevice().getName()`
* `$numerInwentarzowy$` $\rightarrow$ `session.getCoolingDevice().getInventoryNumber()`
* `$dataPierwszegoOdczytu$` $\rightarrow$ Najwcześniejszy czas pomiaru w serii (format `yyyy-MM-dd`).
* `$dataOstatniegoOdczytu$` $\rightarrow$ Najpóźniejszy czas pomiaru w serii (format `yyyy-MM-dd`).
* `$rodzajMaterialu$` $\rightarrow$ `session.getCoolingChamber().getMaterialName()`

### 3.2. Wiersze Tabeli Rejestratorów (Indeksy 1 do 8)
Dla każdej pozycji narożnikowej siatki `GridPosition pos` (gdzie `idx` to numer wiersza od 1 do 8):
* `$nrRej[idx]$` $\rightarrow$ `positionData.getSerialNumber()`
* `$loakalizacjaRej[idx]$` $\rightarrow$ `pos.getLabel()` (Uwaga: zachowano oryginalny błąd pisowni w szablonie - litera „a” w *loakalizacja*)
* `$tminRej[idx]$` $\rightarrow$ Minimalna temperatura odnotowana przez dany rejestrator (format `%.1f`)
* `$tmaxRej[idx]$` $\rightarrow$ Maksymalna temperatura odnotowana przez dany rejestrator (format `%.1f`)
* `$OT[idx]$` (Kwalifikacja TAK) $\rightarrow$ `[X]` jeśli czujnik został wyznaczony jako **Hotspot** lub **Coldspot** sesji mapowania, w przeciwnym razie puste `""`.
* `$ON[idx]$` (Kwalifikacja NIE) $\rightarrow$ `[X]` jeśli czujnik NIE został wyznaczony jako punkt skrajny, w przeciwnym razie puste `""`.

---

## 4. Wyznaczanie Punktów Krytycznych (Zgodność z SOP i GxP)

Zgodnie z wymogami procedury SOP (`SOP-CKiK-DZJ-WK-004`), punkty krytyczne są wyznaczane na podstawie ekstremów absolutnych (Opcja A):
- **Hotspot**: Lokalizacja narożnikowa, która zarejestrowała najwyższą pojedynczą wartość temperatury w całej sesji.
- **Coldspot**: Lokalizacja narożnikowa, która zarejestrowała najniższą pojedynczą wartość temperatury w całej sesji.

Punkty te są automatycznie kwalifikowane jako lokalizacje monitorowane w kolejnych latach (zaznaczenie pola **TAK**). Strefy stabilne temperaturowo są oznaczane jako niekwalifikowane do rewalidacji rocznej (zaznaczenie pola **NIE**).

---

## 5. Integracja z RevalidationZipCompiler

Podczas generowania paczki ZIP, [RevalidationZipCompiler.java](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/RevalidationZipCompiler.java) sprawdza typ procedury sesji:
* **Gdy `session.getProcedureType() == GxPProcedureType.MAPPING`**:
  - Generowany jest Załącznik nr 7 (Protokół mapowania).
  - Do ZIP pakowany jest plik: `Zalacznik_nr_7_Protokol_wykonania_mapowania_urzadzenia.docx`.
  - Załącznik nr 3 (Raport z walidacji procesu przechowywania) jest pomijany.
* **Gdy `session.getProcedureType() == GxPProcedureType.PERIODIC_REVALIDATION`**:
  - Generowany jest Załącznik nr 3 (Raport rewalidacji).
  - Do ZIP pakowany jest plik: `Zalacznik_nr_3_Raport_z_walidacji_procesu_przechowywania.docx`.
  - Załącznik nr 7 jest pomijany.
* Załącznik nr 8 (Schemat rozmieszczenia czujników) jest pakowany niezależnie dla każdego rodzaju procedury.
