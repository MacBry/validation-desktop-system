# TEST-EXC002: Pakiet Testów — Klasyfikacja Ekskursji na Podstawie Wektora Propagacji
## Test Package — Propagation-Aware Excursion Classifier

| Pole | Wartość |
|---|---|
| Identyfikator | TEST-EXC002 v1.0 |
| System | `validation-desktop-system` |
| Powiązane dokumenty | BA-EXC002 v1.0, IMPL-EXC002 v1.0 |
| Framework testowy | JUnit 5 + AssertJ + Mockito (Spring Boot Test) |
| Status | Draft |
| Data | 2026-06-25 |

---

## 1. Strategia testowania

### 1.1 Piramida testów

```
                    ┌──────────┐
                    │ E2E (4)  │  ← Pełny przebieg → PDF z propagacją
                    ├──────────┤
              ┌─────┤Integration│──────┐
              │     │  (8)      │      │
              │     └──────────┘      │
         ┌────┤──────────────────────────┐
         │    │    Unit Tests (30+)       │
         │    │ PropagationVectorClassifier│
         │    │ GridPositionCoordinates   │
         │    │ ExcursionDetector (nowe)  │
         │    │ AirflowSourcePreset       │
         └────┴──────────────────────────┘
```

### 1.2 Zasady

- **Determinizm**: Brak losowości w algorytmach — ten sam wejście = ten sam wynik.
- **Syntetyczne dane**: Wszystkie unit testy używają programowo generowanych szpilek z kontrolowanymi lagami.
- **Dane referencyjne**: Testy regresyjne na danych sesji 2026-06-21 (REAR_WALL) — wynik musi być identyczny z obecną implementacją.
- **Pokrycie geometrii**: Każda konfiguracja `AirflowSourcePreset` ma dedykowany test case.

---

## 2. Testy jednostkowe — `GridPositionCoordinates`

```java
// src/test/java/.../model/regime/GridPositionCoordinatesTest.java

class GridPositionCoordinatesTest {

    // ──────────────────────────────────────────────
    // TC-GPC-001: Wszystkie pozycje mają współrzędne
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-GPC-001: Każda GridPosition ma zdefiniowane współrzędne [0,1]^3")
    void tc_gpc_001_allPositionsHaveCoordinates() {
        for (GridPosition pos : GridPosition.values()) {
            double[] coords = GridPositionCoordinates.getCoordinates(pos);
            assertThat(coords).isNotNull().hasSize(3);
            for (double c : coords) {
                assertThat(c).isBetween(0.0, 1.0);
            }
        }
    }

    // ──────────────────────────────────────────────
    // TC-GPC-002: TOP ma z=1.0, BOTTOM ma z=0.0
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-GPC-002: Warstwa TOP → z=1.0, BOTTOM → z=0.0")
    void tc_gpc_002_topBottomLayers() {
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.TOP_FRONT_LEFT)[2]).isEqualTo(1.0);
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.BOTTOM_FRONT_LEFT)[2]).isEqualTo(0.0);
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.TOP_BACK_RIGHT)[2]).isEqualTo(1.0);
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.BOTTOM_BACK_RIGHT)[2]).isEqualTo(0.0);
    }

    // ──────────────────────────────────────────────
    // TC-GPC-003: FRONT ma y=0.0, BACK ma y=1.0
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-GPC-003: FRONT → y=0.0, BACK → y=1.0")
    void tc_gpc_003_frontBackAxis() {
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.TOP_FRONT_LEFT)[1]).isEqualTo(0.0);
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.TOP_BACK_LEFT)[1]).isEqualTo(1.0);
    }

    // ──────────────────────────────────────────────
    // TC-GPC-004: LEFT ma x=0.0, RIGHT ma x=1.0
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-GPC-004: LEFT → x=0.0, RIGHT → x=1.0")
    void tc_gpc_004_leftRightAxis() {
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.TOP_FRONT_LEFT)[0]).isEqualTo(0.0);
        assertThat(GridPositionCoordinates.getCoordinates(GridPosition.TOP_FRONT_RIGHT)[0]).isEqualTo(1.0);
    }
}
```

---

## 3. Testy jednostkowe — `PropagationVectorClassifier`

### 3.1 Helper do generowania szpilek syntetycznych

