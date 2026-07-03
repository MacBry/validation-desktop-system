# IMPL-EXC002: Klasyfikacja Ekskursji na Podstawie Wektora Propagacji Ciepła
## Implementation Specification — Propagation-Aware Excursion Classifier

| Pole | Wartość |
|---|---|
| Identyfikator | IMPL-EXC002 v1.0 |
| System | `validation-desktop-system` (JavaFX 21 / Spring Boot 3.2 / MySQL / Flyway / Hibernate Envers) |
| Powiązane dokumenty | BA-EXC002 v1.0, BA-DP001 v1.0, IMPL-DP001 v1.0 |
| Status | Draft |
| Data | 2026-06-25 |

---

## 1. Przegląd zmian

### 1.1 Zakres

Zastąpienie hardcoded logiki `isFrontPosition()` w `ExcursionDetector` mechanizmem opartym na:
1. Wyznaczeniu wektora propagacji ciepła z opóźnień reakcji czujników
2. Konfigurowalnej deklaracji pozycji nawiewu/ewaporatora per `CoolingChamber`
3. Walidacji krzyżowej obu sygnałów

### 1.2 Feature flag

```yaml
# application.yml
regime:
  detection:
    enabled: true                     # istniejąca flaga (Faza 1/2)
    propagation-aware: false          # NOWA — domyślnie wyłączone
    propagation:
      cosine-similarity-threshold: 0.7
      ambiguity-margin: 0.1
      min-sensors-for-vector: 3
      default-preset: REAR_WALL
```

Gdy `propagation-aware=false` → zachowanie identyczne z obecnym (`isFrontPosition()`).

---

## 2. Model domenowy

### 2.1 Nowy enum: `AirflowSourcePreset`

```java
package com.mac.bry.desktop.model.regime;

/**
 * Predefiniowana konfiguracja lokalizacji źródła nawiewu/ewaporatora
 * w komorze chłodniczej. Determinuje oczekiwany wektor propagacji
 * ciepła podczas cyklu defrostu.
 */
public enum AirflowSourcePreset {

    REAR_WALL("Tylna ściana", new double[]{0, -1, 0}),
    CEILING("Sufit (nawiew górny)", new double[]{0, 0, -1}),
    FLOOR("Podłoga (nawiew dolny)", new double[]{0, 0, 1}),
    LEFT_WALL("Lewa ściana", new double[]{1, 0, 0}),
    RIGHT_WALL("Prawa ściana", new double[]{-1, 0, 0}),
    REAR_AND_LEFT("Tył + lewa ściana", new double[]{0.707, -0.707, 0}),
    REAR_AND_CEILING("Tył + sufit", new double[]{0, -0.707, -0.707}),
    CUSTOM("Konfiguracja ręczna", null);

    private final String label;
    private final double[] expectedDefrostVector;

    AirflowSourcePreset(String label, double[] expectedDefrostVector) {
        this.label = label;
        this.expectedDefrostVector = expectedDefrostVector;
    }

    public String getLabel() { return label; }

    public double[] getExpectedDefrostVector() { return expectedDefrostVector; }

    /** Wektor drzwi — zawsze od przodu do tyłu (stała dla wszystkich konfiguracji) */
    public static double[] getDoorVector() { return new double[]{0, 1, 0}; }
}
```

### 2.2 Nowy utility: `GridPositionCoordinates`

```java
package com.mac.bry.desktop.model.regime;

import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import java.util.EnumMap;
import java.util.Map;

/**
 * Mapowanie pozycji GridPosition na znormalizowane współrzędne 3D
 * w układzie komory: x=[0,1] lewo→prawo, y=[0,1] przód→tył, z=[0,1] dół→góra.
 */
public final class GridPositionCoordinates {

    private static final Map<GridPosition, double[]> COORDS = new EnumMap<>(GridPosition.class);

    static {
        //                                    x     y     z
        COORDS.put(GridPosition.TOP_FRONT_LEFT,     new double[]{0.0, 0.0, 1.0});
        COORDS.put(GridPosition.TOP_FRONT_RIGHT,    new double[]{1.0, 0.0, 1.0});
        COORDS.put(GridPosition.TOP_BACK_LEFT,      new double[]{0.0, 1.0, 1.0});
        COORDS.put(GridPosition.TOP_BACK_RIGHT,     new double[]{1.0, 1.0, 1.0});
        COORDS.put(GridPosition.BOTTOM_FRONT_LEFT,  new double[]{0.0, 0.0, 0.0});
        COORDS.put(GridPosition.BOTTOM_FRONT_RIGHT, new double[]{1.0, 0.0, 0.0});
        COORDS.put(GridPosition.BOTTOM_BACK_LEFT,   new double[]{0.0, 1.0, 0.0});
        COORDS.put(GridPosition.BOTTOM_BACK_RIGHT,  new double[]{1.0, 1.0, 0.0});
    }

    private GridPositionCoordinates() {}

    public static double[] getCoordinates(GridPosition pos) {
        return COORDS.get(pos);
    }
}
```

