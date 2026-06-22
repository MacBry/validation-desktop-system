# TEST-DP001: Pakiet Testów — Warstwa Interpretacji Reżimów Pracy
## Test Package — Regime-Aware Interpretation Layer

| Pole | Wartość |
|---|---|
| Identyfikator | TEST-DP001 v1.0 |
| System | `validation-desktop-system` |
| Powiązane dokumenty | BA-DP001 v1.0, IMPL-DP001 v1.0, DP-001 v1.0 |
| Framework testowy | JUnit 5 + AssertJ + Mockito (Spring Boot Test) |
| Status | Draft |
| Data | 2026-06-21 |

---

## 1. Strategia testowania

### 1.1 Piramida testów

```
                    ┌──────────┐
                    │  E2E (3) │  ← Pełny przebieg → PDF
                    ├──────────┤
              ┌─────┤Integration│──────┐
              │     │  (12)     │      │
              │     └──────────┘      │
         ┌────┤──────────────────────────┐
         │    │    Unit Tests (45+)       │
         │    │ OlsSegmentor, CUSUM,     │
         │    │ ExcursionDetector,       │
         │    │ RegimeAwareStatsService  │
         └────┴──────────────────────────┘
```

### 1.2 Zasady

- **Determinizm**: Ten sam zestaw danych wejściowych ZAWSZE daje ten sam wynik — brak `ThreadLocalRandom`, brak `System.currentTimeMillis()` w algorytmach.
- **Syntetyczne dane testowe**: Wszystkie dane wejściowe dla unit testów generowane programowo (nie z bazy) — pełna kontrola.
- **Dane referencyjne (2026-06-21)**: Przypadki integracyjne/E2E używają rzeczywistych danych sesji lodówko-zamrażarki Amica jako golden set.
- **Każdy test ma udokumentowane kryterium akceptacji** (BA-DP001 §7).

---

## 2. Testy jednostkowe — OlsSegmentor

