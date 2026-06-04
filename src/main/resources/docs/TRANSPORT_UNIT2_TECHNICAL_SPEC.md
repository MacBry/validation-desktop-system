# Technical Specification: Logika GxP i Analiza Pomiarów Transportowych (Jednostka 2)

## 1. Algorytm Walidacji Temperatur i Limitów GxP

Logika weryfikacji zostanie wdrożona w dedykowanym serwisie `TransportValidationService.java`. Wejściem jest sesja transportowa, która posiada wykadrowany czas startu i końca.

```java
public class TransportValidationService {

    public TransportValidationResult validateSeries(
            ThermoMeasurementSeries series, 
            TransportType type, 
            double minLimit, 
            double maxLimit,
            LocalDateTime trimStart, 
            LocalDateTime trimEnd) {
            
        List<ThermoMeasurementPoint> filteredPoints = series.getMeasurements().stream()
                .filter(p -> !p.getTimestampLocal().isBefore(trimStart) && !p.getTimestampLocal().isAfter(trimEnd))
                .collect(Collectors.toList());

        boolean success = true;
        double minFound = Double.MAX_VALUE;
        double maxFound = -Double.MAX_VALUE;
        double sum = 0.0;
        List<String> violations = new ArrayList<>();

        for (ThermoMeasurementPoint pt : filteredPoints) {
            double temp = pt.getRawCelsius();
            if (temp < minFound) minFound = temp;
            if (temp > maxFound) maxFound = temp;
            sum += temp;

            if (temp < minLimit) {
                success = false;
                violations.add("Przekroczenie dolne: " + temp + "°C < " + minLimit + "°C o godz. " + pt.getTimestampLocal());
            }
            if (temp > maxLimit) {
                success = false;
                violations.add("Przekroczenie górne: " + temp + "°C > " + maxLimit + "°C o godz. " + pt.getTimestampLocal());
            }
        }

        double avg = filteredPoints.isEmpty() ? 0.0 : sum / filteredPoints.size();

        return TransportValidationResult.builder()
                .success(success)
                .minTemperature(minFound == Double.MAX_VALUE ? null : minFound)
                .maxTemperature(maxFound == -Double.MAX_VALUE ? null : maxFound)
                .avgTemperature(filteredPoints.isEmpty() ? null : avg)
                .violations(violations)
                .measurementsCount(filteredPoints.size())
                .build();
    }
}
```

---

## 2. Algorytm Wyznaczania Hold-Time (Awaria Zasilania)

Jeżeli w sesji zadeklarowano test awarii zasilania:
1. Użytkownik przesyła w parametrach czas odłączenia zasilania: `LocalDateTime powerOffTime`.
2. Urządzenie ma zadany limit maksymalny (np. $-18^\circ\text{C}$).
3. Algorytm iteruje po pomiarach następujących po `powerOffTime` i szuka pierwszego punktu, w którym `rawCelsius > maxLimit`.

```java
    public long calculateHoldTimeMinutes(
            ThermoMeasurementSeries series, 
            LocalDateTime powerOffTime, 
            double maxLimit) {
            
        List<ThermoMeasurementPoint> postFailurePoints = series.getMeasurements().stream()
                .filter(p -> !p.getTimestampLocal().isBefore(powerOffTime))
                .sorted(Comparator.comparing(ThermoMeasurementPoint::getTimestampLocal))
                .collect(Collectors.toList());

        LocalDateTime failureLimitReachedTime = null;

        for (ThermoMeasurementPoint pt : postFailurePoints) {
            if (pt.getRawCelsius() > maxLimit) {
                failureLimitReachedTime = pt.getTimestampLocal();
                break;
            }
        }

        if (failureLimitReachedTime == null) {
            // Temperatura nie przekroczyła limitu do końca rejestracji
            ThermoMeasurementPoint lastPoint = postFailurePoints.get(postFailurePoints.size() - 1);
            return java.time.Duration.between(powerOffTime, lastPoint.getTimestampLocal()).toMinutes();
        }

        return java.time.Duration.between(powerOffTime, failureLimitReachedTime).toMinutes();
    }
```

---

## 3. Reprezentacja Danych w Sesji Walidacyjnej

W modelu `TransportValidationSession` zostaną dodane następujące pola konfiguracji czasowej:
```java
public class TransportValidationSession {
    private Long id;
    private LocalDateTime trimStartTime;  // Początek właściwego transportu
    private LocalDateTime trimEndTime;    // Koniec właściwego transportu
    private boolean isPowerFailureTest;   // Czy sesja obejmowała test awarii zasilania
    private LocalDateTime powerFailureStartTime; // Moment wyłączenia prądu
    private Long calculatedHoldTimeMinutes; // Wynik kalkulacji Hold-Time
}
```
Pola te zostaną zapisane w tabeli `transport_validation_sessions` powiązanej z bazodanowym modelem sesji.
