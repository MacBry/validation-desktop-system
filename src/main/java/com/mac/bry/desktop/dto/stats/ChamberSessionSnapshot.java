package com.mac.bry.desktop.dto.stats;

import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Migawka jednej sesji rewalidacji komory — agregaty policzone ze statystyk
 * zapisanych per seria (pozycja). Podstawa dashboardu porównań między sesjami
 * oraz baseline'u trybu MONITORING.
 */
@Value
@Builder
public class ChamberSessionSnapshot {

    /** Identyfikator grupy rewalidacji (wspólny dla serii jednej sesji). */
    String groupId;

    /** Data sesji — najwcześniejszy import serii w grupie. */
    LocalDateTime sessionDate;

    /** Liczba pozycji (serii) w sesji. */
    int seriesCount;

    /** Średnia temperatura komory — średnia ze średnich pozycji [°C]. */
    Double meanTemperature;

    /** Jednorodność przestrzenna: max(avg) − min(avg) między pozycjami [°C]. */
    Double spatialDeltaT;

    /** Najgorsze (największe) odchylenie standardowe wśród pozycji [°C]. */
    Double maxStdDeviation;

    /** Pozycja o najwyższej średniej (hotspot sesji). */
    GridPosition hotspotPosition;

    /** Pozycja o najniższej średniej (coldspot sesji). */
    GridPosition coldspotPosition;

    /** Suma wykrytych szpilek we wszystkich seriach sesji. */
    int totalSpikeCount;

    // ── Delty względem poprzedniej sesji (null dla pierwszej) ───────────────

    /** Zmiana ΔT przestrzennego vs poprzednia sesja [°C]; dodatnia = pogorszenie. */
    Double deltaSpatialDeltaT;

    /** Zmiana najgorszego std dev vs poprzednia sesja [°C]; dodatnia = pogorszenie. */
    Double deltaMaxStdDeviation;
}
