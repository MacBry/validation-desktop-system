# Plan Testów - Interpretacja Reguł Nelsona

## Cel
Weryfikacja, czy dodany mechanizm interpretacji reguł Nelsona na kartach kontrolnych poprawnie pobiera teksty, obsługuje błędy i właściwie zawija długi tekst w interfejsie użytkownika (JavaFX).

## 1. Testy Jednostkowe (Unit Tests)

**Klasa testowana:** `com.mac.bry.desktop.service.stats.NelsonRulesInterpreter`

| ID Testu | Nazwa Przypadku | Akcja do Wykonania | Oczekiwany Rezultat | Status |
|---|---|---|---|---|
| TC_UNIT_01 | Interpretacja X-Bar (Reguła 1) | Wywołaj `getXBarInterpretation(1)` | Zwrócony ciąg znaków zawiera słowo "nagłym" lub "otwarcie drzwi". Nie może być null. | `[x]` |
| TC_UNIT_02 | Interpretacja X-Bar (Reguła 2) | Wywołaj `getXBarInterpretation(2)` | Zwrócony ciąg znaków zawiera frazę "Trwałe przesunięcie". Nie może być null. | `[x]` |
| TC_UNIT_03 | Interpretacja X-Bar (Reguła 3) | Wywołaj `getXBarInterpretation(3)` | Zwrócony ciąg znaków zawiera słowo "trend". Nie może być null. | `[x]` |
| TC_UNIT_04 | Interpretacja X-Bar (Reguła 4) | Wywołaj `getXBarInterpretation(4)` | Zwrócony ciąg znaków zawiera frazę "Niestabilność oscylacyjna". Nie może być null. | `[x]` |
| TC_UNIT_05 | Interpretacja X-Bar (Niezdefiniowana) | Wywołaj `getXBarInterpretation(99)` | Zwrócony ciąg znaków zawiera frazę "Niezdefiniowana anomalia". Nie rzuca wyjątku. | `[x]` |
| TC_UNIT_06 | Interpretacja S (Reguła 1) | Wywołaj `getSChartInterpretation(1)` | Zwrócony ciąg znaków opisuje "skok zmienności" lub "turbulencje". | `[x]` |
| TC_UNIT_07 | Interpretacja S (Niezdefiniowana) | Wywołaj `getSChartInterpretation(99)` | Zwrócony ciąg znaków wskazuje na niezdefiniowaną anomalię zmienności. Nie rzuca wyjątku. | `[x]` |

## 2. Testy Integracyjne (UI JavaFX)

**Testowany widok:** `StatsDiagnosticsDialog`

| ID Testu | Nazwa Przypadku | Akcja do Wykonania | Oczekiwany Rezultat | Status |
|---|---|---|---|---|
| TC_UI_01 | Wyświetlenie listy reguł | Otwórz okno "Diagnostyka Statystyczna" dla czujnika, dla którego wykryto np. cykl defrostu (wymuszona Reguła 1). | W kontrolce `lstNelsonViolations` pojawia się pozycja zawierająca informację o naruszeniu oraz nową linijkę `> Interpretacja: [tekst]`. | `[ ]` |
| TC_UI_02 | Zawijanie długiego tekstu | Rozciągnij/zmniejsz okno z załadowanymi regułami. | Długi tekst interpretacji nie wychodzi poza szerokość okna (brak poziomego paska przewijania, tekst przenosi się do kolejnej linii wewnątrz komórki). | `[ ]` |
| TC_UI_03 | Formatowanie tekstu | Przejrzyj dodane komórki listy. | Tekst jest poprawnie obramowany od dołu (podział na sekcje), czcionka jest czerwona i pogrubiona (`#b91c1c`), dookoła tekstu jest padding. | `[ ]` |