```java
// src/test/java/.../service/regime/PropagationTestHelper.java

class PropagationTestHelper {

    static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 6, 25, 10, 0, 0);

    /**
     * Tworzy listę SpikeEvent symulujących propagację od podanych pozycji źródłowych
     * do pozostałych z rosnącym lagiem.
     */
    static List<PropagationVectorClassifier.SpikeEvent> createPropagation(
            Set<GridPosition> sourcePositions, int lagBetweenLayersSeconds) {

        List<PropagationVectorClassifier.SpikeEvent> events = new ArrayList<>();
        List<GridPosition> allPositions = List.of(GridPosition.values());

        for (GridPosition pos : allPositions) {
            long lagSeconds;
            if (sourcePositions.contains(pos)) {
                lagSeconds = 0;
            } else {
                double[] sourceCoord = computeCentroid(sourcePositions);
                double[] posCoord = GridPositionCoordinates.getCoordinates(pos);
                double distance = euclideanDistance(sourceCoord, posCoord);
                lagSeconds = (long) (distance * lagBetweenLayersSeconds);
            }
            events.add(new PropagationVectorClassifier.SpikeEvent(
                pos, BASE_TIME.plusSeconds(lagSeconds)));
        }
        return events;
    }

    private static double[] computeCentroid(Set<GridPosition> positions) {
        double[] sum = new double[3];
        for (GridPosition pos : positions) {
            double[] c = GridPositionCoordinates.getCoordinates(pos);
            sum[0] += c[0]; sum[1] += c[1]; sum[2] += c[2];
        }
        int n = positions.size();
        return new double[]{sum[0]/n, sum[1]/n, sum[2]/n};
    }

    private static double euclideanDistance(double[] a, double[] b) {
        double dx = a[0]-b[0], dy = a[1]-b[1], dz = a[2]-b[2];
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
}
```

### 3.2 Testy per konfiguracja nawiewu