```java
// src/test/java/.../service/regime/OlsSegmentorTest.java
package com.mac.bry.desktop.service.regime;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class OlsSegmentorTest {

    private OlsSegmentor segmentor;

    @BeforeEach
    void setUp() {
        segmentor = new OlsSegmentor();
    }

    // ──────────────────────────────────────────────
    // TC-OLS-001: Przebieg całkowicie w stanie ustalonym
    // Kryterium: 100% punktów = STEADY_STATE, 0 EQUILIBRATION
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-OLS-001: Przebieg stały → 100% STEADY_STATE")
    void tc_ols_001_fullyStableRun_shouldReturnOnlySteadyState() {
        // Given: 120 min stałych pomiarów z niewielkim szumem (±0,1°C)
        List<ThermoMeasurementPoint> points = generateFlatRun(
            5.0, 0.1, 120, Instant.parse("2026-06-21T08:00:00Z"));

        // When
        List<Segment> segments = segmentor.segment(points);

        // Then
        long steadyCount = countByType(segments, SegmentType.STEADY_STATE);
        long equilCount  = countByType(segments, SegmentType.EQUILIBRATION);

        assertThat(steadyCount).isGreaterThanOrEqualTo(1)
            .as("Powinien wykryć co najmniej jeden segment STEADY_STATE");
        assertThat(equilCount).isZero()
            .as("Brak segmentów EQUILIBRATION w stabilnym przebiegu");
    }

    // ──────────────────────────────────────────────
    // TC-OLS-002: Rampa dochodzenia (EQUILIBRATION)
    // Kryterium: detekcja EQUILIBRATION przed STEADY_STATE, brak false positives STEADY w rampie
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-OLS-002: Rampa nastawy −0,33°C/min → EQUILIBRATION + STEADY_STATE")
    void tc_ols_002_linearRamp_thenSteady_shouldDetectEquilibrationFirst() {
        // Given: 60 min rampy (−0,33°C/min) + 60 min stanu ustalonego
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        Instant t = Instant.parse("2026-06-21T08:00:00Z");

        // Rampa: 5.0°C → -15°C w 60 min (dokładnie przypadek testowy CSV z DP-001 §5)
        for (int i = 0; i < 60; i++) {
            points.add(makePoint(5.0 - 0.33 * i, t.plusSeconds(i * 60L)));
        }
        // Stan ustalony: -15°C ±0,1°C przez 60 min
        for (int i = 60; i < 120; i++) {
            double noise = (i % 2 == 0) ? 0.05 : -0.05;
            points.add(makePoint(-15.0 + noise, t.plusSeconds(i * 60L)));
        }

        // When
        List<Segment> segments = segmentor.segment(points);

        // Then
        assertThat(segments).isNotEmpty();

        // Pierwszy segment = EQUILIBRATION
        Segment first = segments.get(0);
        assertThat(first.getType()).isEqualTo(SegmentType.EQUILIBRATION)
            .as("Pierwszy segment musi być EQUILIBRATION");

        // Ostatni segment = STEADY_STATE
        Segment last = segments.get(segments.size() - 1);
        assertThat(last.getType()).isEqualTo(SegmentType.STEADY_STATE)
            .as("Ostatni segment musi być STEADY_STATE");

        // Żaden punkt rampy (0–59 min) nie może być w STEADY_STATE
        List<Segment> steadyInRamp = segments.stream()
            .filter(s -> s.getType() == SegmentType.STEADY_STATE)
            .filter(s -> s.getFromTimestamp().isBefore(t.plusSeconds(55 * 60L)))
            .toList();
        assertThat(steadyInRamp).isEmpty()
            .as("Brak STEADY_STATE podczas rampy dochodzenia");
    }

    // ──────────────────────────────────────────────
    // TC-OLS-003: Tolerancja znaczników czasu (CSV criterion z DP-001 §5)
    // Kryterium: granica segmentu z dokładnością ≤5 min od prawdziwej granicy
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-OLS-003: Granica EQUILIBRATION→STEADY z dokładnością ≤5 min")
    void tc_ols_003_segmentBoundaryTimestampAccuracy() {
        Instant transitionPoint = Instant.parse("2026-06-21T09:00:00Z"); // 60 min od startu

        List<ThermoMeasurementPoint> points = new ArrayList<>();
        Instant t = Instant.parse("2026-06-21T08:00:00Z");
        for (int i = 0; i < 60; i++) {
            points.add(makePoint(5.0 - 0.33 * i, t.plusSeconds(i * 60L)));
        }
        for (int i = 60; i < 120; i++) {
            points.add(makePoint(-15.0, t.plusSeconds(i * 60L)));
        }

        List<Segment> segments = segmentor.segment(points);

        Optional<Segment> firstSteady = segments.stream()
            .filter(s -> s.getType() == SegmentType.STEADY_STATE)
            .findFirst();

        assertThat(firstSteady).isPresent();
        long diffMinutes = Math.abs(Duration.between(
            firstSteady.get().getFromTimestamp(), transitionPoint).toMinutes());

        assertThat(diffMinutes).isLessThanOrEqualTo(5)
            .as("Granica STEADY musi być wykryta z dokładnością ≤5 minut");
    }

    // ──────────────────────────────────────────────
    // TC-OLS-004: MIN_STEADY_MINUTES — odrzucenie zbyt krótkich segmentów
    // Kryterium: segment STEADY_STATE krótszy niż 30 min = EQUILIBRATION
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-OLS-004: Segment STEADY krótszy niż 30 min = ignorowany")
    void tc_ols_004_shortSteadySegment_shouldNotBeClassifiedAsSteady() {
        // Given: 15 min stały (za krótki) + 60 min rampa
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        Instant t = Instant.parse("2026-06-21T08:00:00Z");
        for (int i = 0; i < 15; i++) points.add(makePoint(5.0, t.plusSeconds(i * 60L)));
        for (int i = 15; i < 75; i++) points.add(makePoint(5.0 - 0.1 * i, t.plusSeconds(i * 60L)));

        List<Segment> segments = segmentor.segment(points);

        long steadyCount = countByType(segments, SegmentType.STEADY_STATE);
        assertThat(steadyCount).isZero()
            .as("Segment <30 min nie może być sklasyfikowany jako STEADY_STATE");
    }

    // ──────────────────────────────────────────────
    // TC-OLS-005: Dane z przerywami (gap > 5 min)
    // Kryterium: segment nie przekracza przerwy — brak ekstrapolacji
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-OLS-005: Przerwa w danych — segment kończy się przed przerwą")
    void tc_ols_005_dataGap_shouldNotSpanGap() {
        List<ThermoMeasurementPoint> points = new ArrayList<>();
        Instant t = Instant.parse("2026-06-21T08:00:00Z");
        for (int i = 0; i < 60; i++) points.add(makePoint(5.0, t.plusSeconds(i * 60L)));
        // Przerwa 30 min
        for (int i = 90; i < 150; i++) points.add(makePoint(5.0, t.plusSeconds(i * 60L)));

        List<Segment> segments = segmentor.segment(points);

        Instant gapStart = t.plusSeconds(60 * 60L);
        Instant gapEnd   = t.plusSeconds(90 * 60L);

        segments.forEach(s ->
            assertThat(s.getToTimestamp().isBefore(gapStart) ||
                       s.getFromTimestamp().isAfter(gapEnd))
                .isTrue()
                .as("Żaden segment nie może obejmować przerwy w danych")
        );
    }

    // --- Helper methods ---

    private List<ThermoMeasurementPoint> generateFlatRun(
            double temp, double noise, int minutes, Instant start) { /* ... */ }

    private ThermoMeasurementPoint makePoint(double temp, Instant time) { /* ... */ }

    private long countByType(List<Segment> segments, SegmentType type) {
        return segments.stream().filter(s -> s.getType() == type).count();
    }
}
```

