# IMPL-DP001: Warstwa Interpretacji Reżimów Pracy
## Implementation Specification — Regime-Aware Interpretation Layer

| Pole | Wartość |
|---|---|
| Identyfikator | IMPL-DP001 v1.0 |
| System | `validation-desktop-system` (JavaFX 21 / Spring Boot 3.2 / MySQL / Flyway / Hibernate Envers) |
| Powiązane dokumenty | BA-DP001 v1.0, DP-001 v1.0 |
| Status | Do przeglądu |
| Data | 2026-06-21 |

---

## 1. Przegląd architektury

### 1.1 Podejście

Warstwa regime-aware jest wdrażana jako **ortogonalny komponent** — nie modyfikuje istniejących metod obliczeniowych (`MetrologicalStatsService.calculateStatistics()`), lecz dodaje:
1. Model domenowy segmentów (nowe encje + Flyway)
2. `RegimeDetectionService` — segmentacja algorytmiczna
3. `RegimeAwareStatsService` — statystyki warunkowe delegujące do istniejącego service
4. Rozszerzenie `RevalidationSession` o `RunMode` + listę segmentów
5. Aktualizacja rendererów PDF — dwa zestawy statystyk
6. Opcjonalny UI JavaFX — adnotacje i potwierdzanie segmentów

Podejście za **feature flagą** (`regime.detection.enabled=false`) zapewnia zerowe ryzyko regresji do czasu pełnej walidacji CSV detektora.

### 1.2 Diagram komponentów

```
┌─────────────────────────────────────────────────────────┐
│                  JavaFX Application                      │
│  ┌────────────────┐  ┌────────────────────────────────┐ │
│  │ SessionWizard  │  │  SegmentAnnotationController   │ │
│  │ (RunMode step) │  │  (human-in-the-loop UI)        │ │
│  └───────┬────────┘  └──────────────┬─────────────────┘ │
│          │                          │                    │
└──────────┼──────────────────────────┼────────────────────┘
           │                          │
┌──────────▼──────────────────────────▼────────────────────┐
│                     Service Layer                         │
│  ┌──────────────────────┐  ┌──────────────────────────┐  │
│  │ RegimeDetectionService│  │ RegimeAwareStatsService  │  │
│  │  - OlsSegmentor      │  │  - filterSteadyState()   │  │
│  │  - CusumDetector     │  │  - computeConditional()  │  │
│  │  - ExcursionDetector │  │  - deleguje do:          │  │
│  └──────────┬───────────┘  │    MetrologicalStatsSvc  │  │
│             │              └──────────────┬───────────┘  │
│             │                             │              │
│  ┌──────────▼─────────────────────────────▼───────────┐  │
│  │              RevalidationSession                    │  │
│  │   + RunMode runMode                                 │  │
│  │   + List<Segment> segments                          │  │
│  │   + Map<GridPos, ConditionalStatsDTO> conditionalStats │  │
│  └──────────────────────────┬────────────────────────┘  │
└─────────────────────────────┼────────────────────────────┘
                              │
┌─────────────────────────────▼────────────────────────────┐
│                   Persistence Layer                       │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐  │
│  │ Segment     │  │  MsAnnotation│  │ RevalidSession  │  │
│  │ @Entity     │  │  @Entity     │  │ (extended)      │  │
│  │ @Audited    │  │  @Audited    │  │ + runMode field │  │
│  └─────────────┘  └──────────────┘  └─────────────────┘  │
│           Flyway V28, V29 migrations                      │
└──────────────────────────────────────────────────────────┘
```

---

## 2. Model domenowy

### 2.1 Enumy