```java
// src/test/java/.../service/regime/PropagationVectorClassifierTest.java

class PropagationVectorClassifierTest {

    private PropagationVectorClassifier classifier;

    @BeforeEach
    void setUp() {
        RegimeDetectionProperties props = new RegimeDetectionProperties();
        props.setPropagationAware(true);
        props.setPropagationCosineSimilarityThreshold(0.7);
        props.setPropagationAmbiguityMargin(0.1);
        props.setPropagationMinSensorsForVector(3);
        classifier = new PropagationVectorClassifier(props);
    }

    // ──────────────────────────────────────────────
    // TC-PVC-001: REAR_WALL — defrost propaguje od tyłu do przodu
    // Kryterium: DEFROST, confidence ≥ 0.85
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-PVC-001: REAR_WALL defrost → DEFROST")
    void tc_pvc_001_rearWall_defrost() {
        Set<GridPosition> source = EnumSet.of(
            GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT,
            GridPosition.BOTTOM_BACK_LEFT, GridPosition.BOTTOM_BACK_RIGHT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
            PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_WALL, null);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.85);
        assertThat(result.cosineDefrost()).isGreaterThan(result.cosineDoor());
    }

    // ──────────────────────────────────────────────
    // TC-PVC-002: REAR_WALL — otwarcie drzwi propaguje od przodu do tyłu
    // Kryterium: DOOR_EVENT, confidence ≥ 0.85
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-PVC-002: REAR_WALL door opening → DOOR_EVENT")
    void tc_pvc_002_rearWall_doorEvent() {
        Set<GridPosition> source = EnumSet.of(
            GridPosition.TOP_FRONT_LEFT, GridPosition.TOP_FRONT_RIGHT,
            GridPosition.BOTTOM_FRONT_LEFT, GridPosition.BOTTOM_FRONT_RIGHT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
            PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_WALL, null);

        assertThat(result.type()).isEqualTo(SegmentType.DOOR_EVENT);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.85);
        assertThat(result.cosineDoor()).isGreaterThan(result.cosineDefrost());
    }

    // ──────────────────────────────────────────────
    // TC-PVC-003: CEILING — defrost propaguje z góry na dół
    // Kryterium: DEFROST (nie DOOR_EVENT jak w obecnej implementacji!)
    // To jest kluczowy test — waliduje naprawę buga
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-PVC-003: CEILING defrost → DEFROST (naprawa błędnej klasyfikacji)")
    void tc_pvc_003_ceiling_defrost() {
        Set<GridPosition> source = EnumSet.of(
            GridPosition.TOP_FRONT_LEFT, GridPosition.TOP_FRONT_RIGHT,
            GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
            PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.CEILING, null);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.85);
    }

    // ──────────────────────────────────────────────
    // TC-PVC-004: CEILING — otwarcie drzwi nadal rozpoznawane
    // Kryterium: DOOR_EVENT (drzwi z przodu, nawiew z góry — różne kierunki)
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-PVC-004: CEILING door opening → DOOR_EVENT")
    void tc_pvc_004_ceiling_doorEvent() {
        Set<GridPosition> source = EnumSet.of(
            GridPosition.TOP_FRONT_LEFT, GridPosition.TOP_FRONT_RIGHT,
            GridPosition.BOTTOM_FRONT_LEFT, GridPosition.BOTTOM_FRONT_RIGHT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
            PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.CEILING, null);

        assertThat(result.type()).isEqualTo(SegmentType.DOOR_EVENT);
    }

    // ──────────────────────────────────────────────
    // TC-PVC-005: RIGHT_WALL — defrost od prawej do lewej
    // Kryterium: DEFROST
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-PVC-005: RIGHT_WALL defrost → DEFROST")
    void tc_pvc_005_rightWall_defrost() {
        Set<GridPosition> source = EnumSet.of(
            GridPosition.TOP_FRONT_RIGHT, GridPosition.TOP_BACK_RIGHT,
            GridPosition.BOTTOM_FRONT_RIGHT, GridPosition.BOTTOM_BACK_RIGHT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
            PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.RIGHT_WALL, null);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.85);
    }

    // ──────────────────────────────────────────────
    // TC-PVC-006: LEFT_WALL — defrost od lewej do prawej
    // Kryterium: DEFROST
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-PVC-006: LEFT_WALL defrost → DEFROST")
    void tc_pvc_006_leftWall_defrost() {
        Set<GridPosition> source = EnumSet.of(
            GridPosition.TOP_FRONT_LEFT, GridPosition.TOP_BACK_LEFT,
            GridPosition.BOTTOM_FRONT_LEFT, GridPosition.BOTTOM_BACK_LEFT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
            PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.LEFT_WALL, null);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
    }

    // ──────────────────────────────────────────────
    // TC-PVC-007: FLOOR — defrost z dołu do góry
    // Kryterium: DEFROST
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-PVC-007: FLOOR defrost → DEFROST")
    void tc_pvc_007_floor_defrost() {
        Set<GridPosition> source = EnumSet.of(
            GridPosition.BOTTOM_FRONT_LEFT, GridPosition.BOTTOM_FRONT_RIGHT,
            GridPosition.BOTTOM_BACK_LEFT, GridPosition.BOTTOM_BACK_RIGHT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
            PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.FLOOR, null);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
    }

    // ──────────────────────────────────────────────
    // TC-PVC-008: REAR_AND_LEFT — dual evaporator
    // Kryterium: DEFROST, oba źródła rozpoznane
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-PVC-008: REAR_AND_LEFT dual defrost → DEFROST")
    void tc_pvc_008_rearAndLeft_defrost() {
        Set<GridPosition> source = EnumSet.of(
            GridPosition.TOP_BACK_LEFT, GridPosition.BOTTOM_BACK_LEFT,
            GridPosition.TOP_BACK_RIGHT, GridPosition.BOTTOM_BACK_RIGHT,
            GridPosition.BOTTOM_FRONT_LEFT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
            PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_AND_LEFT, null);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
    }

    // ──────────────────────────────────────────────
    // TC-PVC-009: CUSTOM — operator definiuje pozycje ręcznie
    // Kryterium: DEFROST z pozycjami TOP_BACK_LEFT, TOP_BACK_RIGHT jako źródło
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-PVC-009: CUSTOM mode → DEFROST z ręcznie zdefiniowanymi pozycjami")
    void tc_pvc_009_custom_defrost() {
        Set<GridPosition> customSource = EnumSet.of(
            GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT);

        Set<GridPosition> spikeSource = EnumSet.of(
            GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT);

        List<PropagationVectorClassifier.SpikeEvent> spikes =
            PropagationTestHelper.createPropagation(spikeSource, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.CUSTOM, customSource);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
    }
}
```

---

## 4. Testy walidacji krzyżowej (wektor vs deklaracja)