---

## 3. Testy jednostkowe — CusumDetector

```java
// src/test/java/.../service/regime/CusumDetectorTest.java

class CusumDetectorTest {

    private CusumDetector detector;

    @BeforeEach
    void setUp() { detector = new CusumDetector(); }

    // TC-CUSUM-001: Sustained shift w górę
    @Test
    @DisplayName("TC-CUSUM-001: Trwałe przesunięcie +5°C → detekcja UP change point")
    void tc_cusum_001_sustainedUpwardShift_shouldDetectChangePoint() {
        // 60 punktów at 5.0°C, następnie 60 punktów at 10.0°C (shift +5°C)
        double[] values = new double[120];
        Arrays.fill(values, 0, 60, 5.0);
        Arrays.fill(values, 60, 120, 10.0);

        List<ChangePoint> changes = detector.detect(values, 0.3);

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).direction()).isEqualTo(Direction.UP);
        assertThat(changes.get(0).index())
            .isBetween(55, 70)  // ±5 min tolerancji
            .as("Change point musi być w oknie 55–70 (actual: 60)");
    }

    // TC-CUSUM-002: Brak zmiany — brak false positives
    @Test
    @DisplayName("TC-CUSUM-002: Stabilny sygnał → brak change points")
    void tc_cusum_002_stableSignal_noFalsePositives() {
        double[] values = new double[200];
        Random random = new Random(42); // deterministyczne
        for (int i = 0; i < 200; i++) {
            values[i] = 5.0 + (random.nextDouble() - 0.5) * 0.6; // sigma ≈ 0.3°C
        }

        List<ChangePoint> changes = detector.detect(values, 0.3);

        assertThat(changes).isEmpty()
            .as("Brak change points dla sygnału bez zmiany nastawy");
    }

    // TC-CUSUM-003: FastCooling — zjazd −7°C (przypadek referencyjny)
    @Test
    @DisplayName("TC-CUSUM-003: FastCooling +6h → detekcja SETPOINT_CHANGE DOWN")
    void tc_cusum_003_fastCooling_shouldDetectDownwardChange() {
        // Modeluje przebieg chłodziarki: 4h normalna (5°C) + 8h fastcooling (~−3°C avg)
        double[] values = new double[360]; // 6h = 360 min próbkowanie 1/min
        Arrays.fill(values, 0, 240, 5.0);    // 4h normalna praca
        Arrays.fill(values, 240, 360, -2.5); // fastcooling

        List<ChangePoint> changes = detector.detect(values, 1.5);

        assertThat(changes).isNotEmpty();
        assertThat(changes.stream().anyMatch(c -> c.direction() == Direction.DOWN))
            .isTrue()
            .as("Musi wykryć zmianę w dół (fastcooling)");
    }

    // TC-CUSUM-004: Dwa kolejne shifty
    @Test
    @DisplayName("TC-CUSUM-004: Dwa shifty (fastcooling + powrót) → 2 change points")
    void tc_cusum_004_twoShifts_shouldDetectBoth() {
        double[] values = new double[300];
        Arrays.fill(values, 0, 100, 5.0);    // normalna
        Arrays.fill(values, 100, 200, -2.5); // fastcooling
        Arrays.fill(values, 200, 300, 5.0);  // powrót

        List<ChangePoint> changes = detector.detect(values, 1.0);

        assertThat(changes).hasSize(2)
            .as("Muszą być wykryte dwa change points: DOWN i UP");
    }
}
```

---

## 4. Testy jednostkowe — RegimeAwareStatsService

