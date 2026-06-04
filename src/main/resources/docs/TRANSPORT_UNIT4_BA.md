# Business Analysis (BA): Interfejs Użytkownika JavaFX (Jednostka 4)

## 1. Cel Biznesowy
Interfejs użytkownika (UI) musi przeprowadzić operatora (metrologa) przez proces walidacji transportu w sposób intuicyjny, uniemożliwiający pominięcie kroków wymaganych przez procedury GxP. 

Wizualna prezentacja wykresu z możliwością interaktywnego kadrowania zakresu czasu (Trim Range) pozwala użytkownikowi na natychmiastową ocenę przebiegu temperatur bez konieczności ręcznego wyliczania czasów w arkuszach kalkulacyjnych.

---

## 2. Architektura Kreatora (Multi-Step Wizard)

Kreator walidacji warunków transportu będzie zaimplementowany jako 5-etapowe okno dialogowe:

```mermaid
graph LR
    Step1[Krok 1: Parametry] --> Step2[Krok 2: Konfiguracja]
    Step2 --> Step3[Krok 3: Import danych]
    Step3 --> Step4[Krok 4: Kadrowanie Wykresu]
    Step4 --> Step5[Krok 5: Wynik GxP]
```

### Krok 1: Wybór parametrów trasy i urządzenia
* Selektor (ComboBox) aktywnego pojazdu lub komory przenośnej z ewidencji.
* Selektor (ComboBox) zdefiniowanej trasy transportowej.
* Pole wyboru daty transportu oraz pole tekstowe na dane kierowcy.

### Krok 2: Parametry ładunku i konfiguracja testu
* Pola numeryczne do wpisania liczby transportowanych składników (np. *„KKCz: 100 szt.”*, *„FFP: 50 szt.”*) w celu udokumentowania pełnego obciążenia.
* Checkbox: *„Test awarii zasilania”*. Po zaznaczeniu użytkownik będzie musiał wskazać na wykresie moment odłączenia prądu.

### Krok 3: Import plików pomiarowych
* Przycisk wyboru pliku PDF z odczytu rejestratora Testo 184 (lub bezpośredni import z USB dla Testo 174T).
* Walidacja poprawności danych (czy plik nie jest uszkodzony, czy numer seryjny czujnika ma ważne świadectwo wzorcowania).

### Krok 4: Interaktywne kadrowanie (Wykres)
* Wyświetlenie przebiegu temperatur na wykresie liniowym.
* Dwa pionowe suwaki (suwak lewy: start transportu, suwak prawy: koniec transportu), którymi użytkownik fizycznie zaznacza zakres podróży na osi czasu.
* Jeśli wybrano test awarii zasilania – trzeci suwak wskazujący czas odłączenia zasilania.

### Krok 5: Podsumowanie i eksport
* Wyświetlenie informacji: status (ZALICZONY / NIEZALICZONY), średnia temperatura, czas podtrzymania (Hold-Time) w przypadku testu awarii.
* Podgląd automatycznie wygenerowanych uwag i wniosków.
* Przycisk **„Generuj paczkę ZIP”** zapisujący archiwum na dysku.