### 2.3 Rozszerzenie encji `CoolingChamber`

```java
// Nowe pola w CoolingChamber.java

@Enumerated(EnumType.STRING)
@Column(name = "airflow_source_preset", length = 30)
@Builder.Default
private AirflowSourcePreset airflowSourcePreset = AirflowSourcePreset.REAR_WALL;

/**
 * Pozycje bliskiego pola źródła nawiewu — wypełniane tylko dla CUSTOM.
 * Przechowywane jako CSV (np. "TOP_BACK_LEFT,TOP_BACK_RIGHT").
 */
@Column(name = "custom_airflow_positions", length = 500)
private String customAirflowPositions;
```

### 2.4 Migracja Flyway

```sql
-- V30__AddAirflowSourceToChambers.sql

ALTER TABLE cooling_chambers
    ADD COLUMN airflow_source_preset VARCHAR(30) DEFAULT 'REAR_WALL' NOT NULL;

ALTER TABLE cooling_chambers
    ADD COLUMN custom_airflow_positions VARCHAR(500) DEFAULT NULL;

-- Tabela audytowa (Hibernate Envers)
ALTER TABLE cooling_chambers_aud
    ADD COLUMN airflow_source_preset VARCHAR(30);

ALTER TABLE cooling_chambers_aud
    ADD COLUMN custom_airflow_positions VARCHAR(500);
```

### 2.5 Rozszerzenie `RegimeDetectionProperties`

```java
// Nowe pola w RegimeDetectionProperties.java

/** Feature flag — propagation-aware classification */
private boolean propagationAware = false;

/** Minimalny cosine similarity do uznania kierunku za zgodny */
private double propagationCosineSimilarityThreshold = 0.7;

/** Margines niejednoznaczności — jeśli |cos_defrost - cos_door| < margin → EXCURSION */
private double propagationAmbiguityMargin = 0.1;

/** Minimalna liczba czujników z niezerowym lagiem do obliczenia wektora */
private int propagationMinSensorsForVector = 3;

/** Domyślny preset dla komór bez konfiguracji */
private AirflowSourcePreset propagationDefaultPreset = AirflowSourcePreset.REAR_WALL;
```

---

## 3. Nowy komponent: `PropagationVectorClassifier`

### 3.1 Odpowiedzialność

Wyznacza wektor propagacji ciepła z grupy nakładających się szpilek i klasyfikuje zdarzenie jako DEFROST / DOOR_EVENT / EXCURSION na podstawie cosine similarity z wektorem oczekiwanym.

### 3.2 Interfejs publiczny

```java
package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.config.RegimeDetectionProperties;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.regime.AirflowSourcePreset;
import com.mac.bry.desktop.model.regime.GridPositionCoordinates;
import com.mac.bry.desktop.model.regime.SegmentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class PropagationVectorClassifier {

    private final RegimeDetectionProperties props;

    /**
     * Wynik klasyfikacji przestrzennej.
     */
    public record ClassificationResult(
        SegmentType type,
        double confidence,
        double[] propagationVector,
        double cosineDefrost,
        double cosineDoor,
        String note
    ) {}

    /**
     * Dane wejściowe: pozycja czujnika i czas startu szpilki.
     */
    public record SpikeEvent(GridPosition position, LocalDateTime startTime) {}

    /**
     * Klasyfikuje grupę nakładających się szpilek na podstawie wektora propagacji.
     *
     * @param spikes       lista szpilek z pozycjami i czasami startu
     * @param preset       zadeklarowany preset źródła nawiewu
     * @param customPositions pozycje bliskiego pola (tylko dla CUSTOM), może być null
     * @return wynik klasyfikacji z confidence i wektorem
     */
    public ClassificationResult classify(
            List<SpikeEvent> spikes,
            AirflowSourcePreset preset,
            Set<GridPosition> customPositions) {
        // ...implementacja w sekcji 3.3
    }
}
```