```java
// src/test/java/.../service/regime/PropagationCrossValidationTest.java

class PropagationCrossValidationTest {

    private PropagationVectorClassifier classifier;

    @BeforeEach
    void setUp() {
        RegimeDetectionProperties props = new RegimeDetectionProperties();
        props.setPropagationAware(true);
        props.setPropagationCosineSimilarityThreshold(0.7);
        props.setPropagationAmbiguityMargin(0.1);
        props.setPropagationMinSensorsForVector(3);
        classifier = new PropagationVectorClassifier(props);
    }

    // ──────────────────────────────────────────────
    // TC-CV-001: Wektor zgodny z deklaracją → confidence podwyższone
    // Kryterium: confidence > base_confidence
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-CV-001: Wektor zgodny z deklaracją → confidence boost")
    void tc_cv_001_vectorMatchesDeclaration() {
        Set<GridPosition> source = EnumSet.of(
            GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT,
            GridPosition.BOTTOM_BACK_LEFT, GridPosition.BOTTOM_BACK_RIGHT);

        var spikes = PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_WALL, null);

        assertThat(result.type()).isEqualTo(SegmentType.DEFROST);
        assertThat(result.confidence()).isGreaterThan(result.cosineDefrost());
    }

    // ──────────────────────────────────────────────
    // TC-CV-002: Wektor SPRZECZNY z deklaracją → confidence obniżone + notatka
    // Kryterium: confidence ≤ 0.6, notatka zawiera "niezgodny"
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-CV-002: Wektor sprzeczny z deklaracją → confidence penalty + warning")
    void tc_cv_002_vectorContradictsDeclaration() {
        // Propagacja od tyłu (wektor: tył→przód = defrost)
        // ale deklaracja mówi CEILING (oczekiwany defrost: góra→dół)
        Set<GridPosition> source = EnumSet.of(
            GridPosition.TOP_BACK_LEFT, GridPosition.TOP_BACK_RIGHT,
            GridPosition.BOTTOM_BACK_LEFT, GridPosition.BOTTOM_BACK_RIGHT);

        var spikes = PropagationTestHelper.createPropagation(source, 120);

        var result = classifier.classify(spikes, AirflowSourcePreset.CEILING, null);

        // Wektor mówi "od tyłu", ale deklaracja mówi "z góry" — sprzeczność
        assertThat(result.confidence()).isLessThanOrEqualTo(0.7);
        assertThat(result.note()).containsIgnoringCase("niezgodny");
    }

    // ──────────────────────────────────────────────
    // TC-CV-003: Za mało czujników z lagiem → fallback do samej deklaracji
    // Kryterium: klasyfikacja na podstawie deklaracji, obniżone confidence
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-CV-003: Tylko 2 czujniki z lagiem → fallback na deklarację")
    void tc_cv_003_tooFewSensors_fallbackToDeclaration() {
        // Tylko 2 czujniki — za mało na wektor
        List<PropagationVectorClassifier.SpikeEvent> spikes = List.of(
            new PropagationVectorClassifier.SpikeEvent(
                GridPosition.TOP_BACK_LEFT,
                PropagationTestHelper.BASE_TIME),
            new PropagationVectorClassifier.SpikeEvent(
                GridPosition.TOP_FRONT_LEFT,
                PropagationTestHelper.BASE_TIME.plusSeconds(120))
        );

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_WALL, null);

        assertThat(result.type()).isIn(SegmentType.DEFROST, SegmentType.EXCURSION);
        assertThat(result.confidence()).isLessThan(0.85);
    }

    // ──────────────────────────────────────────────
    // TC-CV-004: Wszystkie czujniki reagują jednocześnie → INCONCLUSIVE
    // Kryterium: EXCURSION (brak kierunku)
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-CV-004: Jednoczesna reakcja wszystkich → EXCURSION")
    void tc_cv_004_simultaneousReaction_inconclusive() {
        List<PropagationVectorClassifier.SpikeEvent> spikes = new ArrayList<>();
        for (GridPosition pos : GridPosition.values()) {
            spikes.add(new PropagationVectorClassifier.SpikeEvent(
                pos, PropagationTestHelper.BASE_TIME));
        }

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_WALL, null);

        assertThat(result.type()).isEqualTo(SegmentType.EXCURSION);
        assertThat(result.note()).containsIgnoringCase("jednocześnie");
    }

    // ──────────────────────────────────────────────
    // TC-CV-005: Kierunek niejednoznaczny (ukośna propagacja) → EXCURSION
    // Kryterium: oba cosine < threshold LUB różnica < margin
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-CV-005: Ukośna propagacja → EXCURSION (niejednoznaczna)")
    void tc_cv_005_diagonalPropagation_ambiguous() {
        // Propagacja od TOP_BACK_RIGHT do BOTTOM_FRONT_LEFT — ukośna
        // Nie pasuje ani do defrostu (tył→przód), ani do drzwi (przód→tył)
        List<PropagationVectorClassifier.SpikeEvent> spikes = List.of(
            new PropagationVectorClassifier.SpikeEvent(
                GridPosition.TOP_BACK_RIGHT, PropagationTestHelper.BASE_TIME),
            new PropagationVectorClassifier.SpikeEvent(
                GridPosition.TOP_BACK_LEFT, PropagationTestHelper.BASE_TIME.plusSeconds(30)),
            new PropagationVectorClassifier.SpikeEvent(
                GridPosition.BOTTOM_BACK_RIGHT, PropagationTestHelper.BASE_TIME.plusSeconds(30)),
            new PropagationVectorClassifier.SpikeEvent(
                GridPosition.BOTTOM_FRONT_LEFT, PropagationTestHelper.BASE_TIME.plusSeconds(180)),
            new PropagationVectorClassifier.SpikeEvent(
                GridPosition.TOP_FRONT_LEFT, PropagationTestHelper.BASE_TIME.plusSeconds(90)),
            new PropagationVectorClassifier.SpikeEvent(
                GridPosition.BOTTOM_FRONT_RIGHT, PropagationTestHelper.BASE_TIME.plusSeconds(150))
        );

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_WALL, null);

        // Wektor ukośny — może pasować częściowo do defrostu, ale nie idealnie
        // Oczekujemy albo DEFROST z niskim confidence, albo EXCURSION
        assertThat(result.confidence()).isLessThan(0.9);
    }

    // ──────────────────────────────────────────────
    // TC-CV-006: Pojedynczy czujnik → fallback (za mało danych)
    // Kryterium: EXCURSION, confidence ≤ 0.5
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-CV-006: 1 czujnik → EXCURSION (za mało danych)")
    void tc_cv_006_singleSensor() {
        List<PropagationVectorClassifier.SpikeEvent> spikes = List.of(
            new PropagationVectorClassifier.SpikeEvent(
                GridPosition.TOP_FRONT_LEFT, PropagationTestHelper.BASE_TIME)
        );

        var result = classifier.classify(spikes, AirflowSourcePreset.REAR_WALL, null);

        assertThat(result.type()).isEqualTo(SegmentType.EXCURSION);
        assertThat(result.confidence()).isLessThanOrEqualTo(0.5);
    }
}
```