```java
// src/test/java/.../service/regime/RegimeAwareStatsServiceTest.java

@ExtendWith(MockitoExtension.class)
class RegimeAwareStatsServiceTest {

    @InjectMocks
    private RegimeAwareStatsService service;

    @Mock
    private MetrologicalStatsService metrologicalStatsService;

    @Mock
    private CalibrationCorrectionService calibrationCorrectionService;

    // TC-RAWS-001: Filtrowanie do STEADY_STATE — prawidłowe
    @Test
    @DisplayName("TC-RAWS-001: Tylko punkty STEADY_STATE trafiają do obliczeń")
    void tc_raws_001_onlySteadyPointsPassToCalculations() {
        // Given: seria z 120 punktami, segmenty = 60 min EQUILIBRATION + 60 min STEADY
        Instant t = Instant.parse("2026-06-21T08:00:00Z");
        ThermoMeasurementSeries series = buildSeriesWithPoints(t, 120);

        List<Segment> segments = List.of(
            makeSegment(SegmentType.EQUILIBRATION, t, t.plusSeconds(60 * 60L)),
            makeSegment(SegmentType.STEADY_STATE,  t.plusSeconds(60 * 60L), t.plusSeconds(120 * 60L))
        );

        // When
        ConditionalStatsDTO result = service.calculateConditionalStatistics(
            series, segments, null, 1.8, 8.0);

        // Then
        assertThat(result.isHasSteadyStateData()).isTrue();
        assertThat(result.getSteadyStatePointCount())
            .isEqualTo(60)
            .as("Dokładnie 60 punktów STEADY_STATE");
        assertThat(result.getSteadyStateCoveragePercent())
            .isCloseTo(50.0, within(1.0))
            .as("50% pokrycia przez STEADY_STATE");
    }

    // TC-RAWS-002: Za mało punktów STEADY_STATE → hasSteadyStateData = false
    @Test
    @DisplayName("TC-RAWS-002: <30 punktów STEADY_STATE → hasSteadyStateData=false")
    void tc_raws_002_tooFewSteadyPoints_returnsNoCoverage() {
        Instant t = Instant.parse("2026-06-21T08:00:00Z");
        ThermoMeasurementSeries series = buildSeriesWithPoints(t, 100);

        List<Segment> segments = List.of(
            makeSegment(SegmentType.STEADY_STATE, t, t.plusSeconds(20 * 60L)) // tylko 20 min
        );

        ConditionalStatsDTO result = service.calculateConditionalStatistics(
            series, segments, null, 1.8, 8.0);

        assertThat(result.isHasSteadyStateData()).isFalse();
        assertThat(result.getSteadyStatePointCount()).isLessThan(30);
    }

    // TC-RAWS-003: Odrzucony segment (accepted=false) nie wpływa na statystyki
    @Test
    @DisplayName("TC-RAWS-003: Segment accepted=false jest ignorowany")
    void tc_raws_003_rejectedSegment_isIgnored() {
        Instant t = Instant.parse("2026-06-21T08:00:00Z");
        ThermoMeasurementSeries series = buildSeriesWithPoints(t, 120);

        Segment rejected = makeSegment(SegmentType.STEADY_STATE, t, t.plusSeconds(60 * 60L));
        rejected.setAccepted(false); // operator odrzucił

        Segment accepted = makeSegment(SegmentType.STEADY_STATE,
            t.plusSeconds(60 * 60L), t.plusSeconds(120 * 60L));

        ConditionalStatsDTO result = service.calculateConditionalStatistics(
            series, List.of(rejected, accepted), null, 1.8, 8.0);

        assertThat(result.getSteadyStatePointCount())
            .isEqualTo(60)
            .as("Tylko zaakceptowane segmenty STEADY_STATE");
    }

    // TC-RAWS-004: Cpk w STEADY_STATE > Cpk na całym przebiegu (przypadek referencyjny)
    @Test
    @DisplayName("TC-RAWS-004: CpkSteady > CpkFull dla przebiegu z fastcooling")
    void tc_raws_004_conditionalCpkBetterThanFull() {
        // Dane modelujące przebieg chłodziarki z fastcooling:
        // 60 min EQUILIBRATION (temp +1…−6°C) + 60 min STEADY (temp +3…+7°C)
        // Na całym przebiegu Cpk jest niski z powodu ekskursji fastcooling
        // Na STEADY_STATE Cpk powinien być znacznie lepszy

        Instant t = Instant.parse("2026-06-21T08:00:00Z");
        List<ThermoMeasurementPoint> allPoints = new ArrayList<>();

        // Equilibration (przejście od normalnej do fastcooling)
        for (int i = 0; i < 60; i++) {
            allPoints.add(makePoint(5.0 - 0.183 * i, t.plusSeconds(i * 60L)));
        }
        // Steady state (temperatura normalna)
        for (int i = 60; i < 120; i++) {
            double v = 4.5 + (i % 3 == 0 ? 0.2 : -0.2);
            allPoints.add(makePoint(v, t.plusSeconds(i * 60L)));
        }

        ThermoMeasurementSeries series = buildSeriesFromPoints(allPoints);
        List<Segment> segments = List.of(
            makeSegment(SegmentType.EQUILIBRATION, t, t.plusSeconds(60 * 60L)),
            makeSegment(SegmentType.STEADY_STATE, t.plusSeconds(60 * 60L), t.plusSeconds(120 * 60L))
        );

        ConditionalStatsDTO conditional = service.calculateConditionalStatistics(
            series, segments, null, 1.8, 8.0);

        // Cpk na całym przebiegu (liczymy osobno na allPoints)
        double[] allRaw = allPoints.stream().mapToDouble(ThermoMeasurementPoint::getRawCelsius).toArray();
        double cpkFull = SpcEngine.calculateCapability(allRaw, 1.8, 8.0).getCpk();

        assertThat(conditional.getCpkSteady())
            .isGreaterThan(cpkFull)
            .as("Cpk w fazie STEADY_STATE musi być lepszy niż na całym przebiegu z transientem");
    }
}
```

