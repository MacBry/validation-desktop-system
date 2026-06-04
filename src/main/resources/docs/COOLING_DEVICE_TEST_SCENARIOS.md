# Scenariusze Testowe (QA / BA): Urządzenia Wielokomorowe

Dokumentacja scenariuszy testowych weryfikujących poprawność biznesową (BA) i jakościową (QA) wdrożonego modelu relacyjnego 1:N urządzeń chłodniczych (jedno- i wielokomorowych) w aplikacji **VCC Desktop**.

---

## 1. Scenariusze Testów Biznesowych i UAT (Manualne / Funkcjonalne)

### Scenariusz UAT-1: Podział uprawnień (RBAC) w widoku Master-Detail
*   **Aktorzy:** Operator (`ROLE_USER`), Administrator (`ROLE_SUPER_ADMIN` lub `ROLE_DEPT_ADMIN`)
*   **Krok 1:** Zaloguj się jako Operator. Przejdź do zakładki *„Ewidencja Urządzeń Chłodniczych”*.
    *   *Oczekiwany rezultat:* Wyświetla się profesjonalny widok podzielony (`SplitPane`). W górnej tabeli (Master) widać listę fizycznych urządzeń i nową kolumnę *„Liczba komór”*. Dolna tabela (Detail) jest pusta. Przycisk *„Dodaj Urządzenie”* jest niewidoczny i niezarządzany. 
*   **Krok 2:** Wybierz z tabeli górnej dowolne urządzenie chłodnicze (np. dwukomorowe).
    *   *Oczekiwany rezultat:* Dolna tabela (Detail) natychmiast zapełnia się listą komór przypisanych do wybranego urządzenia. Widoczne są szczegółowe parametry: typ komory, zakres roboczy, pojemność, przechowywany materiał oraz wyliczona klasa PDA TR-64 (np. *Klasa S / 9 pkt*). W kolumnie akcji w tabeli górnej jedyne dostępne przyciski to *„Podgląd”* i *„Audit”*.
*   **Krok 3:** Zaloguj się jako Administrator. Przejdź do tej samej zakładki.
    *   *Oczekiwany rezultat:* Przycisk *„Dodaj Urządzenie”* jest w pełni widoczny. W kolumnie akcji tabeli górnej (Master) dostępne są przyciski *„Edytuj”*, *„Usuń”* oraz *„Audit”*.

---

### Scenariusz UAT-2: Zabezpieczenie biznesowe walidacji (Reguła "Przynajmniej 1 komora")
*   **Aktor:** Administrator
*   **Krok 1:** Kliknij przycisk *„Dodaj Urządzenie”*. Wprowadź poprawny numer inwentarzowy, nazwę, wybierz dział i pracownię.
*   **Krok 2:** Sprawdź tabelę komór na dole kreatora. Tabela jest pusta. Kliknij przycisk *„Zapisz urządzenie”*.
    *   *Oczekiwany rezultat:* System blokuje operację zapisu i wyświetla okno ostrzeżenia (Alert.ERROR): **„Urządzenie chłodnicze musi posiadać co najmniej jedną zdefiniowaną komorę!”**. Dane nie zostają wysłane do serwera.
*   **Krok 3:** W sekcji komór kliknij *„+ Dodaj komorę”*. Wprowadź parametry pojedynczej komory (nazwa, typ chłodniczy, zakres temperatur, objętość) i zatwierdź.
    *   *Oczekiwany rezultat:* Nowa komora pojawia się w tabeli kreatora.
*   **Krok 4:** Kliknij przycisk *„Zapisz urządzenie”*.
    *   *Oczekiwany rezultat:* Urządzenie wraz ze zdefiniowaną komorą zostaje pomyślnie zapisane w bazie danych. Kreator zamyka się, a na liście głównej pojawia się nowo utworzony rekord ze wskazaniem *Liczby komór = 1*.

---

### Scenariusz UAT-3: Dynamiczna walidacja metrologiczna PDA TR-64 w edycji komory
*   **Aktor:** Administrator
*   **Krok 1:** W oknie edycji urządzenia kliknij *„+ Dodaj komorę”* lub wybierz istniejącą i kliknij *„Edytuj”*.
*   **Krok 2:** W oknie konfiguracji komory zmień wartość objętości na `1.5` m³.
    *   *Oczekiwany rezultat:* System w czasie rzeczywistym wyświetla podpowiedź o przypisaniu do **„Klasy S (≤ 2 m³) / 9 punktów pomiarowych”**.
*   **Krok 3:** Zmień wartość objętości na `12.0` m³.
    *   *Oczekiwany rezultat:* Podpowiedź automatycznie zmienia się na **„Klasę M (2–20 m³) / 15 punktów pomiarowych”**.
*   **Krok 4:** Zmień wartość objętości na `35.0` m³.
    *   *Oczekiwany rezultat:* Podpowiedź zmienia się na **„Klasę L (> 20 m³) / 27 punktów pomiarowych”**.

---

## 2. Automatyczne Testy Integracyjne (JUnit 5 + Spring Boot)

Wszystkie scenariusze integracyjne i audytowe są automatycznie weryfikowane przy każdym budowaniu aplikacji w klasie [CoolingDeviceAuditIntegrationTest](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/test/java/com/mac/bry/desktop/integration/CoolingDeviceAuditIntegrationTest.java).

### Test A: `shouldAuditMaterialTypeCreationAndModifications`
*   **Cel:** Weryfikacja audytu zmian (Envers) dla słownika kategorii materiałów.
*   **Weryfikacja:** Test sprawdza utworzenie materiału, poprawność zapisu rewizji typu `UTWORZENIE`, modyfikację nazwy, temperatury minimalnej oraz energii aktywacji, a na koniec sprawdza poprawność wygenerowanego diffu w serwisie audytu.

### Test B: `shouldAuditCoolingDeviceCreationAndModifications`
*   **Cel:** Weryfikacja spójności i transakcyjności audytu relacyjnego w strukturze 1:N (rodzic-dziecko).
*   **Kroki testu:**
    1. Tworzy fizyczne urządzenie (`CoolingDevice`) i dodaje do niego komorę podrzędną (`CoolingChamber` o nazwie "Komora Główna").
    2. Zapisuje strukturę transakcyjnie (`saveAndFlush`).
    3. Weryfikuje powstanie początkowej rewizji `UTWORZENIE` z autorem `device_audit_tester`.
    4. Modyfikuje nazwę fizycznego urządzenia oraz precyzyjne parametry metrologiczne podrzędnej komory (zakres temperatur, pojemność, materiał, typ chłodniczy).
    5. Zapisuje zmiany (`saveAndFlush`) i czyści cache JPA.
    6. Odpytuje serwis audytu o historię rodzica.
    7. **Weryfikacja asercji:** Test potwierdza, że serwis audytu połączył zmiany z obu tabel (`cooling_devices_aud` i `cooling_chambers_aud`) i wygenerował skonsolidowane różnice rewizji, w tym precyzyjnie zalogował zmianę parametrów podrzędnej komory z prefiksem `Komora (Komora Główna) - Temp Min`, wartość stara `2.0`, wartość nowa `-80.0`.