```java
// com.mac.bry.desktop.model.regime.RunMode
public enum RunMode {
    QUALIFICATION,    // formalna kwalifikacja IQ/OQ/PQ
    CHARACTERIZATION, // poznanie urządzenia, baseline
    MONITORING        // rutynowy nadzór operacyjny
}

// com.mac.bry.desktop.model.regime.SegmentType
public enum SegmentType {
    EQUILIBRATION,    // dochodzenie do nastawy (trend mono.)
    STEADY_STATE,     // stan ustalony (kryteria kwalifikacyjne)
    DEFROST,          // cykl rozmrażania (periodyczny, od ewaporatora)
    DOOR_EVENT,       // otwarcie drzwi (nieperiodyczne, przód pierwszy)
    SETPOINT_CHANGE,  // trwała zmiana poziomu (CUSUM), fastcooling
    EXCURSION,        // anomalia nieidentyfikowalna inaczej
    NORMAL_USE        // eksploatacja domowa (Characterization)
}

// com.mac.bry.desktop.model.regime.DetectionSource
public enum DetectionSource {
    ALGORITHM,  // wykryty automatycznie
    OPERATOR    // dodany/zmodyfikowany przez człowieka
}
```

### 2.2 Encje

```java
// com.mac.bry.desktop.model.regime.Segment
@Entity
@Table(name = "measurement_segments")
@EntityListeners(AuditingEntityListener.class)
@Audited
public class Segment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private ThermoMeasurementSeries series;

    @Column(nullable = false)
    private Instant fromTimestamp;

    @Column(nullable = false)
    private Instant toTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SegmentType type;

    /** Pewność detekcji algorytmicznej [0.0–1.0]; null dla OPERATOR */
    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DetectionSource source;

    /** Opcjonalna notatka operatora lub etykieta hipotezy przyczynowej */
    @Column(length = 500)
    private String note;

    /** Kto zatwierdził/odrzucił (human-in-the-loop) */
    private String confirmedBy;
    private Instant confirmedAt;

    /** false = odrzucony przez operatora (nie używany w statystykach) */
    @Column(nullable = false)
    private boolean accepted = true;
}
```

```java
// com.mac.bry.desktop.model.regime.SessionRunMode
// Rozszerzenie istniejącej ThermoMeasurementSeries lub jako osobna encja
@Entity
@Table(name = "session_run_modes")
@Audited
public class SessionRunMode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", unique = true)
    private ThermoMeasurementSeries series;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunMode runMode;

    private String declaredBy;
    private Instant declaredAt;
}
```

### 2.3 DTO

```java
// com.mac.bry.desktop.dto.regime.SegmentDto
public record SegmentDto(
    Instant from,
    Instant to,
    SegmentType type,
    Double confidence,
    DetectionSource source,
    String note,
    boolean accepted
) {}

// com.mac.bry.desktop.dto.stats.ConditionalStatsDTO
@Data @Builder
public class ConditionalStatsDTO {
    private String positionName;
    private String recorderSerialNumber;

    // Czy mamy wystarczająco dużo punktów STEADY_STATE (min 30 min)
    private boolean hasSteadyStateData;
    private int steadyStatePointCount;
    private double steadyStateCoveragePercent; // % przebiegu = STEADY_STATE

    // Statystyki na STEADY_STATE
    private double minSteady;
    private double maxSteady;
    private double avgSteady;
    private double medianSteady;
    private double stdDevSteady;
    private Double cpSteady;
    private Double cpkSteady;
    private double expandedUncertaintySteady;
    private double mktSteady;

    // Werdykt warunkowy
    private VerdictLevel verdictSteady;
    private String verdictNote;
}

// com.mac.bry.desktop.dto.regime.VerdictLevel
public enum VerdictLevel {
    PASS, WARNING, FINDING, FAIL, INCONCLUSIVE
}
```

---

## 3. Algorytmy detekcji

### 3.1 Segmentacja stanu ustalonego (Rolling OLS)

