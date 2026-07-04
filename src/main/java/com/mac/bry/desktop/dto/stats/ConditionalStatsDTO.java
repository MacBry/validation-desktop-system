package com.mac.bry.desktop.dto.stats;

import com.mac.bry.desktop.model.regime.RunMode;
import com.mac.bry.desktop.model.regime.VerdictStatus;
import lombok.Builder;
import lombok.Data;

/**
 * Statystyki obliczone warunkowo — wyłącznie na punktach należących
 * do zaakceptowanych segmentów {@code STEADY_STATE}.
 * <p>
 * Wypełniany przez {@code RegimeAwareStatsService} i dołączany do sesji rewalidacji
 * jako {@code conditionalStatsMap} per pozycja. Renderery PDF odczytują dane z tej mapy.
 * <p>
 * Zgodnie z BR-02 i BR-03 (BA-DP001): statystyki kwalifikacyjne wyłącznie na STEADY_STATE,
 * prezentowane obok statystyk całego przebiegu.
 */
@Data
@Builder
public class ConditionalStatsDTO {

    /** Etykieta pozycji przestrzennej (np. "Góra - Tył-Lewy"). */
    private String positionLabel;

    /** Numer seryjny rejestratora. */
    private String recorderSerialNumber;

    /** Tryb runu deklarowany przez operatora. */
    private RunMode runMode;

    // ── Metadane pokrycia ──────────────────────────────────────────────────

    /**
     * Czy seria ma wystarczająco danych STEADY_STATE do obliczeń.
     * {@code false} gdy liczba punktów &lt; {@code minSteadyPointsForStats}.
     */
    private boolean hasSteadyStateData;

    /** Liczba punktów pomiarowych należących do zaakceptowanych segmentów STEADY_STATE. */
    private int steadyStatePointCount;

    /** Odsetek przebiegu sklasyfikowany jako STEADY_STATE [%]. */
    private double steadyStateCoveragePercent;

    // ── Statystyki na STEADY_STATE ─────────────────────────────────────────

    private Double minSteady;
    private Double maxSteady;
    private Double avgSteady;
    private Double medianSteady;
    private Double stdDevSteady;
    private Double cpSteady;
    private Double cpkSteady;

    /** Rozszerzona niepewność pomiaru U wyznaczona na punktach STEADY_STATE [°C]. */
    private Double expandedUncertaintySteady;

    // ── Werdykt warunkowy ──────────────────────────────────────────────────

    /**
     * Czy Cpk(STEADY) ≥ 1,0 — granica zdolności kwalifikacyjnej.
     * {@code null} gdy brak danych STEADY_STATE lub brak LSL/USL.
     */
    private Boolean cpkPassSteady;

    /**
     * Czy std dev(STEADY) mieści się w limicie WHO.
     * Lodówka: ≤ 0,3°C | Zamrażarka: ≤ 1,0°C.
     */
    private Boolean stdDevPassSteady;

    /** Notatka werdyktu — powód WARNING/INCONCLUSIVE/FAIL lub null gdy PASS. */
    private String verdictNote;

    /**
     * Status werdyktu wyznaczony przez {@code VerdictPolicy} zależną od trybu runu
     * (DP-001 §4.5). PASS gdy wszystkie kryteria spełnione.
     */
    private VerdictStatus verdictStatus;
}
