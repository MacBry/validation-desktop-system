# Analiza Biznesowa: Rejestratory Temperatury i Wzorcowanie

## 1. Cel i Kontekst
System VCC służy do walidacji procesów przechowywania produktów wrażliwych. Kluczowym elementem zapewnienia jakości (Quality Assurance) jest pewność, że dane pomiarowe są dokładne i pochodzą z urządzeń podlegających regularnej kontroli metrologicznej.

## 2. Rejestratory Temperatury (ThermoRecorders)
Urządzenia pomiarowe są traktowane jako krytyczne zasoby systemu.

### 2.1. Atrybuty Biznesowe
- **Identyfikowalność**: Każde urządzenie musi być unikalnie identyfikowane przez Numer Seryjny (S/N).
- **Zasoby Lokalizacyjne**: Rejestratory są przypisane do konkretnych jednostek organizacyjnych (Działy, Pracownie).
- **Charakterystyka Metrologiczna**: Rozdzielczość urządzenia jest kluczowa dla budżetu niepewności (zgodnie z wytycznymi GUM).

### 2.2. Cykl Życia Rejestratora
1. **Przyjęcie do systemu**: Rejestracja modelu i S/N.
2. **Wzorcowanie**: Regularne (zazwyczaj co 12 miesięcy) sprawdzanie dokładności w akredytowanym laboratorium.
3. **Eksploatacja**: Wykorzystanie w mapowaniach temperatur i wilgotności.
4. **Wycofanie**: Zmiana statusu na nieaktywny (zachowanie historii dla celów audytowych).

## 3. Wzorcowanie (Calibration)
Wzorcowanie jest zewnętrznym procesem dokumentowanym Świadectwem Wzorcowania.

### 3.1. Wymagania dotyczące Świadectwa
- **Numer Świadectwa**: Unikalny numer nadany przez laboratorium.
- **Data Wzorcowania**: Moment wykonania pomiarów porównawczych.
- **Ważność**: Termin, po którym urządzenie nie może być używane do walidacji (domyślnie 1 rok).
- **Punkty Wzorcowania**: Zbiór par (Temperatura zadana, Błąd systematyczny, Niepewność).
- **Skan Świadectwa (Digitalizacja)**: Możliwość dołączenia pliku PDF bezpośrednio do rekordu wzorcowania, co eliminuje konieczność przeszukiwania fizycznych segregatorów podczas audytu.

## 4. Wykorzystanie Danych we Wspomaganiu Decyzji
System wykorzystuje dane z wzorcowania do:
- **Korekty Pomiary**: Automatyczne dodawanie poprawki (błędu systematycznego) do surowych danych z rejestratora.
- **Obliczania Niepewności**: Uwzględnianie niepewności rozszerzonej (U) w raportach końcowych OQ/PQ.
- **Zapewnienia Zgodności**: Blokowanie możliwości generowania raportu, jeśli którykolwiek z użytych rejestratorów ma nieważne wzorcowanie.

## 5. Zgodność z GxP / GMP
- **Integralność Danych**: Każda edycja danych historycznych musi być zarejestrowana w Audit Trail.
- **Dostępność Danych**: Skany świadectw (PDF) powinny być łatwo dostępne bezpośrednio z poziomu kartoteki rejestratora.