```java
// com.mac.bry.desktop.service.regime.OlsSegmentor

public class OlsSegmentor {

    // Parametry konfiguracyjne (do strojenia — DP-001 §4.4)
    private static final int WINDOW_MINUTES    = 30;   // okno regresji
    private static final double EPS_SLOPE      = 0.01; // °C/min — max nachylenie STEADY
    private static final double BAND_WIDTH     = 1.5;  // °C — max zakres STEADY_STATE
    private static final int MIN_STEADY_MINUTES = 30;  // min. czas trwania STEADY

    /**
     * Klasyfikuje każdą chwilę jako STEADY lub EQUILIBRATION.
     * Dane muszą być posortowane chronologicznie, próbkowanie równomierne (1/min zakładane).
     *
     * @param points Posortowane punkty pomiarowe (rawCelsius, timestamp)
     * @return Lista segmentów z przybliżonymi granicami
     */
    public List<Segment> segment(List<ThermoMeasurementPoint> points) {
        int n = points.size();
        boolean[] isSteady = new boolean[n];

        for (int i = WINDOW_MINUTES; i < n; i++) {
            List<ThermoMeasurementPoint> window =
                points.subList(i - WINDOW_MINUTES, i);

            double slope = computeOlsSlope(window);
            double band  = computeBandWidth(window);

            isSteady[i] = Math.abs(slope) < EPS_SLOPE && band < BAND_WIDTH;
        }

        return buildSegments(points, isSteady);
    }

    private double computeOlsSlope(List<ThermoMeasurementPoint> window) {
        int n = window.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = window.get(i).getRawCelsius();
            sumX += x; sumY += y; sumXY += x * y; sumX2 += x * x;
        }
        double denom = n * sumX2 - sumX * sumX;
        return denom == 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
    }

    private double computeBandWidth(List<ThermoMeasurementPoint> window) {
        double min = window.stream().mapToDouble(ThermoMeasurementPoint::getRawCelsius).min().orElse(0);
        double max = window.stream().mapToDouble(ThermoMeasurementPoint::getRawCelsius).max().orElse(0);
        return max - min;
    }

    private List<Segment> buildSegments(List<ThermoMeasurementPoint> points, boolean[] isSteady) {
        // Łączenie sąsiednich identycznych klasyfikacji w segmenty
        // + eliminacja segmentów STEADY_STATE krótszych niż MIN_STEADY_MINUTES
        // [implementacja merge algorytmu — standardowy run-length encoding]
        // ...
    }
}
```

### 3.2 Detekcja zmiany nastawy (CUSUM)

```java
// com.mac.bry.desktop.service.regime.CusumDetector

public class CusumDetector {

    private static final double K = 0.5;  // allowance parameter (multiples of sigma)
    private static final double H = 5.0;  // decision interval

    /**
     * Wykrywa trwałe przesunięcia poziomu średniej (SETPOINT_CHANGE, fastcooling).
     * Zwraca indeksy punktów gdzie stwierdzono zmianę.
     *
     * @param values  Tablica wartości temperatury [°C]
     * @param sigma   Odchylenie std. z fazy referencyjnej (np. z poprzedniego STEADY_STATE)
     * @return Lista par (startIdx, direction) — zmiana w górę lub w dół
     */
    public List<ChangePoint> detect(double[] values, double sigma) {
        List<ChangePoint> changes = new ArrayList<>();
        double cuSumPos = 0.0;
        double cuSumNeg = 0.0;
        double k = K * sigma;
        double h = H * sigma;
        double target = computeRunningMean(values, 0, Math.min(30, values.length));

        for (int i = 1; i < values.length; i++) {
            double xi = values[i];
            cuSumPos = Math.max(0, cuSumPos + xi - target - k);
            cuSumNeg = Math.max(0, cuSumNeg - xi + target - k);

            if (cuSumPos > h) {
                changes.add(new ChangePoint(i, Direction.UP));
                cuSumPos = 0;
                target = computeRunningMean(values, i, Math.min(i + 30, values.length));
            }
            if (cuSumNeg > h) {
                changes.add(new ChangePoint(i, Direction.DOWN));
                cuSumNeg = 0;
                target = computeRunningMean(values, i, Math.min(i + 30, values.length));
            }
        }
        return changes;
    }
}
```

