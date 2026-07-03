package com.mac.bry.desktop.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Collections;
import java.util.Map;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpatialStatsResult {

    // --- Globalny rozstęp przestrzenny (wszystkie czujniki) ---
    private double meanSpatialRange;
    private double maxSpatialRange;
    private Map<LocalDateTime, Double> spatialRangesOverTime;

    // --- Rozstęp przestrzenny poziomu GÓRA (TOP_*) ---
    private double meanRangeTop;
    private double maxRangeTop;
    private Map<LocalDateTime, Double> spatialRangesOverTimeTop;

    // --- Rozstęp przestrzenny poziomu DÓŁ (BOTTOM_*) ---
    private double meanRangeBottom;
    private double maxRangeBottom;
    private Map<LocalDateTime, Double> spatialRangesOverTimeBottom;

    // --- Gradient pionowy: Avg(TOP) − Avg(BOTTOM) w każdym timestamp ---
    /** Średnia wartość gradientu pionowego [°C] w całym oknie pomiarowym. */
    private double meanVerticalGradient;
    /** Maksymalna (absolutna) wartość gradientu pionowego [°C]. */
    private double maxVerticalGradient;
    /** Seria czasowa gradientu pionowego: timestamp → ΔT_vertical [°C]. */
    private Map<LocalDateTime, Double> verticalGradientOverTime;

    // --- Statystyki Jednorodności Poziomów Fizycznych ---
    private boolean normallyDistributed;
    private double homogeneityPValue;
    private String homogeneityTestName;
    private String homogeneityVerdict;
    private WelchAnovaResult welchAnovaResult;
    private java.util.List<GamesHowellResult> gamesHowellResults;
    private double kruskalWallisPValue;
    private java.util.List<DunnResult> dunnResults;

    /** Zwraca true gdy dane poziomowe są dostępne (tzn. były czujniki na obu poziomach). */
    public boolean hasLevelData() {
        return spatialRangesOverTimeTop != null && !spatialRangesOverTimeTop.isEmpty()
            && spatialRangesOverTimeBottom != null && !spatialRangesOverTimeBottom.isEmpty();
    }

    /** Konstruktor kompatybilny wstecz — dla kodu używającego starego 3-arg konstruktora. */
    public SpatialStatsResult(double meanSpatialRange, double maxSpatialRange,
                              Map<LocalDateTime, Double> spatialRangesOverTime) {
        this.meanSpatialRange = meanSpatialRange;
        this.maxSpatialRange = maxSpatialRange;
        this.spatialRangesOverTime = spatialRangesOverTime;
        this.spatialRangesOverTimeTop = Collections.emptyMap();
        this.spatialRangesOverTimeBottom = Collections.emptyMap();
        this.verticalGradientOverTime = Collections.emptyMap();
    }
}

