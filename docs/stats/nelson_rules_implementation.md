# Dokumentacja Implementacyjna - Interpretacje Reguł Nelsona

## Architektura Rozwiązania
Zmiana polega na utworzeniu małej, zgrabnej warstwy translacji statystycznej na język biznesowy. W ramach implementacji uniknięto zmiany silnika analitycznego (`NelsonRulesDetector`), aby nie zaciemniać kodu matematycznego opisami.

### 1. Nowa Klasa `NelsonRulesInterpreter`
* **Zasada działania**: Zaimplementowana jako `public class` ze statycznymi metodami narzędziowymi (wzorzec Helper/Utility).
* **Metody**:
    * `getXBarInterpretation(int ruleNumber)`: Przyjmuje zdekodowany ID reguły dla karty X-Bar i na bazie bloku `switch` zwraca gotowy `String` z interpretacją dla inżyniera walidacji.
    * `getSChartInterpretation(int ruleNumber)`: Analogicznie obsługuje naruszenia na karcie zmienności S.
* **Cel architektoniczny**: Separacja logiki biznesowej/językowej od silnika statystycznego (Single Responsibility Principle).

### 2. Zmiany w UI (`StatsDiagnosticsDialogController`)
* W pętlach przetwarzających kolekcje `xbarViolations` i `sViolations` dodano wywołanie metod interpretera.
* Interpolacja Stringa w `msg` została zaktualizowana do formatu: `... \n> Interpretacja: [tekst]`.
* **Rozszerzenie ListCell**: Standardowa klasa `ListView` przycinała teksty po szerokości. Aby interpretacje (które potrafią zająć 2-3 linijki tekstu) były czytelne, użyto:
    * `setWrapText(true)` - co automatycznie zwija tekst zachowując pełne bloki bez obcinania.
    * Dodano stylizację CSS z lekkim dołu-obramowaniem (`-fx-border-color: #e5e7eb; -fx-border-width: 0 0 1 0;`), aby wyraźnie oddzielić poszczególne reguły od siebie, skoro pojedynczy element listy zajmuje teraz znacznie więcej miejsca w pionie. Zastosowano delikatny padding.

### 3. Testy Jednostkowe
* Zaimplementowano klasę testową w folderze `src/test/java`, pokrywającą pozytywne przypadki biznesowe (Reguły 1-4) oraz zachowanie funkcji dla ID, które nie są zdefiniowane (tzw. "graceful degradation", np. zwracanie domyślnego ciągu "Niezdefiniowana anomalia").