### 3.3 Detekcja ekskursji (gradient + powrót)

```java
// com.mac.bry.desktop.service.regime.ExcursionDetector

public class ExcursionDetector {

    private static final double GRADIENT_THRESHOLD  = 0.5;  // °C/min — min. szybkość
    private static final double RETURN_WINDOW_MIN   = 60;   // max czas powrotu do baseline
    private static final double PERIODICITY_MIN_CYCLES = 2; // min. cykle → DEFROST

    /**
     * Wykrywa szpilki i klasyfikuje jako DEFROST vs DOOR_EVENT vs EXCURSION.
     *
     * Sygnatura DEFROST:
     *   - Gradient ≥0,5°C/min od ewaporatora (tylne czujniki pierwsze)
     *   - Powrót w czasie <40 min
     *   - Powtarzający się co ~4–12h (regularność FFT)
     *
     * Sygnatura DOOR_EVENT:
     *   - Gradient od czujników przednich/górnych
     *   - Czas powrotu <20 min
     *   - Nieregularny interwał
     */
    public List<Excursion> detect(
            Map<GridPosition, List<ThermoMeasurementPoint>> channelData) {
        // ...
    }
}
```

### 3.4 Facade — RegimeDetectionService

```java
// com.mac.bry.desktop.service.regime.RegimeDetectionService

@Service
@Slf4j
@RequiredArgsConstructor
public class RegimeDetectionService {

    private final OlsSegmentor olsSegmentor;
    private final CusumDetector cusumDetector;
    private final ExcursionDetector excursionDetector;

    @Value("${regime.detection.enabled:false}")
    private boolean enabled;

    /**
     * Przeprowadza pełną segmentację przebiegu dla pojedynczej serii pomiarowej.
     *
     * @param series      Seria z danymi pomiarowymi
     * @param allChannels Wszystkie kanały sesji (potrzebne do sygnatury przestrzennej)
     * @return DetectionResult z listą segmentów i zdarzeń
     */
    public DetectionResult detect(
            ThermoMeasurementSeries series,
            Map<GridPosition, ThermoMeasurementSeries> allChannels) {

        if (!enabled) {
            log.debug("Regime detection disabled (feature flag off)");
            return DetectionResult.disabled();
        }

        List<ThermoMeasurementPoint> points = series.getMeasurements();
        double[] values = points.stream()
                .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                .toArray();

        // 1. Segmentacja OLS (STEADY_STATE vs EQUILIBRATION)
        List<Segment> olsSegments = olsSegmentor.segment(points);

        // 2. CUSUM na całym przebiegu — nadpisuje EQUILIBRATION → SETPOINT_CHANGE
        double sigma = computeBaseSigma(olsSegments, values);
        List<ChangePoint> changePoints = cusumDetector.detect(values, sigma);
        applyChangePoints(olsSegments, changePoints);

        // 3. Detekcja ekskursji na segmentach STEADY_STATE
        List<Excursion> excursions = excursionDetector.detect(toChannelMap(allChannels));
        classifyExcursions(olsSegments, excursions);

        return DetectionResult.of(olsSegments, excursions);
    }
}
```

---

## 4. Statystyki warunkowe