---

## 5. Testy integracyjne — Flyway + Persistence

```java
// src/test/java/.../repository/SegmentRepositoryTest.java

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration")
class SegmentRepositoryTest {

    @Autowired
    private SegmentRepository segmentRepository;

    @Autowired
    private ThermoMeasurementSeriesRepository seriesRepository;

    // TC-REPO-001: Zapisanie segmentu z audytem Envers
    @Test
    @DisplayName("TC-REPO-001: Segment zapisywany i odczytywany poprawnie")
    void tc_repo_001_saveAndFindSegment() {
        ThermoMeasurementSeries series = seriesRepository.findAll().get(0);

        Segment seg = Segment.builder()
            .series(series)
            .fromTimestamp(Instant.parse("2026-06-21T08:00:00Z"))
            .toTimestamp(Instant.parse("2026-06-21T09:00:00Z"))
            .type(SegmentType.STEADY_STATE)
            .confidence(0.92)
            .source(DetectionSource.ALGORITHM)
            .accepted(true)
            .build();

        Segment saved = segmentRepository.save(seg);

        assertThat(saved.getId()).isNotNull();
        Segment found = segmentRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getType()).isEqualTo(SegmentType.STEADY_STATE);
        assertThat(found.getConfidence()).isCloseTo(0.92, within(0.001));
    }

    // TC-REPO-002: Cascading DELETE przy usunięciu serii
    @Test
    @DisplayName("TC-REPO-002: Usunięcie serii kasuje segmenty (CASCADE)")
    void tc_repo_002_deleteSeries_cascadeDeletesSegments() {
        // Given: seria z 2 segmentami
        // When: usunięcie serii
        // Then: segmenty nie istnieją (ON DELETE CASCADE)
        // ...
    }

    // TC-REPO-003: Zapytanie o segmenty STEADY_STATE dla serii
    @Test
    @DisplayName("TC-REPO-003: findBySeries_IdAndTypeAndAccepted zwraca poprawne segmenty")
    void tc_repo_003_findSteadySegmentsForSeries() {
        // ...
    }
}
```

---

## 6. Testy CSV Detektora (per DP-001 §5) — Przypadki Testowe Specyfikacyjne

> [!IMPORTANT]
> Poniższe przypadki testowe są **deliverables specyfikacyjnymi** — są bezpośrednim wymaganiem DP-001 §5 ("Walidacja rozwiązania (self-CSV)"). Muszą być wykonane i udokumentowane przed aktywacją feature flag w środowisku produkcyjnym.

