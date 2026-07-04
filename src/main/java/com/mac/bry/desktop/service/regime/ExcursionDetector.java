package com.mac.bry.desktop.service.regime;

import com.mac.bry.desktop.config.RegimeDetectionProperties;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.model.regime.AirflowSourcePreset;
import com.mac.bry.desktop.model.regime.DetectionSource;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Detektor ekskursji (anomalii dynamicznych) dla Fazy 2 (DP-001 §4.4).
 * Wykrywa szpilki temperaturowe i klasyfikuje je jako DEFROST / DOOR_EVENT / EXCURSION
 * na podstawie gradientu temperatury, czasu powrotu, okresowości oraz sygnatury przestrzennej.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExcursionDetector {

    private final RegimeDetectionProperties props;
    private final PropagationVectorClassifier propagationClassifier;

    /**
     * Wykonuje detekcję szpilek dla wszystkich kanałów, analizując okresowość
     * oraz nakładanie się czasowe celem detekcji sygnatury przestrzennej.
     * Wariant bez konfiguracji nawiewu — domyślny preset REAR_WALL (backward compat).
     *
     * @param allChannels Mapa pozycji na odpowiadające im serie pomiarowe.
     * @return Mapa z wykrytymi szpilkami (reżimami ekskursji) per pozycja.
     */
    public Map<GridPosition, List<MeasurementSegment>> detectAll(
            Map<GridPosition, ThermoMeasurementSeries> allChannels) {
        return detectAll(allChannels, AirflowSourcePreset.REAR_WALL, null);
    }

    /**
     * Wykonuje detekcję szpilek z uwzględnieniem deklarowanej konfiguracji
     * źródła nawiewu komory (IMPL-EXC002 §4).
     *
     * @param allChannels            Mapa pozycji na odpowiadające im serie pomiarowe.
     * @param airflowSourcePreset    Deklarowany preset źródła nawiewu.
     * @param customAirflowPositions Pozycje bliskiego pola dla trybu CUSTOM (może być null).
     * @return Mapa z wykrytymi szpilkami (reżimami ekskursji) per pozycja.
     */
    public Map<GridPosition, List<MeasurementSegment>> detectAll(
            Map<GridPosition, ThermoMeasurementSeries> allChannels,
            AirflowSourcePreset airflowSourcePreset,
            Set<GridPosition> customAirflowPositions) {

        Map<GridPosition, List<MeasurementSegment>> rawSpikesMap = new HashMap<>();

        // 1. Detekcja szpilek dla każdego kanału osobno
        for (Map.Entry<GridPosition, ThermoMeasurementSeries> entry : allChannels.entrySet()) {
            GridPosition pos = entry.getKey();
            ThermoMeasurementSeries series = entry.getValue();
            List<MeasurementSegment> rawSpikes = detectRawSpikes(series);
            rawSpikesMap.put(pos, rawSpikes);
        }

        // 2. Klasyfikacja przestrzenno-okresowa szpilek
        classifySpikes(rawSpikesMap, airflowSourcePreset, customAirflowPositions);

        return rawSpikesMap;
    }

    private List<MeasurementSegment> detectRawSpikes(ThermoMeasurementSeries series) {
        List<MeasurementSegment> spikes = new ArrayList<>();
        List<ThermoMeasurementPoint> points = series.getMeasurements();
        if (points == null || points.size() < 2) return spikes;

        int n = points.size();
        Integer interval = series.getLoggingIntervalMinutes();
        if (interval == null || interval <= 0) interval = 1;

        double gradientThreshold = props.getExcursionGradientThreshold();
        int maxReturnWindowPoints = (int) Math.ceil((double) props.getExcursionReturnWindowMinutes() / interval);

        int i = 1;
        while (i < n) {
            double prevTemp = points.get(i - 1).getRawCelsius();
            double currTemp = points.get(i).getRawCelsius();
            double diff = currTemp - prevTemp;
            double rate = diff / interval;

            if (rate >= gradientThreshold) {
                // Początek szpilki
                int startIdx = i - 1;
                LocalDateTime startTime = points.get(startIdx).getTimestampLocal();

                // Znajdź szczyt (max temp) w oknie maxReturnWindowPoints
                int endSearchIdx = Math.min(n, startIdx + maxReturnWindowPoints);
                int peakIdx = startIdx;
                double maxTemp = points.get(startIdx).getRawCelsius();
                for (int k = startIdx; k < endSearchIdx; k++) {
                    if (points.get(k).getRawCelsius() > maxTemp) {
                        maxTemp = points.get(k).getRawCelsius();
                        peakIdx = k;
                    }
                }

                // Znajdź punkt powrotu (gdzie temperatura opada z powrotem do temp startowej + 1.0 stopień)
                int returnIdx = -1;
                double baseline = points.get(startIdx).getRawCelsius();
                for (int k = peakIdx; k < endSearchIdx; k++) {
                    if (points.get(k).getRawCelsius() <= baseline + 1.0) {
                        returnIdx = k;
                        break;
                    }
                }

                if (returnIdx != -1) {
                    LocalDateTime endTime = points.get(returnIdx).getTimestampLocal();
                    spikes.add(MeasurementSegment.builder()
                            .series(series)
                            .fromTimestamp(startTime)
                            .toTimestamp(endTime)
                            .type(SegmentType.EXCURSION) // tymczasowo, zostanie zreklasyfikowany
                            .confidence(0.9)
                            .source(DetectionSource.ALGORITHM)
                            .accepted(true)
                            .note("Szybki skok temperatury o " + String.format("%.2f", (maxTemp - baseline)) + "°C")
                            .build());
                    i = returnIdx + 1; // przeskocz za koniec szpilki
                    continue;
                }
            }
            i++;
        }

        return spikes;
    }

    private void classifySpikes(Map<GridPosition, List<MeasurementSegment>> rawSpikesMap,
                                AirflowSourcePreset airflowSourcePreset,
                                Set<GridPosition> customAirflowPositions) {
        // A. Sprawdzenie okresowości (Defrost) per kanał
        for (GridPosition pos : rawSpikesMap.keySet()) {
            List<MeasurementSegment> spikes = rawSpikesMap.get(pos);
            if (spikes.size() >= 2) {
                List<Long> intervals = new ArrayList<>();
                for (int i = 1; i < spikes.size(); i++) {
                    long minutes = ChronoUnit.MINUTES.between(
                            spikes.get(i - 1).getFromTimestamp(),
                            spikes.get(i).getFromTimestamp());
                    intervals.add(minutes);
                }

                boolean isPeriodic = true;
                double sum = 0;
                for (long val : intervals) {
                    sum += val;
                    if (val < 240 || val > 720) { // co 4-12 godzin
                        isPeriodic = false;
                        break;
                    }
                }
                
                if (isPeriodic && !intervals.isEmpty()) {
                    double avg = sum / intervals.size();
                    for (long val : intervals) {
                        if (Math.abs(val - avg) > 30) { // tolerancja stabilności interwału 30 min
                            isPeriodic = false;
                            break;
                        }
                    }
                } else {
                    isPeriodic = false;
                }

                if (isPeriodic) {
                    for (MeasurementSegment seg : spikes) {
                        seg.setType(SegmentType.DEFROST);
                        seg.setNote("Wykryto okresowy cykl rozmrażania (defrost)");
                    }
                }
            }
        }

        // B. Przestrzenna klasyfikacja nakładających się zdarzeń (Defrost vs Door Event)
        List<List<PositionSpike>> overlappingGroups = groupOverlappingSpikes(rawSpikesMap);

        for (List<PositionSpike> group : overlappingGroups) {
            if (group.size() > 1) {
                if (props.isPropagationAware()) {
                    classifyByPropagationVector(group, airflowSourcePreset, customAirflowPositions);
                } else {
                    classifyByFrontPosition(group); // dotychczasowa logika — backward compat
                }
            } else if (group.size() == 1) {
                PositionSpike ps = group.get(0);
                if (ps.segment.getType() == SegmentType.EXCURSION) {
                    long durMinutes = ChronoUnit.MINUTES.between(
                            ps.segment.getFromTimestamp(), ps.segment.getToTimestamp());
                    if (durMinutes <= 20) {
                        ps.segment.setType(SegmentType.DOOR_EVENT);
                        ps.segment.setNote("Otwarcie drzwi (zdarzenie lokalne)");
                    } else {
                        ps.segment.setType(SegmentType.EXCURSION);
                        ps.segment.setNote("Niezidentyfikowana ekskursja temperatury");
                    }
                }
            }
        }
    }

    /**
     * Dotychczasowa klasyfikacja przestrzenna: pierwsza reakcja na froncie → DOOR_EVENT,
     * w głębi → DEFROST. Poprawna tylko dla komór z ewaporatorem na tylnej ścianie.
     * Używana gdy {@code propagationAware=false}.
     */
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

    /**
     * Klasyfikacja przestrzenna na podstawie wektora propagacji ciepła
     * i deklarowanego źródła nawiewu (IMPL-EXC002 §4.2).
     */
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

        // Pełna traceability dla audytora (IMPL-EXC002 §7): nota zawiera wektor,
        // cosine similarity, deklarowane źródło i confidence.
        String fullNote = String.format("%s. Deklarowane źródło: %s. Confidence: %.2f",
                result.note(), preset.getLabel(), result.confidence());

        for (PositionSpike ps : group) {
            ps.segment.setType(result.type());
            ps.segment.setConfidence(result.confidence());
            ps.segment.setNote(fullNote);
        }
    }

    private boolean isFrontPosition(GridPosition pos) {
        return pos == GridPosition.TOP_FRONT_LEFT 
            || pos == GridPosition.TOP_FRONT_RIGHT 
            || pos == GridPosition.BOTTOM_FRONT_LEFT 
            || pos == GridPosition.BOTTOM_FRONT_RIGHT;
    }

    private List<List<PositionSpike>> groupOverlappingSpikes(
            Map<GridPosition, List<MeasurementSegment>> rawSpikesMap) {
        
        List<PositionSpike> allSpikes = new ArrayList<>();
        for (Map.Entry<GridPosition, List<MeasurementSegment>> entry : rawSpikesMap.entrySet()) {
            for (MeasurementSegment seg : entry.getValue()) {
                allSpikes.add(new PositionSpike(entry.getKey(), seg));
            }
        }

        List<List<PositionSpike>> groups = new ArrayList<>();
        boolean[] visited = new boolean[allSpikes.size()];

        for (int i = 0; i < allSpikes.size(); i++) {
            if (visited[i]) continue;
            List<PositionSpike> group = new ArrayList<>();
            queueGroup(i, allSpikes, visited, group);
            groups.add(group);
        }

        return groups;
    }

    private void queueGroup(int startIdx, List<PositionSpike> allSpikes, boolean[] visited, List<PositionSpike> group) {
        Queue<Integer> queue = new LinkedList<>();
        queue.add(startIdx);
        visited[startIdx] = true;

        while (!queue.isEmpty()) {
            int currIdx = queue.poll();
            PositionSpike curr = allSpikes.get(currIdx);
            group.add(curr);

            for (int i = 0; i < allSpikes.size(); i++) {
                if (!visited[i]) {
                    PositionSpike other = allSpikes.get(i);
                    if (overlaps(curr.segment, other.segment)) {
                        visited[i] = true;
                        queue.add(i);
                    }
                }
            }
        }
    }

    private boolean overlaps(MeasurementSegment s1, MeasurementSegment s2) {
        return !s1.getToTimestamp().isBefore(s2.getFromTimestamp()) 
            && !s2.getToTimestamp().isBefore(s1.getFromTimestamp());
    }

    private static class PositionSpike {
        final GridPosition position;
        final MeasurementSegment segment;

        PositionSpike(GridPosition position, MeasurementSegment segment) {
            this.position = position;
            this.segment = segment;
        }
    }
}