```java
// com.mac.bry.desktop.service.regime.RegimeAwareStatsService

@Service
@RequiredArgsConstructor
public class RegimeAwareStatsService {

    private final MetrologicalStatsService metrologicalStatsService;
    private final CalibrationCorrectionService calibrationCorrectionService;

    /**
     * Oblicza statystyki WYŁĄCZNIE na punktach należących do segmentów STEADY_STATE.
     * Deleguje matematykę do MetrologicalStatsService — bez duplikacji logiki.
     */
    public ConditionalStatsDTO calculateConditionalStatistics(
            ThermoMeasurementSeries series,
            List<Segment> segments,
            Calibration calibration,
            Double lsl,
            Double usl) {

        // 1. Filtruj punkty do STEADY_STATE
        List<ThermoMeasurementPoint> steadyPoints =
                filterPointsToSteadyState(series.getMeasurements(), segments);

        String posName = series.getGridPosition() != null
                ? series.getGridPosition().name() : "UNKNOWN";
        String sn = series.getThermoRecorder() != null
                ? series.getThermoRecorder().getSerialNumber() : "UNKNOWN";

        if (steadyPoints.size() < 30) { // min. 30 minut danych
            log.warn("calculateConditionalStatistics [{}]: zbyt mało punktów STEADY_STATE: {}",
                    posName, steadyPoints.size());
            return ConditionalStatsDTO.builder()
                    .positionName(posName)
                    .recorderSerialNumber(sn)
                    .hasSteadyStateData(false)
                    .steadyStatePointCount(steadyPoints.size())
                    .build();
        }

        // 2. Zbuduj syntetyczną serię z przefiltrowanych punktów
        ThermoMeasurementSeries steadySeries = buildSyntheticSeries(series, steadyPoints);

        // 3. Oblicz statystyki (delegacja do istniejącego service)
        metrologicalStatsService.calculateStatistics(steadySeries);
        metrologicalStatsService.calculateExpandedUncertainty(steadySeries, calibration);

        // 4. Wyznacz SPC vs LSL/USL
        double[] raw = steadyPoints.stream()
                .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                .toArray();
        Double cpSteady = null, cpkSteady = null;
        if (lsl != null && usl != null) {
            var capability = SpcEngine.calculateCapability(raw, lsl, usl);
            cpSteady  = capability.getCp();
            cpkSteady = capability.getCpk();
        }

        // 5. Werdykt warunkowy
        double steadyCoveragePercent =
                (double) steadyPoints.size() / series.getMeasurements().size() * 100.0;

        VerdictLevel verdict = computeVerdict(steadySeries, cpkSteady, lsl, usl);

        return ConditionalStatsDTO.builder()
                .positionName(posName)
                .recorderSerialNumber(sn)
                .hasSteadyStateData(true)
                .steadyStatePointCount(steadyPoints.size())
                .steadyStateCoveragePercent(steadyCoveragePercent)
                .minSteady(steadySeries.getMinTemperature())
                .maxSteady(steadySeries.getMaxTemperature())
                .avgSteady(steadySeries.getAvgTemperature())
                .stdDevSteady(steadySeries.getStdDeviation())
                .cpSteady(cpSteady)
                .cpkSteady(cpkSteady)
                .expandedUncertaintySteady(steadySeries.getExpandedUncertainty())
                .verdictSteady(verdict)
                .build();
    }

    private List<ThermoMeasurementPoint> filterPointsToSteadyState(
            List<ThermoMeasurementPoint> points, List<Segment> segments) {
        Set<Segment> steadySegments = segments.stream()
                .filter(s -> s.getType() == SegmentType.STEADY_STATE && s.isAccepted())
                .collect(Collectors.toSet());

        return points.stream()
                .filter(p -> steadySegments.stream()
                        .anyMatch(s -> !p.getMeasurementTime().isBefore(s.getFromTimestamp())
                                    && !p.getMeasurementTime().isAfter(s.getToTimestamp())))
                .toList();
    }
}
```

---

## 5. Migracje Flyway

### V28 — model domenowy segmentów