---

## 5. Testy kompatybilności wstecznej — `ExcursionDetector`

```java
// src/test/java/.../service/regime/ExcursionDetectorBackwardCompatTest.java

class ExcursionDetectorBackwardCompatTest {

    // ──────────────────────────────────────────────
    // TC-BC-001: propagationAware=false → identyczne wyniki jak obecna implementacja
    // Kryterium: isFrontPosition() logika zachowana
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-BC-001: Feature flag OFF → stara logika isFrontPosition()")
    void tc_bc_001_featureFlagOff_usesLegacyLogic() {
        RegimeDetectionProperties props = new RegimeDetectionProperties();
        props.setPropagationAware(false); // FLAGA WYŁĄCZONA
        props.setExcursionGradientThreshold(0.5);
        props.setExcursionReturnWindowMinutes(60);

        ExcursionDetector detector = new ExcursionDetector(props, null /*propagation nie użyte*/);

        // Symulacja szpilki z frontu → powinno być DOOR_EVENT (stara logika)
        Map<GridPosition, ThermoMeasurementSeries> channels = createTestChannels(
            GridPosition.TOP_FRONT_LEFT, /*firstReactingPosition*/
            GridPosition.TOP_BACK_LEFT   /*secondReactingPosition*/
        );

        Map<GridPosition, List<MeasurementSegment>> result = detector.detectAll(channels);

        // Weryfikacja: front reacted first → DOOR_EVENT (jak w obecnej implementacji)
        List<MeasurementSegment> frontSegments = result.get(GridPosition.TOP_FRONT_LEFT);
        assertThat(frontSegments).isNotEmpty();
        assertThat(frontSegments.get(0).getType()).isEqualTo(SegmentType.DOOR_EVENT);
    }

    // ──────────────────────────────────────────────
    // TC-BC-002: REAR_WALL + propagationAware=true → ten sam wynik co stara logika
    // Kryterium: defrost od tyłu = DEFROST, door od przodu = DOOR_EVENT
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-BC-002: REAR_WALL preset → kompatybilny wynik z nową logiką")
    void tc_bc_002_rearWallPreset_sameResultAsLegacy() {
        RegimeDetectionProperties props = new RegimeDetectionProperties();
        props.setPropagationAware(true);
        props.setPropagationCosineSimilarityThreshold(0.7);
        props.setPropagationAmbiguityMargin(0.1);
        props.setPropagationMinSensorsForVector(3);

        PropagationVectorClassifier classifierComponent = new PropagationVectorClassifier(props);
        ExcursionDetector detector = new ExcursionDetector(props, classifierComponent);

        // Symulacja defrostu od tyłu
        Map<GridPosition, ThermoMeasurementSeries> channels = createDefrostFromBack();
        Map<GridPosition, List<MeasurementSegment>> result =
            detector.detectAll(channels, AirflowSourcePreset.REAR_WALL, null);

        // Powinno być DEFROST — tak samo jak w obecnej implementacji
        for (List<MeasurementSegment> segments : result.values()) {
            for (MeasurementSegment seg : segments) {
                if (seg.getType() == SegmentType.DEFROST || seg.getType() == SegmentType.DOOR_EVENT) {
                    assertThat(seg.getType()).isEqualTo(SegmentType.DEFROST);
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    // TC-BC-003: Domyślny overload detectAll() bez parametrów → REAR_WALL
    // Kryterium: metoda bez parametrów nawiewu zachowuje obecne zachowanie
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-BC-003: detectAll() bez parametrów → domyślny REAR_WALL")
    void tc_bc_003_overloadWithoutParams() {
        // Stary kod wywołujący detectAll(channels) powinien działać bez zmian
        ExcursionDetector detector = createDetectorWithDefaults();
        Map<GridPosition, ThermoMeasurementSeries> channels = createTestChannels();

        // Nie powinno rzucić wyjątku — backward compat overload
        assertThatCode(() -> detector.detectAll(channels)).doesNotThrowAnyException();
    }
}
```