### 3.3 Algorytm — pseudokod implementacji

```java
public ClassificationResult classify(
        List<SpikeEvent> spikes,
        AirflowSourcePreset preset,
        Set<GridPosition> customPositions) {

    if (spikes == null || spikes.size() < 2) {
        return new ClassificationResult(
            SegmentType.EXCURSION, 0.5, null, 0, 0,
            "Za mało czujników do analizy wektora propagacji");
    }

    // 1. Wyznacz t_min i oblicz lagi
    LocalDateTime tMin = spikes.stream()
        .map(SpikeEvent::startTime)
        .min(Comparator.naturalOrder())
        .orElseThrow();

    Map<GridPosition, Long> lags = new LinkedHashMap<>();
    for (SpikeEvent spike : spikes) {
        long lagMinutes = ChronoUnit.SECONDS.between(tMin, spike.startTime());
        lags.put(spike.position(), lagMinutes);
    }

    // 2. Pozycje z lag = 0 (źródło) i z max lag (cel)
    List<GridPosition> sourcePositions = new ArrayList<>();
    List<GridPosition> farPositions = new ArrayList<>();
    long maxLag = lags.values().stream().mapToLong(Long::longValue).max().orElse(0);

    for (Map.Entry<GridPosition, Long> entry : lags.entrySet()) {
        if (entry.getValue() == 0) {
            sourcePositions.add(entry.getKey());
        }
        if (entry.getValue() == maxLag && maxLag > 0) {
            farPositions.add(entry.getKey());
        }
    }

    // 3. Sprawdź czy mamy wystarczającą liczbę czujników
    long nonZeroLagCount = lags.values().stream().filter(v -> v > 0).count();
    if (nonZeroLagCount < props.getPropagationMinSensorsForVector() - 1) {
        // Fallback: jeśli za mało danych na wektor, użyj samej deklaracji
        return classifyByDeclarationOnly(sourcePositions, preset, customPositions);
    }

    // 4. Oblicz centroidy
    double[] centroidSource = computeCentroid(sourcePositions);
    double[] centroidFar = computeCentroid(farPositions);

    // 5. Wektor propagacji
    double[] vector = new double[]{
        centroidFar[0] - centroidSource[0],
        centroidFar[1] - centroidSource[1],
        centroidFar[2] - centroidSource[2]
    };
    double norm = Math.sqrt(vector[0]*vector[0] + vector[1]*vector[1] + vector[2]*vector[2]);

    if (norm < 0.1) {
        return new ClassificationResult(
            SegmentType.EXCURSION, 0.5, vector, 0, 0,
            "Wektor propagacji nierozróżnialny (wszystkie czujniki reagują jednocześnie)");
    }

    // Normalizacja
    vector[0] /= norm;
    vector[1] /= norm;
    vector[2] /= norm;

    // 6. Wektory referencyjne
    double[] expectedDefrost = resolveDefrostVector(preset, customPositions);
    double[] expectedDoor = AirflowSourcePreset.getDoorVector();

    // 7. Cosine similarity
    double cosDefrost = cosineSimilarity(vector, expectedDefrost);
    double cosDoor = cosineSimilarity(vector, expectedDoor);

    double threshold = props.getPropagationCosineSimilarityThreshold();
    double margin = props.getPropagationAmbiguityMargin();

    // 8. Klasyfikacja
    SegmentType type;
    double baseConfidence;
    String note;

    if (cosDefrost >= threshold && cosDefrost > cosDoor + margin) {
        type = SegmentType.DEFROST;
        baseConfidence = cosDefrost;
        note = String.format(
            "Wektor propagacji [%.2f, %.2f, %.2f] zgodny z kierunkiem defrostu (cos=%.3f)",
            vector[0], vector[1], vector[2], cosDefrost);
    } else if (cosDoor >= threshold && cosDoor > cosDefrost + margin) {
        type = SegmentType.DOOR_EVENT;
        baseConfidence = cosDoor;
        note = String.format(
            "Wektor propagacji [%.2f, %.2f, %.2f] zgodny z kierunkiem otwarcia drzwi (cos=%.3f)",
            vector[0], vector[1], vector[2], cosDoor);
    } else {
        type = SegmentType.EXCURSION;
        baseConfidence = Math.max(cosDefrost, cosDoor);
        note = String.format(
            "Wektor propagacji [%.2f, %.2f, %.2f] niejednoznaczny (cos_defrost=%.3f, cos_door=%.3f)",
            vector[0], vector[1], vector[2], cosDefrost, cosDoor);
    }

    // 9. Korekta confidence na podstawie zgodności z deklaracją
    double finalConfidence = adjustConfidenceByDeclaration(
        type, baseConfidence, sourcePositions, preset, customPositions);

    if (finalConfidence < baseConfidence - 0.2) {
        note += " | UWAGA: Wektor niezgodny z deklarowanym źródłem nawiewu ("
             + preset.getLabel() + ")";
    }

    return new ClassificationResult(type, finalConfidence, vector, cosDefrost, cosDoor, note);
}
```

