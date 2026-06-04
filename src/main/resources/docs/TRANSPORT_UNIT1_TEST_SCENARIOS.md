# Test Plan / Scenarios: Ewidencja Urządzeń i Tras Transportowych (Jednostka 1)

Zestaw scenariuszy testowych weryfikujących operacje bazodanowe, logowanie audytowe (Audit Trail) oraz zachowanie ewidencji w pamięci.

---

## Scenariusz 1: Pomyślny Zapis Urządzenia Transportowego i Rejestracja Historii (Envers)

### Warunki początkowe (Setup):
1. Dostęp do czystej bazy danych testowych H2/MySQL.
2. Zalogowany użytkownik testowy: `admin_metrolog`.

### Kroki testowe:
1. Utwórz nowy obiekt `TransportUnit`:
   * Nazwa: *„Ford Transit – Chłodnia”*
   * Numer inwentarzowy: *„DEV-CAR-01”*
   * Numer rejestracyjny: *„WY 12345”*
   * Typ: `CAR_CHAMBER`
2. Zapisz obiekt w repozytorium `TransportUnitRepository`.
3. Dokonaj edycji obiektu (np. zmiana numeru rejestracyjnego na *„WY 99999”*) i ponownie zapisz.
4. Pobierz historię rewizji dla zapisanego obiektu za pomocą `AuditReader`.

### Oczekiwany wynik (Pass Criteria):
* Urządzenie zostało poprawnie zapisane z wygenerowanym ID.
* Druga operacja (update) powiodła się.
* W tabeli audytowej `transport_units_aud` znajdują się dokładnie 2 wpisy powiązane z rewizjami.
* Pierwsza rewizja zawiera stare dane, druga rewizja zawiera zaktualizowany numer rejestracyjny.
* Każda rewizja ma przypisanego użytkownika `admin_metrolog` oraz poprawny znacznik czasu.

---

## Scenariusz 2: Blokada Zapisu Zdublowanego Numeru Inwentarzowego

### Warunki początkowe (Setup):
1. W bazie danych istnieje już urządzenie o numerze inwentarzowym *„DEV-PORT-100”*.

### Kroki testowe:
1. Spróbuj utworzyć i zapisać nowe urządzenie transportowe o tej samej wartości pola `inventoryNumber` (*„DEV-PORT-100”*), ale innej nazwie.

### Oczekiwany wynik (Pass Criteria):
* Repozytorium/Baza danych wyrzuca wyjątek naruszenia unikalności klucza (`DataIntegrityViolationException`).
* Transakcja zostaje wycofana (rollback), a duplikat nie zostaje zapisany w bazie.

---

## Scenariusz 3: Walidacja Parametrów Trasy Transportowej

### Warunki początkowe (Setup):
1. Uruchomiony walidator beanów (`jakarta.validation.Validator`).

### Kroki testowe:
1. Spróbuj zapisać trasę `TransportRoute` z pustą nazwą lub z czasem przejazdu równym zero lub ujemnym.

### Oczekiwany wynik (Pass Criteria):
* Naruszenie zasad walidacji pól – walidator zgłasza błędy (np. `@Min(value = 1)` dla oczekiwanego czasu trwania trasy).
* Próba zapisu kończy się rzuceniem `ConstraintViolationException`.
