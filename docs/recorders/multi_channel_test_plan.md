# Scenariusze Testowe – Rejestratory wielokanałowe

Poniższy plan testów ma na celu weryfikację zmian, które modyfikują jądro aplikacji: architekturę rejestratorów w modelu relacyjnym bazy danych.

## 1. Testy Bazy Danych i Migracji (Database & Liquibase/Flyway)

| ID | Tytuł Scenariusza | Warunki Początkowe | Kroki Testowe | Oczekiwany Rezultat |
| :--- | :--- | :--- | :--- | :--- |
| **DB-01** | Migracja starych ciągów znaków (model) | Baza przed uruchomieniem skryptów, z 5 rejestratorami "Testo 174T" i jednym "Testo 176 T4" | Uruchom środowisko lub narzędzie do migracji bazy danych | Tabela `thermo_recorder_models` posiada poprawnie wydobyte dwie pozycje modeli. Kolumna `model_id` w rejestratorach poprawnie wskazuje na właściwe pozycje ze słownika.
| **DB-02** | Domyślny kanał starych świadectw wzorcowania | Baza posiada podpięte stare świadectwa dla urządzeń jednokanałowych | Odpytaj tabele świadectw wzorcowania | Każde świadectwo ze starego schematu bazy danych ma poprawnie wypełnioną nową kolumnę `channel_number = 1`.

## 2. Testy Słownika (Dictionary Management)

| ID | Tytuł Scenariusza | Kroki Testowe | Oczekiwany Rezultat |
| :--- | :--- | :--- | :--- |
| **DICT-01** | Dodanie modelu wielokanałowego | 1. Otwórz panel zarządzania modelami. <br> 2. Dodaj "Testo 176T4" <br> 3. Określ liczbę kanałów na 4. <br> 4. Zapisz. | Model poprawnie dodany. Widoczny w systemie i rozwijanych listach wyboru sprzętu. |
| **DICT-02** | Ukrywanie wycofanych modeli (Soft delete) | 1. Wybierz stary model "Testo 174T" <br> 2. Kliknij "Wycofaj / Dezaktywuj" | Model "Testo 174T" nie jest dostępny podczas rejestracji nowych urządzeń, jednak widnieje w historycznych rejestratorach posiadających go podpiętego. |

## 3. Testy Świadectw Wzorcowania i Kanałów

| ID | Tytuł Scenariusza | Kroki Testowe | Oczekiwany Rezultat |
| :--- | :--- | :--- | :--- |
| **CAL-01** | Dodanie 2 świadectw dla tego samego urządzenia, dla kanału 1 i 2. | 1. Otwórz profil urządzenia "Testo 176T4" (które ma w słowniku 4 kanały). <br> 2. Dodaj nowy certyfikat i wybierz "Kanał 1". Zapisz. <br> 3. Dodaj nowy certyfikat i wybierz "Kanał 2". Zapisz. | System zezwala na zapis i nie uznaje tego drugiego certyfikatu za "nadpisanie" poprzedniego. Obydwa mają status ważnego certyfikatu z osobną przypisaną datą i kanałem. |
| **CAL-02** | Test wymuszenia poprawności liczby kanałów | 1. Otwórz profil urządzenia jednokanałowego (np. Testo 174T, 1 kanał wg słownika). <br> 2. Spróbuj zmodyfikować numer kanału na "2" z poziomu warstwy logiki. | Próba przypisania certyfikatu do kanału nieistniejącego dla tego urządzenia jest blokowana wyrzuceniem walidacyjnego `BusinessException`. |

## 4. Testy Logiki Rewalidacji (Wizard Flow)

| ID | Tytuł Scenariusza | Kroki Testowe | Oczekiwany Rezultat |
| :--- | :--- | :--- | :--- |
| **WIZ-01** | Przypisanie tego samego kanału 2x w jednej sesji | 1. Uruchom nową rewalidację. <br> 2. Do narożnika Górny lewy przypisz Testo 176T4 (S/N 123) z Kanałem 1. <br> 3. Do narożnika Dolny Prawy spróbuj przypisać Testo 176T4 (S/N 123) również z Kanałem 1. | System rzuca błędem, informując że nie można użyć tego samego fizycznego kanału pomiarowego na dwóch różnych pozycjach w jednym czasie. |
| **WIZ-02** | Powodzenie mapowania 4 kanałów z 1 urządzenia | 1. Skonfiguruj 4 różne narożniki na użycie rejestratora Testo 176T4 (S/N 123), przydzielając na każdy inny numer kanału (Kanał 1, 2, 3 i 4). | Sesja zostaje poprawnie zwalidowana. Podsumowanie raportu podciąga 4 różne świadectwa odpowiednio do narożników. |
