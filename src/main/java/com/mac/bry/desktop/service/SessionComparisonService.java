package com.mac.bry.desktop.service;

import com.mac.bry.desktop.dto.stats.ChamberSessionSnapshot;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.repository.ThermoMeasurementSeriesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Porównania między sesjami rewalidacji tej samej komory.
 * <p>
 * Agregaty liczone ze statystyk zapisanych per seria (kolumny V19) —
 * bez wczytywania punktów pomiarowych, więc historia komory z wieloma
 * sesjami buduje się natychmiast. Sesja = grupa serii o wspólnym
 * {@code revalidationGroupId}; szybkie odczyty USB bez grupy są pomijane.
 * <p>
 * Zastosowania: dashboard trendów (degradacja jednorodności/stabilności
 * w czasie) oraz baseline dla trybu MONITORING (DP-001 §4.5).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionComparisonService {

    private final ThermoMeasurementSeriesRepository seriesRepository;

    /**
     * Historia sesji komory, chronologicznie (najstarsza pierwsza),
     * z deltami względem sesji poprzedniej.
     */
    @Transactional(readOnly = true)
    public List<ChamberSessionSnapshot> getSessionHistory(Long chamberId) {
        Map<String, List<ThermoMeasurementSeries>> groups =
                seriesRepository.findByCoolingChamberId(chamberId).stream()
                        .filter(s -> s.getRevalidationGroupId() != null
                                && !s.getRevalidationGroupId().isBlank())
                        .collect(Collectors.groupingBy(ThermoMeasurementSeries::getRevalidationGroupId));

        List<ChamberSessionSnapshot> snapshots = groups.entrySet().stream()
                .map(e -> buildSnapshot(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ChamberSessionSnapshot::getSessionDate))
                .collect(Collectors.toList());

        return withDeltas(snapshots);
    }

    /**
     * Baseline trybu MONITORING: statystyki (avg, std) tej samej komory
     * i pozycji z najnowszej WCZEŚNIEJSZEJ sesji niż wskazana grupa.
     *
     * @param currentGroupId grupa bieżącej sesji — wykluczana z wyszukiwania;
     *                       {@code null} = weź najnowszą dostępną sesję
     */
    @Transactional(readOnly = true)
    public Optional<PositionBaseline> findBaseline(Long chamberId, GridPosition position,
                                                   String currentGroupId) {
        return seriesRepository.findByCoolingChamberId(chamberId).stream()
                .filter(s -> position == s.getGridPosition())
                .filter(s -> s.getRevalidationGroupId() != null
                        && !s.getRevalidationGroupId().isBlank()
                        && !s.getRevalidationGroupId().equals(currentGroupId))
                .filter(s -> s.getAvgTemperature() != null && s.getStdDeviation() != null)
                .max(Comparator.comparing(ThermoMeasurementSeries::getImportedAt))
                .map(s -> new PositionBaseline(
                        s.getRevalidationGroupId(),
                        s.getImportedAt(),
                        s.getAvgTemperature(),
                        s.getStdDeviation()));
    }

    /** Baseline jednej pozycji z poprzedniej sesji. */
    public record PositionBaseline(String groupId, LocalDateTime sessionDate,
                                   double avgTemperature, double stdDeviation) {
    }

    // ──────────────────────────────────────────────────────────────────────────

    private ChamberSessionSnapshot buildSnapshot(String groupId, List<ThermoMeasurementSeries> series) {
        LocalDateTime date = series.stream()
                .map(ThermoMeasurementSeries::getImportedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(LocalDateTime.MIN);

        List<ThermoMeasurementSeries> withAvg = series.stream()
                .filter(s -> s.getAvgTemperature() != null)
                .toList();

        Double meanTemp = null, spatialDeltaT = null, maxStd = null;
        GridPosition hotspot = null, coldspot = null;

        if (!withAvg.isEmpty()) {
            meanTemp = withAvg.stream()
                    .mapToDouble(ThermoMeasurementSeries::getAvgTemperature)
                    .average().orElse(Double.NaN);

            ThermoMeasurementSeries hottest = withAvg.stream()
                    .max(Comparator.comparing(ThermoMeasurementSeries::getAvgTemperature)).orElseThrow();
            ThermoMeasurementSeries coldest = withAvg.stream()
                    .min(Comparator.comparing(ThermoMeasurementSeries::getAvgTemperature)).orElseThrow();
            spatialDeltaT = hottest.getAvgTemperature() - coldest.getAvgTemperature();
            hotspot = hottest.getGridPosition();
            coldspot = coldest.getGridPosition();
        }

        Optional<Double> worstStd = series.stream()
                .map(ThermoMeasurementSeries::getStdDeviation)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder());
        if (worstStd.isPresent()) {
            maxStd = worstStd.get();
        }

        int spikes = series.stream()
                .map(ThermoMeasurementSeries::getSpikeCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();

        return ChamberSessionSnapshot.builder()
                .groupId(groupId)
                .sessionDate(date)
                .seriesCount(series.size())
                .meanTemperature(meanTemp)
                .spatialDeltaT(spatialDeltaT)
                .maxStdDeviation(maxStd)
                .hotspotPosition(hotspot)
                .coldspotPosition(coldspot)
                .totalSpikeCount(spikes)
                .build();
    }

    /** Przelicza delty vs poprzednia sesja — wynik to nowe, kompletne snapshoty. */
    private List<ChamberSessionSnapshot> withDeltas(List<ChamberSessionSnapshot> chronological) {
        List<ChamberSessionSnapshot> result = new ArrayList<>(chronological.size());
        ChamberSessionSnapshot previous = null;
        for (ChamberSessionSnapshot current : chronological) {
            Double dDeltaT = null, dStd = null;
            if (previous != null) {
                if (current.getSpatialDeltaT() != null && previous.getSpatialDeltaT() != null) {
                    dDeltaT = current.getSpatialDeltaT() - previous.getSpatialDeltaT();
                }
                if (current.getMaxStdDeviation() != null && previous.getMaxStdDeviation() != null) {
                    dStd = current.getMaxStdDeviation() - previous.getMaxStdDeviation();
                }
            }
            result.add(ChamberSessionSnapshot.builder()
                    .groupId(current.getGroupId())
                    .sessionDate(current.getSessionDate())
                    .seriesCount(current.getSeriesCount())
                    .meanTemperature(current.getMeanTemperature())
                    .spatialDeltaT(current.getSpatialDeltaT())
                    .maxStdDeviation(current.getMaxStdDeviation())
                    .hotspotPosition(current.getHotspotPosition())
                    .coldspotPosition(current.getColdspotPosition())
                    .totalSpikeCount(current.getTotalSpikeCount())
                    .deltaSpatialDeltaT(dDeltaT)
                    .deltaMaxStdDeviation(dStd)
                    .build());
            previous = current;
        }
        return result;
    }
}
