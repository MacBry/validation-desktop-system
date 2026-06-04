# Specyfikacja Techniczna - Moduł Audytu (21 CFR Part 11)

## 1. Cel
Zapewnienie pełnej śledzalności zmian w danych użytkowników oraz ich uprawnieniach, zgodnie z wymogami regulacyjnymi dla systemów walidacyjnych.

## 2. Architektura
Moduł oparty jest na **Hibernate Envers** oraz niestandardowej encji rewizji, która przechowuje informację o autorze zmiany.

### 2.1. Encje Audytowane
*   `User`: Śledzi zmiany pól takich jak username, email, active, locked, mustChangePassword.
*   `Role`: Śledzi definicje ról.
*   `user_roles` (Join Table): Śledzi przypisania ról do użytkowników.

### 2.2. Struktura Tabel Audytowych
*   `revinfo`: `rev` (PK), `revtstmp`, `modified_by` (Username autora zmiany).
*   `users_aud`: Lustrzane odbicie tabeli `users` + pola `rev`, `revtype`.
*   `roles_aud`: Lustrzane odbicie tabeli `roles` + pola `rev`, `revtype`.
*   `user_roles_aud`: Pola `rev`, `user_id`, `role_id`, `revtype`.

## 3. Kluczowe Mechanizmy

### 3.1. Śledzenie Autora (`UserRevisionListener`)
Klasa `UserRevisionListener` automatycznie pobiera nazwę zalogowanego użytkownika z `SecurityContextHolder` Spring Security podczas każdego zapisu do bazy i umieszcza ją w kolumnie `modified_by` tabeli `revinfo`.

### 3.2. Wymuszanie Rewizji
Ponieważ zmiany w samej relacji Many-to-Many nie zawsze generują nową rewizję dla encji nadrzędnej, w metodzie `updateUserRoles` (część `UserAccountService` od maja 2026, wcześniej `UserService`) dodano wymuszenie aktualizacji pola `updatedDate` użytkownika:
```java
user.setUpdatedDate(LocalDateTime.now());
```
**Uwaga:** Od maja 2026 ta logika znajduje się w `UserAccountService.updateUserRoles()` jako część refaktoryzacji UserService na cztery wyspecjalizowane serwisy.

### 3.3. Baseline Audytowy (Wsteczna Kompatybilność)
Aby umożliwić odczyt historii dla danych stworzonych przed włączeniem audytu, zastosowano mechanizm baseline (Migracja V8), który wstawia istniejące role do `roles_aud` z najniższą możliwą rewizją (`rev=1`).

## 4. Wyświetlanie Historii (`AuditService`)
Historia jest generowana poprzez porównywanie kolejnych stanów encji (`User`) pobranych z `AuditReader`. 
Dla każdego pola (Email, Aktywny, Uprawnienia itp.) system sprawdza różnicę między wersją N a N-1 i generuje wpis `UserAuditDto`.

### 4.1. Porównywanie Ról
Role są porównywane jako zbiory nazw (Set<String>). Zmiana ról jest wyświetlana jako jedno pole "Uprawnienia" ze starą i nową listą ról.

## 5. Migracje SQL
*   `V4`: Dodanie kolumny `modified_by` do `revinfo`.
*   `V5-V6`: Tworzenie tabel `roles_aud` i `user_roles_aud`.
*   `V7-V8`: Inicjalizacja danych bazowych w tabelach audytowych.