### 3.4 Metody pomocnicze

```java
private double[] computeCentroid(List<GridPosition> positions) {
    double[] sum = new double[3];
    for (GridPosition pos : positions) {
        double[] coords = GridPositionCoordinates.getCoordinates(pos);
        sum[0] += coords[0];
        sum[1] += coords[1];
        sum[2] += coords[2];
    }
    int n = positions.size();
    return new double[]{sum[0] / n, sum[1] / n, sum[2] / n};
}

private double cosineSimilarity(double[] a, double[] b) {
    double dot = a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
    double normA = Math.sqrt(a[0]*a[0] + a[1]*a[1] + a[2]*a[2]);
    double normB = Math.sqrt(b[0]*b[0] + b[1]*b[1] + b[2]*b[2]);
    if (normA < 1e-9 || normB < 1e-9) return 0.0;
    return dot / (normA * normB);
}

private double[] resolveDefrostVector(
        AirflowSourcePreset preset, Set<GridPosition> customPositions) {
    if (preset == AirflowSourcePreset.CUSTOM && customPositions != null && !customPositions.isEmpty()) {
        // Oblicz wektor od centroidu CUSTOM pozycji do centroidu pozostałych
        Set<GridPosition> allPositions = EnumSet.allOf(GridPosition.class);
        Set<GridPosition> farField = EnumSet.copyOf(allPositions);
        farField.removeAll(customPositions);
        if (farField.isEmpty()) return new double[]{0, 0, 0};
        double[] cSource = computeCentroid(new ArrayList<>(customPositions));
        double[] cFar = computeCentroid(new ArrayList<>(farField));
        double[] v = {cFar[0]-cSource[0], cFar[1]-cSource[1], cFar[2]-cSource[2]};
        double norm = Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
        if (norm > 1e-9) { v[0]/=norm; v[1]/=norm; v[2]/=norm; }
        return v;
    }
    return preset.getExpectedDefrostVector();
}

private double adjustConfidenceByDeclaration(
        SegmentType classifiedType,
        double baseConfidence,
        List<GridPosition> sourcePositions,
        AirflowSourcePreset preset,
        Set<GridPosition> customPositions) {

    Set<GridPosition> expectedNearField = resolveNearFieldPositions(preset, customPositions);
    if (expectedNearField.isEmpty()) return baseConfidence;

    boolean sourceMatchesDeclaration = sourcePositions.stream()
        .anyMatch(expectedNearField::contains);

    if (classifiedType == SegmentType.DEFROST) {
        if (sourceMatchesDeclaration) {
            return Math.min(baseConfidence + 0.1, 1.0);
        } else {
            return Math.max(baseConfidence - 0.25, 0.3);
        }
    } else if (classifiedType == SegmentType.DOOR_EVENT) {
        if (!sourceMatchesDeclaration) {
            return Math.min(baseConfidence + 0.1, 1.0);
        } else {
            return Math.max(baseConfidence - 0.25, 0.3);
        }
    }
    return baseConfidence;
}

private Set<GridPosition> resolveNearFieldPositions(
        AirflowSourcePreset preset, Set<GridPosition> customPositions) {
    if (preset == AirflowSourcePreset.CUSTOM) {
        return customPositions != null ? customPositions : EnumSet.noneOf(GridPosition.class);
    }
    return switch (preset) {
        case REAR_WALL -> EnumSet.of(
            GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT,
            GridPosition.BOTTOM_BACK_LEFT, GridPosition.BOTTOM_BACK_RIGHT);
        case CEILING -> EnumSet.of(
            GridPosition.TOP_FRONT_LEFT, GridPosition.TOP_FRONT_RIGHT,
            GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT);
        case FLOOR -> EnumSet.of(
            GridPosition.BOTTOM_FRONT_LEFT, GridPosition.BOTTOM_FRONT_RIGHT,
            GridPosition.BOTTOM_BACK_LEFT, GridPosition.BOTTOM_BACK_RIGHT);
        case LEFT_WALL -> EnumSet.of(
            GridPosition.TOP_FRONT_LEFT, GridPosition.TOP_BACK_LEFT,
            GridPosition.BOTTOM_FRONT_LEFT, GridPosition.BOTTOM_BACK_LEFT);
        case RIGHT_WALL -> EnumSet.of(
            GridPosition.TOP_FRONT_RIGHT, GridPosition.TOP_BACK_RIGHT,
            GridPosition.BOTTOM_FRONT_RIGHT, GridPosition.BOTTOM_BACK_RIGHT);
        case REAR_AND_LEFT -> EnumSet.of(
            GridPosition.TOP_BACK_LEFT, GridPosition.BOTTOM_BACK_LEFT,
            GridPosition.TOP_BACK_RIGHT, GridPosition.BOTTOM_BACK_RIGHT,
            GridPosition.BOTTOM_FRONT_LEFT);
        case REAR_AND_CEILING -> EnumSet.of(
            GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT,
            GridPosition.TOP_FRONT_LEFT, GridPosition.TOP_FRONT_RIGHT,
            GridPosition.BOTTOM_BACK_LEFT, GridPosition.BOTTOM_BACK_RIGHT);
        default -> EnumSet.noneOf(GridPosition.class);
    };
}
```

