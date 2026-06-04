# Plan Implementacji - Moduł Audytu Zmian Użytkowników

## 1. Architektura i Konfiguracja
System wykorzystuje **Hibernate Envers**, który jest już skonfigurowany w projekcie (`@Audited` na encji `User`). Należy rozbudować mechanizm o powiązanie rewizji z zalogowanym użytkownikiem.

### 1.1. Konfiguracja Revision Listener
*   Stworzenie klasy `UserRevisionListener` implementującej `RevisionListener`.
*   Pobieranie aktualnie zalogowanego użytkownika z `SecurityContextHolder` i zapisywanie jego nazwy w tabeli rewizji (`REVINFO`).
*   Rozbudowa encji rewizji o pole `modifiedBy`.

## 2. Warstwa Backend (Logic)
1.  **DTO Audytu**: Stworzenie klasy `UserAuditDto` (timestamp, modifiedBy, operationType, propertyName, oldValue, newValue).
2.  **AuditService**:
    *   Wykorzystanie `AuditReaderFactory.get(entityManager)`.
    *   Metoda `getUserHistory(Long userId)` pobierająca rewizje dla danego użytkownika.
    *   Porównywanie stanów między rewizjami w celu wyciągnięcia konkretnych zmian w polach.

## 3. Warstwa UI (JavaFX)
1.  **Widok szczegółów (`user_audit.fxml`)**:
    *   Tabela (`TableView`) wyświetlająca historię rewizji.
    *   Kolumny: Data, Wykonał, Pole, Stara Wartość, Nowa Wartość.
2.  **Integracja z Panelem Admina**:
    *   Dodanie przycisku "Historia Audytu" w `admin_panel.fxml` (widoczny po wybraniu użytkownika z tabeli).
    *   Otwieranie nowego okna (Modal) z historią audytu dla wybranego ID.
3.  **Kontroler (`UserAuditController`)**:
    *   Inicjalizacja danych po otwarciu okna.
    *   Obsługa odświeżania.

## 4. Zadania do wykonania
- [x] Implementacja `RevisionListener` i rozbudowa `REVINFO` (Migracja V4).
- [x] Stworzenie `UserAuditDto` i `AuditService` z logiką porównywania pól.
- [x] Przygotowanie widoku `user_audit.fxml` i `UserAuditController`.
- [x] Podpięcie przycisku w `AdminPanelController`.
- [x] Implementacja pełnego audytowania ról (ManyToMany):
    - [x] Migracja V5-V6 (tabele audytowe ról).
    - [x] Migracja V7-V8 (baseline ról dla wstecznej kompatybilności).
- [x] Testy weryfikacyjne (potwierdzono poprawne wyświetlanie zmian roli i pól użytkownika).

## 5. Uwagi techniczne
*   **Audit Baseline**: Ze względu na to, że role istniały przed włączeniem audytu, konieczne było wstawienie ich do tabeli `roles_aud` z rewizją `rev=1` (Migracja V8).
*   **Revision Force**: Zmiana samej relacji ManyToMany nie zawsze wyzwalała rewizję Envers dla encji `User`, dlatego dodano wymuszenie aktualizacji pola `updatedDate` w metodzie `updateUserRoles` (od maja 2026 część `UserAccountService`, wcześniej `UserService`).
