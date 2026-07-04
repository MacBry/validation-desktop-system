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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Klasyfikator przestrzenny ekskursji na podstawie wektora propagacji ciepła
 * (IMPL-EXC002 §3). Wyznacza wektor z opóźnień reakcji czujników w grupie
 * nakładających się szpilek i porównuje go (cosine similarity) z kierunkiem
 * oczekiwanym dla defrostu (z deklaracji źródła nawiewu) oraz dla otwarcia drzwi.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PropagationVectorClassifier {

    private final RegimeDetectionProperties props;

    /** Wynik klasyfikacji przestrzennej. */
    public record ClassificationResult(
            SegmentType type,
            double confidence,
            double[] propagationVector,
            double cosineDefrost,
            double cosineDoor,
            String note
    ) {}

    /** Dane wejściowe: pozycja czujnika i czas startu szpilki. */
    public record SpikeEvent(GridPosition position, LocalDateTime startTime) {}

    /**
     * Klasyfikuje grupę nakładających się szpilek na podstawie wektora propagacji.
     *
     * @param spikes          lista szpilek z pozycjami i czasami startu
     * @param preset          zadeklarowany preset źródła nawiewu
     * @param customPositions pozycje bliskiego pola (tylko dla CUSTOM), może być null
     * @return wynik klasyfikacji z confidence i wektorem
     */
    public ClassificationResult classify(
            List<SpikeEvent> spikes,
            AirflowSourcePreset preset,
            Set<GridPosition> customPositions) {

        if (spikes == null || spikes.size() < 2) {
            return new ClassificationResult(
                    SegmentType.EXCURSION, 0.5, null, 0, 0,
                    "Za mało czujników do analizy wektora propagacji");
        }

        // 1. Wyznacz t_min i oblicz lagi (BA-EXC002 §4.2)
        LocalDateTime tMin = spikes.stream()
                .map(SpikeEvent::startTime)
                .min(Comparator.naturalOrder())
                .orElseThrow();

        Map<GridPosition, Long> lags = new LinkedHashMap<>();
        for (SpikeEvent spike : spikes) {
            long lagSeconds = ChronoUnit.SECONDS.between(tMin, spike.startTime());
            lags.put(spike.position(), lagSeconds);
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

        // 3a. Reakcja jednoczesna wszystkich czujników → brak informacji kierunkowej
        //     (BA-EXC002 §4.2 krok 3-4: czujniki lag=0 nie niosą kierunku)
        if (maxLag == 0) {
            return new ClassificationResult(
                    SegmentType.EXCURSION, 0.5, null, 0, 0,
                    "Wektor propagacji nierozróżnialny (wszystkie czujniki reagują jednocześnie)");
        }

        // 3b. Za mało czujników z niezerowym lagiem → fallback do samej deklaracji
        long nonZeroLagCount = lags.values().stream().filter(v -> v > 0).count();
        if (nonZeroLagCount < props.getPropagationMinSensorsForVector() - 1) {
            return classifyByDeclarationOnly(sourcePositions, preset, customPositions);
        }

        // 4. Centroidy źródła i dalekiego pola
        double[] centroidSource = computeCentroid(sourcePositions);
        double[] centroidFar = computeCentroid(farPositions);

        // 5. Wektor propagacji
        double[] vector = new double[]{
                centroidFar[0] - centroidSource[0],
                centroidFar[1] - centroidSource[1],
                centroidFar[2] - centroidSource[2]
        };
        double norm = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);

        if (norm < 0.1) {
            return new ClassificationResult(
                    SegmentType.EXCURSION, 0.5, vector, 0, 0,
                    "Wektor propagacji nierozróżnialny (wszystkie czujniki reagują jednocześnie)");
        }

        vector[0] /= norm;
        vector[1] /= norm;
        vector[2] /= norm;

        // 6. Wektory referencyjne
        double[] expectedDefrost = resolveDefrostVector(preset, customPositions);
        double[] expectedDoor = AirflowSourcePreset.getDoorVector();

        // 7. Cosine similarity
        double cosDefrost = expectedDefrost != null ? cosineSimilarity(vector, expectedDefrost) : 0.0;
        double cosDoor = cosineSimilarity(vector, expectedDoor);

        double threshold = props.getPropagationCosineSimilarityThreshold();
        double margin = props.getPropagationAmbiguityMargin();

        // 8. Klasyfikacja (BA-EXC002 §4.4)
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
            if (expectedDefrost != null && cosDefrost < threshold) {
                note += " | UWAGA: Wektor propagacji niezgodny z deklarowanym źródłem nawiewu ("
                        + preset.getLabel() + ")";
            }
        }

        // 9. Korekta confidence na podstawie zgodności z deklaracją (BA-EXC002 §4.5)
        double finalConfidence = adjustConfidenceByDeclaration(
                type, baseConfidence, sourcePositions, preset, customPositions);

        if (finalConfidence < baseConfidence - 0.2) {
            note += " | UWAGA: Wektor niezgodny z deklarowanym źródłem nawiewu ("
                    + preset.getLabel() + ")";
        }

        return new ClassificationResult(type, finalConfidence, vector, cosDefrost, cosDoor, note);
    }

    /**
     * Fallback gdy lag między czujnikami jest nierozróżnialny (np. długi interwał
     * logowania): klasyfikacja wyłącznie na podstawie zgodności pozycji pierwszej
     * reakcji z deklarowaną strefą bliskiego pola (BA-EXC002 §8, ryzyko #1).
     */
    private ClassificationResult classifyByDeclarationOnly(
            List<GridPosition> sourcePositions,
            AirflowSourcePreset preset,
            Set<GridPosition> customPositions) {

        Set<GridPosition> nearField = resolveNearFieldPositions(preset, customPositions);
        if (nearField.isEmpty() || sourcePositions.isEmpty()) {
            return new ClassificationResult(
                    SegmentType.EXCURSION, 0.5, null, 0, 0,
                    "Za mało czujników z niezerowym opóźnieniem — wektor propagacji niewyznaczalny");
        }

        long inNearField = sourcePositions.stream().filter(nearField::contains).count();
        boolean sourceNearDeclared = inNearField * 2 >= sourcePositions.size();

        if (sourceNearDeclared) {
            return new ClassificationResult(
                    SegmentType.DEFROST, 0.6, null, 0, 0,
                    "Klasyfikacja wg deklaracji źródła (lag nierozróżnialny): pierwsza reakcja w strefie "
                            + preset.getLabel());
        }
        return new ClassificationResult(
                SegmentType.DOOR_EVENT, 0.6, null, 0, 0,
                "Klasyfikacja wg deklaracji źródła (lag nierozróżnialny): pierwsza reakcja poza strefą "
                        + preset.getLabel());
    }

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
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        double normA = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        double normB = Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
        if (normA < 1e-9 || normB < 1e-9) return 0.0;
        return dot / (normA * normB);
    }

    private double[] resolveDefrostVector(
            AirflowSourcePreset preset, Set<GridPosition> customPositions) {
        if (preset == AirflowSourcePreset.CUSTOM && customPositions != null && !customPositions.isEmpty()) {
            // Wektor od centroidu CUSTOM pozycji do centroidu pozostałych
            Set<GridPosition> farField = EnumSet.allOf(GridPosition.class);
            farField.removeAll(customPositions);
            if (farField.isEmpty()) return new double[]{0, 0, 0};
            double[] cSource = computeCentroid(new ArrayList<>(customPositions));
            double[] cFar = computeCentroid(new ArrayList<>(farField));
            double[] v = {cFar[0] - cSource[0], cFar[1] - cSource[1], cFar[2] - cSource[2]};
            double norm = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            if (norm > 1e-9) {
                v[0] /= norm;
                v[1] /= norm;
                v[2] /= norm;
            }
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
            return sourceMatchesDeclaration
                    ? Math.min(baseConfidence + 0.1, 1.0)
                    : Math.max(baseConfidence - 0.25, 0.3);
        }
        if (classifiedType == SegmentType.DOOR_EVENT) {
            return !sourceMatchesDeclaration
                    ? Math.min(baseConfidence + 0.1, 1.0)
                    : Math.max(baseConfidence - 0.25, 0.3);
        }
        return baseConfidence;
    }

    /** Strefa bliskiego pola per preset (BA-EXC002 §2.3). */
    Set<GridPosition> resolveNearFieldPositions(
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
}