---

## 4. Modyfikacja `ExcursionDetector`

### 4.1 Zmiana w `classifySpikes()` — sekcja B

Obecny kod (do zastąpienia):

```java
// B. Przestrzenna klasyfikacja nakładających się zdarzeń (Defrost vs Door Event)
List<List<PositionSpike>> overlappingGroups = groupOverlappingSpikes(rawSpikesMap);

for (List<PositionSpike> group : overlappingGroups) {
    if (group.size() > 1) {
        PositionSpike earliest = group.stream()
                .min(Comparator.comparing(ps -> ps.segment.getFromTimestamp()))
                .orElse(null);
        if (earliest != null) {
            boolean isFrontFirst = isFrontPosition(earliest.position);
            SegmentType type = isFrontFirst ? SegmentType.DOOR_EVENT : SegmentType.DEFROST;
            // ...
        }
    }
}
```

Nowy kod:

```java
// B. Przestrzenna klasyfikacja nakładających się zdarzeń
List<List<PositionSpike>> overlappingGroups = groupOverlappingSpikes(rawSpikesMap);

for (List<PositionSpike> group : overlappingGroups) {
    if (group.size() > 1) {
        if (props.isPropagationAware()) {
            classifyByPropagationVector(group, airflowSourcePreset, customPositions);
        } else {
            classifyByFrontPosition(group); // obecna logika — backward compat
        }
    } else if (group.size() == 1) {
        // ... bez zmian — reguła ≤20min/DOOR, >20min/EXCURSION
    }
}
```

### 4.2 Nowa metoda `classifyByPropagationVector()`