```sql
-- V28__AddMeasurementSegments.sql

CREATE TABLE measurement_segments (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    series_id          BIGINT       NOT NULL,
    from_timestamp     DATETIME(3)  NOT NULL,
    to_timestamp       DATETIME(3)  NOT NULL,
    type               VARCHAR(30)  NOT NULL,
    confidence         DOUBLE,
    source             VARCHAR(20)  NOT NULL DEFAULT 'ALGORITHM',
    note               VARCHAR(500),
    confirmed_by       VARCHAR(100),
    confirmed_at       DATETIME(3),
    accepted           TINYINT(1)   NOT NULL DEFAULT 1,
    created_date       DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_segment_series FOREIGN KEY (series_id)
        REFERENCES thermo_measurement_series(id) ON DELETE CASCADE,
    INDEX idx_segment_series (series_id),
    INDEX idx_segment_type   (type),
    INDEX idx_segment_time   (from_timestamp, to_timestamp)
);

-- Tabela audytowa Envers
CREATE TABLE measurement_segments_aud (
    id             BIGINT      NOT NULL,
    rev            INT         NOT NULL,
    revtype        TINYINT,
    series_id      BIGINT,
    from_timestamp DATETIME(3),
    to_timestamp   DATETIME(3),
    type           VARCHAR(30),
    confidence     DOUBLE,
    source         VARCHAR(20),
    note           VARCHAR(500),
    confirmed_by   VARCHAR(100),
    confirmed_at   DATETIME(3),
    accepted       TINYINT(1),
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_seg_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
```

### V29 — RunMode na serii

```sql
-- V29__AddRunModeToMeasurementSeries.sql

CREATE TABLE session_run_modes (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    series_id    BIGINT       NOT NULL UNIQUE,
    run_mode     VARCHAR(20)  NOT NULL DEFAULT 'CHARACTERIZATION',
    declared_by  VARCHAR(100),
    declared_at  DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_runmode_series FOREIGN KEY (series_id)
        REFERENCES thermo_measurement_series(id) ON DELETE CASCADE
);

CREATE TABLE session_run_modes_aud (
    id          BIGINT      NOT NULL,
    rev         INT         NOT NULL,
    revtype     TINYINT,
    series_id   BIGINT,
    run_mode    VARCHAR(20),
    declared_by VARCHAR(100),
    declared_at DATETIME(3),
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_runmode_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
```

---

## 6. Integracja z PDF Rendererami

### 6.1 Rozszerzenie RevalidationSession

```java
// Dodanie do RevalidationSession:

/** Tryb runu — deklarowany przez operatora */
@Builder.Default
private RunMode runMode = RunMode.CHARACTERIZATION;

/** Mapa segmentów per pozycja (klucz: GridPosition, wartość: wykryte segmenty) */
@Builder.Default
private Map<GridPosition, List<Segment>> detectedSegments = new HashMap<>();

/** Statystyki warunkowe (STEADY_STATE only) */
@Builder.Default
private Map<GridPosition, ConditionalStatsDTO> conditionalStatsMap = new HashMap<>();
```

### 6.2 Pre-compute w RevalidationReportPdfRenderer

```java
// Dodanie przed pętlą renderowania (analogicznie do correctedStatsMap):

if (featureFlags.isRegimeDetectionEnabled()) {
    for (GridPosition pos : activePositions) {
        PositionData d = session.getAssignedPositions().get(pos);
        if (d != null && d.getSeries() != null) {
            // Segmentacja
            DetectionResult detection = regimeDetectionService.detect(
                d.getSeries(), collectAllSeries(session));
            session.getDetectedSegments().put(pos, detection.getSegments());

            // Statystyki warunkowe
            ConditionalStatsDTO conditional = regimeAwareStatsService
                .calculateConditionalStatistics(
                    d.getSeries(),
                    detection.getSegments(),
                    d.getLatestCalibration(),
                    lsl, usl);
            session.getConditionalStatsMap().put(pos, conditional);
        }
    }
}
```

### 6.3 Nowy renderer sekcji — RegimeAwareSectionRenderer

Nowa sekcja PDF (4.0 — przed §4.1) prezentuje:
- Oś czasu z kolorowymi segmentami (STEADY_STATE=zielony, EQUILIBRATION=żółty, DEFROST=niebieski, DOOR=szary)
- Tabelę: pozycja | % STEADY_STATE | Cpk(cały) | Cpk(STEADY) | Werdykt
- Log zdarzeń (zastępuje listę naruszeń Nelsona)