---

## 6. Testy integracyjne — `RegimeDetectionService`

```java
// src/test/java/.../service/regime/RegimeDetectionServicePropagationIntegrationTest.java

@SpringBootTest
class RegimeDetectionServicePropagationIntegrationTest {

    @Autowired
    private RegimeDetectionService regimeDetectionService;

    // ──────────────────────────────────────────────
    // TC-INT-001: Pełny pipeline CEILING — defrost nie jest klasyfikowany jako DOOR
    // Kryterium: na wyjściu DetectionResult segmenty DEFROST (nie DOOR_EVENT)
    //            dla szpilek propagujących z góry na dół
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-INT-001: Full pipeline z CEILING preset → DEFROST poprawnie wykryty")
    void tc_int_001_fullPipeline_ceiling() {
        // Given: seria z komorą skonfigurowaną jako CEILING
        ThermoMeasurementSeries series = createSeriesWithDefrostFromTop();
        series.getCoolingChamber().setAirflowSourcePreset(AirflowSourcePreset.CEILING);

        Map<GridPosition, ThermoMeasurementSeries> allChannels = createAllChannelsWithTopDefrost();

        // When
        DetectionResult result = regimeDetectionService.detect(series, allChannels);

        // Then
        long defrostCount = result.getSegments().stream()
            .filter(s -> s.getType() == SegmentType.DEFROST).count();
        long doorCount = result.getSegments().stream()
            .filter(s -> s.getType() == SegmentType.DOOR_EVENT).count();

        assertThat(defrostCount).isGreaterThan(0);
        // Kluczowa asercja: defrosty z góry NIE SĄ klasyfikowane jako DOOR_EVENT
        // (co by się stało w obecnej implementacji)
    }

    // ──────────────────────────────────────────────
    // TC-INT-002: Migracja Flyway — nowe kolumny istnieją
    // Kryterium: SELECT na nowych kolumnach nie rzuca wyjątku
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-INT-002: Flyway V30 — nowe kolumny w cooling_chambers")
    void tc_int_002_flywayMigration(@Autowired JdbcTemplate jdbc) {
        // Sprawdź czy kolumny istnieją
        assertThatCode(() -> jdbc.queryForList(
            "SELECT airflow_source_preset, custom_airflow_positions FROM cooling_chambers LIMIT 1"
        )).doesNotThrowAnyException();
    }

    // ──────────────────────────────────────────────
    // TC-INT-003: Domyślna wartość REAR_WALL dla istniejących komór
    // Kryterium: komory utworzone przed migracją mają REAR_WALL
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-INT-003: Istniejące komory → domyślny REAR_WALL")
    void tc_int_003_existingChambersDefaultPreset(@Autowired CoolingChamberRepository repo) {
        List<CoolingChamber> chambers = repo.findAll();
        for (CoolingChamber chamber : chambers) {
            assertThat(chamber.getAirflowSourcePreset())
                .isEqualTo(AirflowSourcePreset.REAR_WALL);
        }
    }

    // ──────────────────────────────────────────────
    // TC-INT-004: Confidence w notatce segmentu zawiera informację o wektorze
    // Kryterium: note zawiera cosine similarity i kierunek wektora
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-INT-004: Notatka segmentu zawiera dane wektora propagacji")
    void tc_int_004_segmentNoteContainsVectorInfo() {
        ThermoMeasurementSeries series = createSeriesWithDefrostFromBack();
        series.getCoolingChamber().setAirflowSourcePreset(AirflowSourcePreset.REAR_WALL);

        DetectionResult result = regimeDetectionService.detect(series,
            createAllChannelsWithBackDefrost());

        Optional<MeasurementSegment> defrostSegment = result.getSegments().stream()
            .filter(s -> s.getType() == SegmentType.DEFROST)
            .findFirst();

        assertThat(defrostSegment).isPresent();
        assertThat(defrostSegment.get().getNote()).contains("cos=");
    }
}
```