```java
private void classifyByPropagationVector(
        List<PositionSpike> group,
        AirflowSourcePreset preset,
        Set<GridPosition> customPositions) {

    List<PropagationVectorClassifier.SpikeEvent> events = group.stream()
        .map(ps -> new PropagationVectorClassifier.SpikeEvent(
            ps.position, ps.segment.getFromTimestamp()))
        .toList();

    PropagationVectorClassifier.ClassificationResult result =
        propagationClassifier.classify(events, preset, customPositions);

    for (PositionSpike ps : group) {
        ps.segment.setType(result.type());
        ps.segment.setConfidence(result.confidence());
        ps.segment.setNote(result.note());
    }
}
```

### 4.3 Zmiana sygnatury `detectAll()`

Obecna sygnatura:
```java
public Map<GridPosition, List<MeasurementSegment>> detectAll(
    Map<GridPosition, ThermoMeasurementSeries> allChannels)
```

Nowa sygnatura:
```java
public Map<GridPosition, List<MeasurementSegment>> detectAll(
    Map<GridPosition, ThermoMeasurementSeries> allChannels,
    AirflowSourcePreset airflowSourcePreset,
    Set<GridPosition> customAirflowPositions)
```

Overload zachowujący backward compatibility:
```java
public Map<GridPosition, List<MeasurementSegment>> detectAll(
    Map<GridPosition, ThermoMeasurementSeries> allChannels) {
    return detectAll(allChannels,
        AirflowSourcePreset.REAR_WALL,
        null);
}
```

### 4.4 Wyodrębnienie istniejącej logiki

Metoda `isFrontPosition()` pozostaje w kodzie, ale przeniesiona do prywatnej metody `classifyByFrontPosition()` — wywoływana tylko gdy `propagationAware=false`:

```java
private void classifyByFrontPosition(List<PositionSpike> group) {
    PositionSpike earliest = group.stream()
        .min(Comparator.comparing(ps -> ps.segment.getFromTimestamp()))
        .orElse(null);
    if (earliest != null) {
        boolean isFrontFirst = isFrontPosition(earliest.position);
        SegmentType type = isFrontFirst ? SegmentType.DOOR_EVENT : SegmentType.DEFROST;
        String note = isFrontFirst
            ? "Wykryto otwarcie drzwi (pierwsza reakcja: " + earliest.position.getLabel() + ")"
            : "Wykryto cykl odszraniania (pierwsza reakcja: " + earliest.position.getLabel() + ")";
        for (PositionSpike ps : group) {
            ps.segment.setType(type);
            ps.segment.setNote(note);
        }
    }
}
```

---

## 5. Modyfikacja `RegimeDetectionService`

### 5.1 Przekazywanie konfiguracji nawiewu

Zmiana w metodzie `detect()` — sekcja "Krok 3":

```java
// ── Krok 3: Detekcja i nakładanie ekskursji (Faza 2) ────────────────
if (series.getGridPosition() != null) {
    Map<GridPosition, ThermoMeasurementSeries> channels = new HashMap<>(allChannels);
    if (!channels.containsKey(series.getGridPosition())) {
        channels.put(series.getGridPosition(), series);
    }

    // NOWE: pobranie konfiguracji nawiewu z komory
    AirflowSourcePreset preset = resolvePreset(series);
    Set<GridPosition> customPositions = resolveCustomPositions(series);

    Map<GridPosition, List<MeasurementSegment>> allExcursions =
        excursionDetector.detectAll(channels, preset, customPositions);
    // ... reszta bez zmian
}
```

```java
private AirflowSourcePreset resolvePreset(ThermoMeasurementSeries series) {
    if (series.getCoolingChamber() != null
        && series.getCoolingChamber().getAirflowSourcePreset() != null) {
        return series.getCoolingChamber().getAirflowSourcePreset();
    }
    return props.getPropagationDefaultPreset();
}

private Set<GridPosition> resolveCustomPositions(ThermoMeasurementSeries series) {
    if (series.getCoolingChamber() == null) return null;
    String csv = series.getCoolingChamber().getCustomAirflowPositions();
    if (csv == null || csv.isBlank()) return null;
    Set<GridPosition> positions = EnumSet.noneOf(GridPosition.class);
    for (String s : csv.split(",")) {
        try {
            positions.add(GridPosition.valueOf(s.trim()));
        } catch (IllegalArgumentException e) {
            log.warn("Nieznana pozycja GridPosition w custom_airflow_positions: {}", s.trim());
        }
    }
    return positions;
}
```

---

## 6. Modyfikacja UI — `CoolingChamberDialogController`