```java
// src/test/java/.../service/regime/RegimeDetectorCsvTest.java
// CSV = Computer System Validation — testy walidacyjne samego detektora

@SpringBootTest
@TestPropertySource(properties = "regime.detection.enabled=true")
class RegimeDetectorCsvTest {

    @Autowired
    private RegimeDetectionService regimeDetectionService;

    // ══════════════════════════════════════════════════════════════════
    // CSV-TC-001: Rampa nastawy bez szpilek
    // Spec: 1× EQUILIBRATION + STEADY_STATE, brak ekskursji
    // Kryterium akceptacji: czułość STEADY ≥95%, brak false positive DEFROST/DOOR
    // ══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("CSV-TC-001 [WYMAGANY]: Rampa −0,33°C/min, brak szpilek → EQUIL + STEADY")
    void csv_tc_001_rampWithoutSpikes() {
        // Dane syntetyczne: 2h rampa (-0,33°C/min od +5 do -35°C) + 3h steady (-18°C ±0,2°C)
        ThermoMeasurementSeries series = buildSeries_RampThenSteady(
            5.0, -18.0, 120, 180, Instant.parse("2026-06-21T08:00:00Z"));

        Map<GridPosition, ThermoMeasurementSeries> allChannels =
            Map.of(GridPosition.G_PL, series);

        DetectionResult result = regimeDetectionService.detect(series, allChannels);

        // Warunki akceptacji
        assertThat(result.getSegments())
            .extracting(Segment::getType)
            .contains(SegmentType.EQUILIBRATION, SegmentType.STEADY_STATE)
            .as("CSV-TC-001: Musi zawierać EQUILIBRATION i STEADY_STATE");

        assertThat(result.getSegments())
            .noneMatch(s -> s.getType() == SegmentType.DEFROST ||
                           s.getType() == SegmentType.DOOR_EVENT)
            .as("CSV-TC-001: Brak false positive DEFROST/DOOR_EVENT");

        double steadyCoverage = computeSteadyCoverage(result, 120, 300);
        assertThat(steadyCoverage).isGreaterThanOrEqualTo(0.90)
            .as("CSV-TC-001: ≥90% punktów fazy STEADY_STATE poprawnie sklasyfikowanych");
    }

    // ══════════════════════════════════════════════════════════════════
    // CSV-TC-002: Defrost co 8h, 3 cykle
    // Spec: 3× DEFROST oznaczone jako periodyczne
    // Kryterium: wykrycie 3 DEFROST ±1, każdy w oknie ±15 min od syntetycznej szpilki
    // ══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("CSV-TC-002 [WYMAGANY]: Defrost co 8h, 3 cykle → 3× DEFROST")
    void csv_tc_002_periodicDefrost3cycles() {
        // Syntetyczny przebieg: 24h steady (-18°C) + 3 szpilki defrostu co 8h
        // Każda szpilka: gradient +1°C/min przez 10 min, potem powrót w 20 min
        Instant start = Instant.parse("2026-06-21T08:00:00Z");
        ThermoMeasurementSeries series = buildSeries_SteadyWithPeriodicDefrosts(
            -18.0, 3, 480, 10, 20, start);

        DetectionResult result = regimeDetectionService.detect(series, Map.of(GridPosition.G_PL, series));

        long defrostCount = countByType(result.getSegments(), SegmentType.DEFROST);
        assertThat(defrostCount)
            .isBetween(2L, 4L) // ±1 tolerancja
            .as("CSV-TC-002: Muszą być wykryte 2–4 cykle DEFROST (oczekiwane 3)");

        // Każdy wykryty DEFROST musi być w oknie ±15 min od syntetycznej szpilki
        List<Instant> expectedDefrosts = List.of(
            start.plusSeconds(8L * 3600),
            start.plusSeconds(16L * 3600),
            start.plusSeconds(24L * 3600)
        );
        result.getSegments().stream()
            .filter(s -> s.getType() == SegmentType.DEFROST)
            .forEach(detected -> {
                boolean withinWindow = expectedDefrosts.stream().anyMatch(expected ->
                    Math.abs(Duration.between(detected.getFromTimestamp(), expected).toMinutes()) <= 15);
                assertThat(withinWindow)
                    .isTrue()
                    .as("CSV-TC-002: DEFROST wykryty w %s poza oknem ±15 min",
                        detected.getFromTimestamp());
            });
    }

    // ══════════════════════════════════════════════════════════════════
    // CSV-TC-003: Pojedyncze otwarcie drzwi
    // Spec: 1× DOOR_EVENT (nieokresowe, czujniki przednie pierwsze)
    // Kryterium: wykrycie 1 DOOR_EVENT ±15 min od zdarzenia
    // ══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("CSV-TC-003 [WYMAGANY]: Otwarcie drzwi → 1× DOOR_EVENT")
    void csv_tc_003_singleDoorEvent() {
        Instant start = Instant.parse("2026-06-21T08:00:00Z");
        Instant doorOpen = start.plusSeconds(3L * 3600); // po 3h

        // Czujniki przednie reagują pierwsze (G-PL, G-PP, D-PL, D-PP)
        Map<GridPosition, ThermoMeasurementSeries> channels =
            buildMultiChannelDoorEvent(start, doorOpen, 5.0, 8.0, 12.0);

        ThermoMeasurementSeries refSeries = channels.get(GridPosition.G_PL);
        DetectionResult result = regimeDetectionService.detect(refSeries, channels);

        long doorCount = countByType(result.getSegments(), SegmentType.DOOR_EVENT);
        assertThat(doorCount)
            .isEqualTo(1)
            .as("CSV-TC-003: Musi być wykryty dokładnie 1 DOOR_EVENT");

        Optional<Segment> doorSeg = result.getSegments().stream()
            .filter(s -> s.getType() == SegmentType.DOOR_EVENT)
            .findFirst();

        assertThat(doorSeg).isPresent();
        long diffMin = Math.abs(Duration.between(doorSeg.get().getFromTimestamp(), doorOpen).toMinutes());
        assertThat(diffMin).isLessThanOrEqualTo(15)
            .as("CSV-TC-003: DOOR_EVENT z dokładnością ≤15 min");
    }

    // ══════════════════════════════════════════════════════════════════
    // CSV-TC-004: Przebieg całkowicie w stanie ustalonym
    // Spec: 100% STEADY_STATE, Cpk liczony na całości
    // Kryterium: brak EQUILIBRATION/DEFROST/DOOR, pokrycie STEADY ≥95%
    // ══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("CSV-TC-004 [WYMAGANY]: Przebieg ustalony → 100% STEADY_STATE")
    void csv_tc_004_fullyEstablishedRun() {
        Instant start = Instant.parse("2026-06-21T08:00:00Z");
        ThermoMeasurementSeries series = buildSeries_SteadyOnly(-18.0, 0.3, 1440, start); // 24h

        DetectionResult result = regimeDetectionService.detect(series,
            Map.of(GridPosition.G_PL, series));

        assertThat(result.getSegments())
            .noneMatch(s -> s.getType() == SegmentType.EQUILIBRATION ||
                           s.getType() == SegmentType.DEFROST ||
                           s.getType() == SegmentType.DOOR_EVENT)
            .as("CSV-TC-004: Brak fałszywych ekskursji dla przebiegu ustalonego");

        double steadyCoverage = computeSteadyCoverage(result, 0, 1440);
        assertThat(steadyCoverage).isGreaterThanOrEqualTo(0.95)
            .as("CSV-TC-004: ≥95% punktów = STEADY_STATE");
    }

    // ══════════════════════════════════════════════════════════════════
    // CSV-TC-005: Dane referencyjne 2026-06-21 (przypadek lodówko-zamrażarki)
    // Spec: wykrycie SETPOINT_CHANGE (fastcooling) w oknie 6–14h sesji
    // Kryterium: co najmniej 1 SETPOINT_CHANGE lub EXCURSION wykryty w godzinach 6–14
    // ══════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("CSV-TC-005 [WYMAGANY]: Przebieg referencyjny 2026-06-21 → SETPOINT_CHANGE w 6–14h")
    void csv_tc_005_referenceRun_fastcooling_detected() {
        // Modeluje przebieg G-TL chłodziarki (najlepszy przykład fastcooling):
        // 0–6h: +1,5°C (normalna praca, słaba stabilność z powodu MIXED mode)
        // 6–14h: zjazd do −6,5°C (fastcooling)
        // 14–25h: powrót do +1,5°C
        Instant start = Instant.parse("2026-06-21T00:00:00Z");
        List<ThermoMeasurementPoint> points = new ArrayList<>();

        for (int i = 0; i < 360; i++) { // 6h normalna
            points.add(makePoint(1.5 + (i % 5 == 0 ? 0.3 : -0.3), start.plusSeconds(i * 60L)));
        }
        for (int i = 360; i < 840; i++) { // 8h fastcooling (zjazd od 1,5 do −6,5°C)
            double frac = (double)(i - 360) / 480;
            points.add(makePoint(1.5 - 8.0 * frac, start.plusSeconds(i * 60L)));
        }
        for (int i = 840; i < 1500; i++) { // 11h powrót
            points.add(makePoint(1.5, start.plusSeconds(i * 60L)));
        }

        ThermoMeasurementSeries series = buildSeriesFromPoints(points);
        DetectionResult result = regimeDetectionService.detect(series, Map.of(GridPosition.G_TL, series));

        Instant fastCoolingStart = start.plusSeconds(6L * 3600);
        Instant fastCoolingEnd   = start.plusSeconds(14L * 3600);

        boolean fastCoolingDetected = result.getSegments().stream()
            .filter(s -> s.getType() == SegmentType.SETPOINT_CHANGE ||
                        s.getType() == SegmentType.EXCURSION)
            .anyMatch(s -> !s.getFromTimestamp().isAfter(fastCoolingEnd) &&
                          !s.getToTimestamp().isBefore(fastCoolingStart));

        assertThat(fastCoolingDetected)
            .isTrue()
            .as("CSV-TC-005: Fastcooling (6–14h) musi być wykryty jako SETPOINT_CHANGE lub EXCURSION");
    }
}
```

