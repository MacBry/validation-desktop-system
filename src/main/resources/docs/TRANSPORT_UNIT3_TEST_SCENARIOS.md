# Test Plan / Scenarios: Generator Raportów i Paczka ZIP (Jednostka 3)

Zestaw scenariuszy weryfikacji generowania raportów Word (.docx) oraz kompilacji spakowanych archiwów ZIP.

---

## Scenariusz 1: Generowanie Załącznika nr 4 (DOCX) z Mapowaniem Tagów

### Warunki początkowe (Setup):
1. Zapisany w zasobach szablon `appendix_4_template.docx`.
2. Przygotowana sesja walidacji transportu `TransportValidationSession`:
   * Trasa: *„Trasa Północna”*
   * Samochód: *„Ford Transit”* (Inwentarzowy: *„DEV-CAR-01”*)
   * Wynik GxP: Zaliczone (success = true), średnia temperatura = 4.5°C
   * Test awarii zasilania: nie dotyczy

### Kroki testowe:
1. Wywołaj metodę `generateAppendix4` serwisu `TestoTransportWordService`.
2. Otwórz powstały strumień bajtów jako `XWPFDocument` i wyodrębnij tekst.

### Oczekiwany wynik (Pass Criteria):
* Dokument generuje się bez wyjątków.
* Tekst zawiera podstawione wartości: *„Trasa Północna”*, *„Ford Transit”*, *„DEV-CAR-01”*.
* Checkbox `$tak$` został zamieniony na `[X]`. Checkbox `$nie$` został usunięty.
* Tag `$calculatedHoldTime$` został zamieniony na tekst *„Nie dotyczy”*.
* W dokumencie nie występują żadne niepodmienione tagi tekstowe (brak znaków `$`).

---

## Scenariusz 2: Kompilacja i Sprawdzenie Zawartości Paczki ZIP

### Warunki początkowe (Setup):
1. Sesja walidacji transportu ukończona i poprawnie przeliczona.
2. Przygotowane mocki dla serwisu wykresów PDF oraz świadectw wzorcowania.

### Kroki testowe:
1. Uruchom metodę `compileTransportPackage` z klasy `TransportZipCompiler`, wskazując plik wyjściowy `transport_package.zip`.
2. Rozpakuj archiwum ZIP i zweryfikuj nazwy plików w głównym katalogu oraz strukturę podkatalogów.

### Oczekiwany wynik (Pass Criteria):
* Plik ZIP zostaje pomyślnie utworzony na dysku i ma rozmiar większy niż 0.
* W archiwum znajdują się pliki:
  * `Zalacznik_nr_4_Raport_z_walidacji_warunkow_transportu.docx`
  * `Zalacznik_nr_8_Graficzny_schemat_rozmieszczenia_czujnikow.docx`
* W podkatalogu `wykresy/` znajdują się pliki wykresów PDF sensorów.
* W podkatalogu `certyfikaty/` znajdują się świadectwa wzorcowania użytych czujników.
