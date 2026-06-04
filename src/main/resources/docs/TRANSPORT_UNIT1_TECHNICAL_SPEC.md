# Technical Specification: Ewidencja Urządzeń i Tras Transportowych (Jednostka 1)

## 1. Model Relacyjny i Migracje (MySQL)

Poniższy skrypt DDL przedstawia strukturę tabel słownikowych oraz powiązanych z nimi tabel historycznych Envers (`_aud`) w celu zapewnienia Audit Trail.

```sql
-- Słownik urządzeń transportowych
CREATE TABLE transport_units (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    inventory_number VARCHAR(50) NOT NULL UNIQUE,
    license_plate VARCHAR(30) NULL,
    transport_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE transport_units_aud (
    id BIGINT NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    name VARCHAR(200) NULL,
    inventory_number VARCHAR(50) NULL,
    license_plate VARCHAR(30) NULL,
    transport_type VARCHAR(50) NULL,
    is_active BOOLEAN NULL,
    PRIMARY KEY (id, REV)
);

-- Słownik tras transportowych
CREATE TABLE transport_routes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    route_code VARCHAR(50) NOT NULL UNIQUE,
    origin VARCHAR(200) NOT NULL,
    destination VARCHAR(200) NOT NULL,
    expected_duration_minutes INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE transport_routes_aud (
    id BIGINT NOT NULL,
    REV INTEGER NOT NULL,
    REVTYPE TINYINT,
    name VARCHAR(200) NULL,
    route_code VARCHAR(50) NULL,
    origin VARCHAR(200) NULL,
    destination VARCHAR(200) NULL,
    expected_duration_minutes INT NULL,
    is_active BOOLEAN NULL,
    PRIMARY KEY (id, REV)
);
```

---

## 2. Klasy Encji (JPA/Hibernate)

### 2.1. Klasa `TransportUnit.java`
```java
package com.mac.bry.desktop.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "transport_units")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransportUnit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "inventory_number", nullable = false, unique = true, length = 50)
    private String inventoryNumber;

    @Column(name = "license_plate", length = 30)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", nullable = false, length = 50)
    private TransportType transportType;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
```

### 2.2. Klasa `TransportRoute.java`
```java
package com.mac.bry.desktop.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "transport_routes")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransportRoute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "route_code", nullable = false, unique = true, length = 50)
    private String routeCode;

    @Column(nullable = false, length = 200)
    private String origin;

    @Column(nullable = false, length = 200)
    private String destination;

    @Column(name = "expected_duration_minutes", nullable = false)
    private Integer expectedDurationMinutes;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
```

### 2.3. Enum `TransportType.java`
```java
package com.mac.bry.desktop.model;

public enum TransportType {
    CAR_CHAMBER("Komora klimatyzowana w aucie"),
    PORTABLE_ACTIVE("Lodówka przenośna aktywna"),
    COOLER_BAG("Termotorba pasywna"),
    DRY_ICE_BOX("Pojemnik z suchym lodem");

    private final String displayName;
    TransportType(String displayName) {
        this.displayName = displayName;
    }
    public String getDisplayName() { return displayName; }
}
```

---

## 3. Repozytoria i Serwisy
W celu zarządzania zasobami z poziomu interfejsu JavaFX zostaną wdrożone standardowe repozytoria Spring Data JPA (`TransportUnitRepository`, `TransportRouteRepository`) oraz serwisy transakcyjne, które automatycznie logują transakcje zapisu w bazie danych.
