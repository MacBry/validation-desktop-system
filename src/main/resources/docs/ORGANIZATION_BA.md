# Analiza Biznesowa (BA) - Struktura Organizacyjna (Działy i Pracownie)

## 1. Cel i Zakres
Celem modułu jest odwzorowanie struktury organizacyjnej jednostki (np. RCKiK) w aplikacji desktopowej. W przeciwieństwie do wersji webowej, aplikacja desktopowa obsługuje **tylko jedną główną jednostkę**, która jest konfigurowalna globalnie.

## 2. Model Hierarchii

Struktura opiera się na trójpoziomowej hierarchii:
1. **Jednostka Główna (Main Unit)**: Konfigurowana w pliku `application.yml`. Nie posiada własnej encji w bazie danych (dla uproszczenia), lecz jej dane są wstrzykiwane do raportów i nagłówków.
2. **Działy (Departments)**: Główne piony organizacyjne (np. Dział Laboratoryjny, Dział Ekspedycji).
3. **Pracownie (Laboratories)**: Jednostki podległe pod działy (np. Pracownia Immunologii, Pracownia Wirusologii).

## 3. Kluczowe Atrybuty

### 3.1. Dział (Department)
- **Nazwa**: Pełna nazwa działu.
- **Skrót**: Unikalny skrót (np. DL, DE) używany w generatorach numerów dokumentów.
- **Opis**: Pole opcjonalne.

### 3.2. Pracownia (Laboratory)
- **Nazwa**: Pełna nazwa pracowni.
- **Skrót**: Unikalny skrót (np. IMM, WIR).
- **Przynależność**: Każda pracownia musi być przypisana do dokładnie jednego działu.

## 4. Powiązania z innymi modułami

### 4.1. Użytkownicy
- Każdy użytkownik powinien mieć możliwość przypisania do **Pracowni** (opcjonalnie bezpośrednio do Działu, jeśli dana jednostka nie posiada podziału na pracownie).
- Przypisanie to będzie wykorzystywane przy automatycznym wypełnianiu metadanych w protokołach walidacji.

### 4.2. Numeracja Dokumentów (RPW)
- System numeracji powinien uwzględniać skróty działu/pracowni w celu zachowania ciągłości z systemem webowym (np. `RPW/DL/IMM/2026/001`).

## 5. Uproszczenia (Desktop vs Web)
- Rezygnacja z tabeli `companies`. Dane firmy są statyczne dla danej instancji aplikacji.
- Rezygnacja z multi-tenancy. Wszystkie dane należą do jednej organizacji.

## 6. Rola Administratora Działu (ROLE_DEPT_ADMIN)

Wprowadzenie nowej roli o pośrednim poziomie uprawnień:
- **Zakres**: Zarządzanie użytkownikami przypisanymi do tego samego działu (`department_id`).
- **Uprawnienia**:
    - Przeglądanie listy użytkowników swojego działu.
    - Aktywacja/dezaktywacja kont w ramach działu.
    - Resetowanie haseł użytkownikom swojego działu.
- **Ograniczenia**:
    - Brak wglądu w użytkowników z innych działów.
    - Brak możliwości nadawania roli `ROLE_SUPER_ADMIN`.
    - Brak dostępu do globalnych ustawień systemu i backupów.

## 7. Audyt i Historia Zmian
Wszelkie zmiany w przypisaniu użytkownika do Działu lub Pracowni muszą być rejestrowane w historii audytu:
- Rejestracja starej i nowej wartości (nazwa jednostki).
- Identyfikacja osoby dokonującej zmiany (Administratora).
- Możliwość eksportu historii zmian do PDF/CSV.