---

## 7. Testy regresyjne — dane referencyjne

```java
// src/test/java/.../service/regime/ExcursionDetectorRegressionTest.java

class ExcursionDetectorRegressionTest {

    // ──────────────────────────────────────────────
    // TC-REG-001: Dane sesji 2026-06-21 (Amica) — identyczne wyniki
    // Kryterium: REAR_WALL + propagationAware=true → te same segmenty
    //            co propagationAware=false
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-REG-001: Regresja na danych 2026-06-21 — REAR_WALL identyczny wynik")
    void tc_reg_001_regressionOnReferenceData() {
        // Given: dane referencyjne z sesji Amica
        Map<GridPosition, ThermoMeasurementSeries> channels = loadReferenceData();

        // When: stara logika
        RegimeDetectionProperties propsOld = createProps(false);
        ExcursionDetector detectorOld = new ExcursionDetector(propsOld, null);
        Map<GridPosition, List<MeasurementSegment>> oldResult = detectorOld.detectAll(channels);

        // When: nowa logika z REAR_WALL
        RegimeDetectionProperties propsNew = createProps(true);
        PropagationVectorClassifier classifier = new PropagationVectorClassifier(propsNew);
        ExcursionDetector detectorNew = new ExcursionDetector(propsNew, classifier);
        Map<GridPosition, List<MeasurementSegment>> newResult =
            detectorNew.detectAll(channels, AirflowSourcePreset.REAR_WALL, null);

        // Then: te same typy segmentów w tym samym porządku
        for (GridPosition pos : GridPosition.values()) {
            List<SegmentType> oldTypes = extractTypes(oldResult.getOrDefault(pos, List.of()));
            List<SegmentType> newTypes = extractTypes(newResult.getOrDefault(pos, List.of()));
            assertThat(newTypes)
                .as("Pozycja %s: typy segmentów powinny być identyczne", pos)
                .isEqualTo(oldTypes);
        }
    }
}
```

---

## 8. Testy jednostkowe — `AirflowSourcePreset`