---

## 7. Testy wydajnościowe

```java
// src/test/java/.../service/regime/RegimeDetectionPerformanceTest.java

class RegimeDetectionPerformanceTest {

    // NFR-02: czas segmentacji <2s dla 12000 punktów (25h × 8 kanałów × 1/min)
    @Test
    @DisplayName("NFR-02: Segmentacja 12000 punktów w <2000ms")
    void nfr_02_segmentationPerformance_under2Seconds() {
        // 8 kanałów × 1500 punktów (25h × 1/min)
        List<ThermoMeasurementPoint> points = generateLargeRun(1500);

        long startMs = System.currentTimeMillis();
        for (int ch = 0; ch < 8; ch++) {
            new OlsSegmentor().segment(points);
        }
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertThat(elapsedMs).isLessThan(2000L)
            .as("Segmentacja 8 kanałów × 1500 punktów musi wykonać się w <2s, była: " + elapsedMs + "ms");
    }
}
```

---

## 8. Testy E2E — PDF z segmentacją

```java
// src/test/java/.../service/pdf/RegimeAwarePdfE2ETest.java

@SpringBootTest
@TestPropertySource(properties = "regime.detection.enabled=true")
class RegimeAwarePdfE2ETest {

    @Autowired
    private TestoRevalidationPdfService pdfService;

    // TC-E2E-001: Raport z trybem CHARACTERIZATION nie zawiera FAIL dla przebiegu z fastcooling
    @Test
    @DisplayName("TC-E2E-001: CHARACTERIZATION run z fastcooling → brak FAIL w raporcie")
    void tc_e2e_001_characterizationRun_noFailVerdict() throws Exception {
        RevalidationSession session = buildSessionWithFastCooling(RunMode.CHARACTERIZATION);
        File outputFile = File.createTempFile("test_regime_pdf_", ".pdf");

        pdfService.generateRevalidationReport(session, outputFile, null);

        // Parsuj PDF i sprawdź brak słowa "FAIL" w werdykcie głównym
        String pdfText = extractPdfText(outputFile);
        assertThat(pdfText)
            .doesNotContain("WYNIK: FAIL")
            .contains("FINDING")
            .as("TC-E2E-001: Run CHARACTERIZATION z fastcooling nie może dać FAIL");

        outputFile.deleteOnExit();
    }

    // TC-E2E-002: Raport zawiera dwa zestawy statystyk (cały przebieg + STEADY_STATE only)
    @Test
    @DisplayName("TC-E2E-002: Raport zawiera sekcję z Cpk(cały przebieg) i Cpk(STEADY_STATE)")
    void tc_e2e_002_reportContainsBothStatSets() throws Exception {
        RevalidationSession session = buildSessionWithMixedRegimes(RunMode.QUALIFICATION);
        File outputFile = File.createTempFile("test_dual_stats_pdf_", ".pdf");

        pdfService.generateRevalidationReport(session, outputFile, null);

        String pdfText = extractPdfText(outputFile);
        assertThat(pdfText)
            .contains("STEADY_STATE")
            .contains("Cały przebieg")
            .as("TC-E2E-002: Raport musi zawierać oba zestawy statystyk");

        outputFile.deleteOnExit();
    }

    // TC-E2E-003: Brak STEADY_STATE w przebiegu → sekcja "INCONCLUSIVE" w raporcie
    @Test
    @DisplayName("TC-E2E-003: Brak fazy STEADY_STATE → INCONCLUSIVE w werdykcie")
    void tc_e2e_003_noSteadyState_inconclusiveVerdict() throws Exception {
        RevalidationSession session = buildSessionAllEquilibration(RunMode.QUALIFICATION);
        File outputFile = File.createTempFile("test_inconclusive_pdf_", ".pdf");

        pdfService.generateRevalidationReport(session, outputFile, null);

        String pdfText = extractPdfText(outputFile);
        assertThat(pdfText)
            .contains("INCONCLUSIVE")
            .as("TC-E2E-003: Brak STEADY_STATE musi dać INCONCLUSIVE");

        outputFile.deleteOnExit();
    }
}
```