### 6.1 Nowe kontrolki FXML

```xml
<!-- W pliku FXML dialogu edycji komory -->
<Label text="Źródło nawiewu / ewaporator:" GridPane.rowIndex="8"/>
<ComboBox fx:id="airflowSourceCombo" GridPane.rowIndex="8" GridPane.columnIndex="1"/>

<Label fx:id="customPositionsLabel" text="Pozycje bliskiego pola (CUSTOM):"
       GridPane.rowIndex="9" visible="false" managed="false"/>
<FlowPane fx:id="customPositionsPane" GridPane.rowIndex="9" GridPane.columnIndex="1"
          visible="false" managed="false">
    <!-- CheckBoxy generowane dynamicznie per GridPosition -->
</FlowPane>
```

### 6.2 Logika kontrolera

```java
@FXML
private ComboBox<AirflowSourcePreset> airflowSourceCombo;

@FXML
private FlowPane customPositionsPane;

@FXML
private Label customPositionsLabel;

private final Map<GridPosition, CheckBox> positionCheckboxes = new EnumMap<>(GridPosition.class);

@FXML
private void initialize() {
    // ... istniejąca inicjalizacja ...

    airflowSourceCombo.setItems(
        FXCollections.observableArrayList(AirflowSourcePreset.values()));
    airflowSourceCombo.setConverter(new StringConverter<>() {
        @Override
        public String toString(AirflowSourcePreset p) { return p != null ? p.getLabel() : ""; }
        @Override
        public AirflowSourcePreset fromString(String s) { return null; }
    });

    airflowSourceCombo.valueProperty().addListener((obs, old, val) -> {
        boolean showCustom = val == AirflowSourcePreset.CUSTOM;
        customPositionsLabel.setVisible(showCustom);
        customPositionsLabel.setManaged(showCustom);
        customPositionsPane.setVisible(showCustom);
        customPositionsPane.setManaged(showCustom);
    });

    for (GridPosition pos : GridPosition.values()) {
        CheckBox cb = new CheckBox(pos.getLabel());
        positionCheckboxes.put(pos, cb);
        customPositionsPane.getChildren().add(cb);
    }
}
```

---

## 7. Modyfikacja PDF — `RegimeAwareSectionRenderer`

### 7.1 Rozszerzenie notatki segmentu

Dla segmentów DEFROST i DOOR_EVENT wykrytych z `propagationAware=true`, notatka zawiera:
- Obliczony wektor propagacji `[dx, dy, dz]`
- Cosine similarity z defrostem i drzwiami
- Deklarowany preset źródła nawiewu
- Informację o sprzeczności (jeśli istnieje)

Przykład notatki w raporcie:
```
Wykryto cykl odszraniania. Wektor propagacji [0.00, 0.00, -0.98] 
zgodny z kierunkiem defrostu (cos=0.982). Deklarowane źródło: Sufit (nawiew górny).
Confidence: 0.92
```

---

## 8. Kolejność implementacji

| Krok | Komponent | Zależności | Estymacja |
|---|---|---|---|
| 1 | `AirflowSourcePreset` enum | Brak | 0.5h |
| 2 | `GridPositionCoordinates` utility | Brak | 0.5h |
| 3 | Flyway V30 migracja | Brak | 0.5h |
| 4 | Rozszerzenie `CoolingChamber` (pola + getter) | Krok 1, 3 | 0.5h |
| 5 | Rozszerzenie `RegimeDetectionProperties` | Krok 1 | 0.5h |
| 6 | `PropagationVectorClassifier` | Krok 1, 2, 5 | 3h |
| 7 | Modyfikacja `ExcursionDetector` | Krok 6 | 2h |
| 8 | Modyfikacja `RegimeDetectionService` | Krok 7 | 1h |
| 9 | Modyfikacja UI `CoolingChamberDialogController` | Krok 1, 4 | 2h |
| 10 | Modyfikacja `RegimeAwareSectionRenderer` (PDF) | Krok 6 | 1h |
| 11 | Testy jednostkowe `PropagationVectorClassifier` | Krok 6 | 3h |
| 12 | Testy integracyjne + regresyjne | Krok 7, 8 | 3h |
| **Suma** | | | **~18h** |