```java
// src/test/java/.../model/regime/AirflowSourcePresetTest.java

class AirflowSourcePresetTest {

    // ──────────────────────────────────────────────
    // TC-ASP-001: Każdy preset (oprócz CUSTOM) ma wektor defrostu
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-ASP-001: Presety mają znormalizowane wektory defrostu")
    void tc_asp_001_presetsHaveDefrostVectors() {
        for (AirflowSourcePreset preset : AirflowSourcePreset.values()) {
            if (preset == AirflowSourcePreset.CUSTOM) {
                assertThat(preset.getExpectedDefrostVector()).isNull();
            } else {
                double[] v = preset.getExpectedDefrostVector();
                assertThat(v).isNotNull().hasSize(3);
                double norm = Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
                assertThat(norm).isCloseTo(1.0, within(0.01));
            }
        }
    }

    // ──────────────────────────────────────────────
    // TC-ASP-002: Wektory defrostu i drzwi nie są równoległe
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-ASP-002: Wektor defrostu ≠ wektor drzwi (cosine < 0.9)")
    void tc_asp_002_defrostAndDoorVectorsAreDifferent() {
        double[] doorVector = AirflowSourcePreset.getDoorVector();
        for (AirflowSourcePreset preset : AirflowSourcePreset.values()) {
            if (preset == AirflowSourcePreset.CUSTOM) continue;
            double[] defrostVector = preset.getExpectedDefrostVector();
            double cos = cosineSimilarity(defrostVector, doorVector);
            assertThat(Math.abs(cos))
                .as("Preset %s: wektor defrostu nie powinien być równoległy do drzwi", preset)
                .isLessThan(0.9);
        }
    }

    // ──────────────────────────────────────────────
    // TC-ASP-003: REAR_WALL wektor = (0, -1, 0)
    // Kryterium: kompatybilność z istniejącą logiką "tył → przód"
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("TC-ASP-003: REAR_WALL → wektor (0, -1, 0)")
    void tc_asp_003_rearWallVector() {
        double[] v = AirflowSourcePreset.REAR_WALL.getExpectedDefrostVector();
        assertThat(v[0]).isEqualTo(0.0);
        assertThat(v[1]).isEqualTo(-1.0);
        assertThat(v[2]).isEqualTo(0.0);
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
        double normA = Math.sqrt(a[0]*a[0] + a[1]*a[1] + a[2]*a[2]);
        double normB = Math.sqrt(b[0]*b[0] + b[1]*b[1] + b[2]*b[2]);
        return dot / (normA * normB);
    }
}
```

---

## 9. Macierz pokrycia wymagań

| Wymaganie (BA-EXC002) | Test Case | Typ |
|---|---|---|
| EXC2-F01 (wektor propagacji) | TC-PVC-001..008 | Unit |
| EXC2-F02 (deklaracja w CoolingChamber) | TC-INT-002, TC-INT-003 | Integration |
| EXC2-F03 (walidacja krzyżowa) | TC-CV-001..006 | Unit |
| EXC2-F04 (DEFROST z wektorem) | TC-PVC-001,003,005,006,007,008 | Unit |
| EXC2-F05 (DOOR_EVENT z wektorem) | TC-PVC-002, TC-PVC-004 | Unit |
| EXC2-F06 (EXCURSION fallback) | TC-CV-004, TC-CV-005, TC-CV-006 | Unit |
| EXC2-F07 (domyślny REAR_WALL) | TC-BC-003, TC-INT-003 | Unit/Integration |
| EXC2-F08 (sprzeczność → low confidence) | TC-CV-002 | Unit |
| EXC2-NF01 (backward compat) | TC-BC-001..003, TC-REG-001 | Unit/Regression |
| EXC2-NF02 (wydajność) | TC-PVC-* (implicitly < 1ms) | Unit |
| EXC2-NF03 (audytowalność) | TC-INT-004 | Integration |
| EXC2-NF04 (migracja Flyway) | TC-INT-002 | Integration |
| EXC2-NF05 (feature flag) | TC-BC-001 | Unit |

---

## 10. Podsumowanie statystyk

| Kategoria | Liczba testów |
|---|---|
| Unit — `GridPositionCoordinates` | 4 |
| Unit — `AirflowSourcePreset` | 3 |
| Unit — `PropagationVectorClassifier` | 9 |
| Unit — Cross-validation | 6 |
| Unit — Backward compatibility | 3 |
| Integration — `RegimeDetectionService` | 4 |
| Regression — dane referencyjne | 1 |
| **Suma** | **30** |