---

## 7. Plan wdrożenia fazowego

### Faza 1 — Fundament (V28, V29, OLS, CUSUM, statystyki warunkowe)

| Krok | Plik | Priorytet |
|---|---|---|
| V28 Flyway — `measurement_segments` | `V28__AddMeasurementSegments.sql` | 1 |
| V29 Flyway — `session_run_modes` | `V29__AddRunModeToMeasurementSeries.sql` | 1 |
| Enumy: `RunMode`, `SegmentType`, `DetectionSource` | `model/regime/` | 1 |
| Encja `Segment` + `SessionRunMode` + Repos | `model/regime/` | 1 |
| `OlsSegmentor` | `service/regime/` | 1 |
| `CusumDetector` | `service/regime/` | 1 |
| `RegimeDetectionService` (facade, za flagą) | `service/regime/` | 1 |
| `ConditionalStatsDTO` | `dto/stats/` | 1 |
| `RegimeAwareStatsService` | `service/regime/` | 1 |
| Rozszerzenie `RevalidationSession` | `model/` | 1 |
| Pre-compute w `RevalidationReportPdfRenderer` | `service/pdf/` | 1 |
| `RegimeAwareSectionRenderer` (tabela warunkowa) | `service/pdf/section/` | 1 |

### Faza 2 — Detekcja zdarzeń

| Krok | Plik |
|---|---|
| `ExcursionDetector` (gradient + FFT periodyczność) | `service/regime/` |
| Klasyfikacja przestrzenna (drzwi vs defrost) | `service/regime/` |
| Log zdarzeń w PDF (zastępuje §4.3 Nelsona) | `service/pdf/section/` |

### Faza 3 — Polityka werdyktu

| Krok | Plik |
|---|---|
| `VerdictPolicy` interface + implementacje | `service/regime/verdict/` |
| `QualificationPolicy`, `CharacterizationPolicy` | `service/regime/verdict/` |
| Integracja z `MetrologicalSectionRenderer` | `service/pdf/section/` |

### Faza 4 — UI JavaFX

| Krok | Plik |
|---|---|
| `SegmentAnnotationController` + FXML | `controller/` + `view/` |
| Timeline widget (JavaFX Canvas) | `ui/widget/` |

### Faza 5 — Generator hipotez

| Krok | Plik |
|---|---|
| `CausalHypothesisGenerator` | `service/regime/` |
| Integracja z PDF — zdania per zdarzenie | `service/pdf/section/` |

---

## 8. Feature Flag

```yaml
# application.properties / application.yml
regime:
  detection:
    enabled: false          # CHANGE TO true AFTER CSV VALIDATION OF DETECTOR
    ols-window-minutes: 30
    ols-eps-slope: 0.01
    ols-band-width: 1.5
    ols-min-steady-minutes: 30
    cusum-k: 0.5
    cusum-h: 5.0
    excursion-gradient-threshold: 0.5
    excursion-return-window-minutes: 60
```

```java
// com.mac.bry.desktop.config.RegimeDetectionProperties
@ConfigurationProperties(prefix = "regime.detection")
@Component
@Data
public class RegimeDetectionProperties {
    private boolean enabled = false;
    private int olsWindowMinutes = 30;
    private double olsEpsSlope = 0.01;
    private double olsBandWidth = 1.5;
    private int olsMinSteadyMinutes = 30;
    private double cusumK = 0.5;
    private double cusumH = 5.0;
    private double excursionGradientThreshold = 0.5;
    private int excursionReturnWindowMinutes = 60;
}
```

---

## 9. Zależności

Brak nowych dependencji Maven — algorytmy OLS i CUSUM implementowane natywnie. Jeśli w przyszłości wymagana jest FFT (periodyczność defrostu), dostępna jest `commons-math3` (już w classpath via Apache POI).
