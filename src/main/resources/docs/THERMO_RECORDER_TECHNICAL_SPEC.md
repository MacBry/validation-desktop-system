# Dokumentacja Implementacji: Rejestratory i Wzorcowanie

## 1. Architektura Danych

### 1.1. Model ERD (Kluczowe Encje)
System opiera się na relacji jeden-do-wielu pomiędzy Rejestratorem a jego Wzorcowaniami.

#### `ThermoRecorder` (Encja Główna)
- `id` (Long): PK
- `serialNumber` (String): Unique, index
- `model` (String): Model urządzenia (np. Testo 174T)
- `status` (Enum): `ACTIVE` (Aktywny) | `INACTIVE` (Nieaktywny) | `UNDER_CALIBRATION` (Wysłano do wzorcowania) | `DECOMMISSIONED` (Wyłączone z użytku)
- `resolution` (BigDecimal): Rozdzielczość cyfrowa (np. 0.1, 0.01)
- `department_id` (FK): Powiązanie z jednostką organizacyjną

#### `Calibration` (Historia Wzorcowań)
- `id` (Long): PK
- `thermo_recorder_id` (FK): Powiązanie z rejestratorem
- `calibrationDate` (LocalDate): Data wykonania
- `validUntil` (LocalDate): Data ważności (obliczana: `date + 12m`)
- `certificateNumber` (String): Numer świadectwa
- `certificateFilePath` (String): Link do dokumentu PDF

#### `CalibrationPoint` (Dane Metrologiczne)
- `id` (Long): PK
- `calibration_id` (FK): Powiązanie ze świadectwem
- `temperatureValue` (BigDecimal): Punkt wzorcowania
- `systematicError` (BigDecimal): Poprawka (błąd)
- `uncertainty` (BigDecimal): Niepewność rozszerzona (U)

## 2. Logika Biznesowa (Service Layer)

### 2.1. Zarządzanie Ważnością
Logic w `Calibration.java` wykorzystuje adnotacje JPA `@PrePersist` i `@PreUpdate` do automatycznego wyznaczania daty `validUntil`.
```java
public void calculateValidUntil() {
    if (calibrationDate != null && validUntil == null) {
        validUntil = calibrationDate.plusYears(1);
    }
}
```

### 2.2. Interpolacja Poprawek
Podczas procesowania danych z walidacji, system wyszukuje najbliższe punkty wzorcowania i stosuje poprawkę.
*   Jeśli temperatura pomiaru pokrywa się z punktem wzorcowania: stosowana jest bezpośrednia wartość `systematicError`.
*   Jeśli temperatura jest pomiędzy punktami: stosowana jest interpolacja liniowa.

## 3. Audyt i Wersjonowanie
Wszystkie encje są oznaczone adnotacją `@Audited` (Hibernate Envers). 
- Zmiany w parametrach metrologicznych są śledzone w tabelach `*_AUD`.
- Relacje są konfigurowane tak, aby usunięcie wzorcowania (orphan removal) było możliwe tylko w określonych warunkach (brak powiązań z raportami).

## 4. Bezpieczeństwo (Control Access)
Dostęp do rejestratorów jest filtrowany na poziomie `Repository` lub `Service` na podstawie przypisania użytkownika do `Department`.
Użytkownik widzi tylko rejestratory przypisane do jego działu lub pracowni.

## 5. UI / UX (Zalecenia implementacyjne)
- Listowanie rejestratorów powinno zawierać szybki podgląd statusu wzorcowania (ikonka tarczy: zielona/żółta/czerwona).
- Formularz wzorcowania zawiera mechanizm wyboru pliku PDF, który jest kopiowany do folderu `uploads/certificates/`.
- Tabela historii wzorcowań umożliwia bezpośrednie otwarcie załączonego świadectwa PDF za pomocą systemowej przeglądarki.
