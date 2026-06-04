# Analiza Biznesowa (BA) - Moduł Audytu Zmian Użytkowników

## 1. Cel Biznesowy
Zapewnienie pełnej rozliczalności i zgodności z wymaganiami **GAMP 5** oraz **21 CFR Part 11** w zakresie nadzoru nad kontami użytkowników. Każda zmiana uprawnień, statusu konta czy danych osobowych musi pozostawiać trwały i czytelny ślad (Audit Trail), pozwalający na odtworzenie historii zmian.

## 2. Wymagania Funkcjonalne

### 2.1. Rejestracja Zdarzeń (Audit Logging)
*   **REQ-AU-01**: System musi automatycznie rejestrować każdą zmianę w encji użytkownika (zmiana ról, aktywacja/dezaktywacja, blokada, zmiana danych osobowych).
*   **REQ-AU-02**: Log audytowy musi zawierać:
    *   Datę i godzinę zmiany.
    *   Identyfikator użytkownika dokonującego zmiany (np. administratora).
    *   Typ operacji (Utworzenie, Modyfikacja).
    *   Wartość przed zmianą i po zmianie (dla kluczowych pól).
*   **REQ-AU-03**: Dane audytowe muszą być nienaruszalne (brak możliwości edycji lub usunięcia logów z poziomu aplikacji).

### 2.2. Przeglądanie Historii (Audit Viewer)
*   **REQ-AU-04**: Administrator (ROLE_SUPER_ADMIN) musi mieć możliwość wyświetlenia pełnej historii zmian dla wybranego użytkownika z poziomu Panelu Administratora.
*   **REQ-AU-05**: Historia powinna być prezentowana w formie czytelnej tabeli z możliwością sortowania od najnowszych zdarzeń.
*   **REQ-AU-06**: Szczegóły audytu powinny jasno wskazywać, co konkretnie zostało zmienione (np. "Zmiana roli: dodano ROLE_QA").

## 3. Zgodność z Regulacjami
*   **Integritet danych**: Wykorzystanie mechanizmu Hibernate Envers gwarantuje spójność logów z danymi w bazie.
*   **Rozliczalność**: Powiązanie każdej rewizji z użytkownikiem (Adminem) zalogowanym w momencie dokonywania zmiany.

## 4. Wykluczenia
*   Eksport logów do formatu PDF/Excel zostanie zrealizowany w osobnym etapie raportowania globalnego.