---

## 9. Macierz pokrycia wymagań

| Wymaganie (BA) | Przypadki testowe | Status |
|---|---|---|
| BR-01 (segmentacja) | TC-OLS-001, TC-OLS-002, CSV-TC-001, CSV-TC-004 | Do implementacji |
| BR-02 (metryki na STEADY_STATE) | TC-RAWS-001, TC-RAWS-003, TC-RAWS-004 | Do implementacji |
| BR-03 (oba zestawy w raporcie) | TC-E2E-002 | Do implementacji |
| BR-04 (RunMode) | TC-E2E-001, TC-E2E-003 | Do implementacji |
| BR-05 (alert brak STEADY) | TC-E2E-003 | Do implementacji |
| BR-06 (detekcja DEFROST/DOOR) | CSV-TC-002, CSV-TC-003 | Do implementacji |
| NFR-01 (determinizm) | Wszystkie unit testy (brak Random) | Do weryfikacji |
| NFR-02 (czas <2s) | NFR-02 perf test | Do implementacji |
| NFR-04 (feature flag) | Integracyjne z `enabled=false` | Do implementacji |
| NFR-05 (self-CSV) | CSV-TC-001..005 | **WYMAGANE PRZED GO-LIVE** |

---

## 10. Kryteria wejścia / wyjścia

### Kryteria wejścia do testów (Entry Criteria)
- [ ] Flyway V28 i V29 wykonane bez błędów
- [ ] `RegimeDetectionService` skompilowany
- [ ] Feature flag `regime.detection.enabled=true` dla środowiska testowego

### Kryteria wyjścia (Exit Criteria — warunek aktywacji produkcyjnej)
- [ ] Wszystkie 5 testów CSV (CSV-TC-001..005) = PASS z udokumentowanymi wynikami
- [ ] Żaden test regresyjny istniejącej funkcjonalności = FAIL
- [ ] Test NFR-02 (wydajność) = PASS
- [ ] Inspekcja werdyktu na danych referencyjnych 2026-06-21: Cpk(STEADY) > Cpk(cały przebieg)
- [ ] Zatwierdzenie dokumentacji testowej przez kierownika walidacji
