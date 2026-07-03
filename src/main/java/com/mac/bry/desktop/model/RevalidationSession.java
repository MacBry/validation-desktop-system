package com.mac.bry.desktop.model;

import com.mac.bry.desktop.dto.stats.ConditionalStatsDTO;
import com.mac.bry.desktop.dto.stats.CorrectedStatsDTO;
import com.mac.bry.desktop.dto.stats.SpatialStatsResult;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.RunMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model stanu sesji kreatora rewalidacji komory chłodniczej w pamięci (Wizard State).
 * Łączy urządzenie chłodnicze, wybraną komorę oraz przypisane do 8 narożników serie pomiarowe z bazodanowymi danymi wzorcowania.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevalidationSession {

    @Builder.Default
    private String sessionId = java.util.UUID.randomUUID().toString();

    private CoolingDevice coolingDevice;
    private CoolingChamber coolingChamber;

    @Builder.Default
    private GxPProcedureType procedureType = GxPProcedureType.PERIODIC_REVALIDATION;

    @Builder.Default
    private Map<GridPosition, PositionData> assignedPositions = new HashMap<>();

    /**
     * Mapa ze statystykami skorygowanymi (korekcja wzorcowania) per pozycja.
     * Wypełniana przez {@code RevalidationReportPdfRenderer} przed renderowaniem sekcji PDF.
     * Renderery sekcji odczytują dane z tej mapy — bez zmiany interfejsu PdfSectionRenderer.
     */
    @Builder.Default
    private Map<GridPosition, CorrectedStatsDTO> correctedStatsMap = new HashMap<>();

    // ── Regime-Aware Layer (DP-001 Faza 1) ────────────────────────────────

    /**
     * Tryb runu zadeklarowany przez operatora przed wygenerowaniem raportu.
     * Domyślnie CHARACTERIZATION — bezpieczna wartość (nie nakłada kryteriów kwalifikacyjnych).
     */
    @Builder.Default
    private RunMode runMode = RunMode.CHARACTERIZATION;

    /**
     * Mapa wykrytych segmentów reżimów pracy per pozycja.
     * Wypełniana przez {@code RevalidationReportPdfRenderer} przed renderowaniem.
     * Klucz: GridPosition, Wartość: lista segmentów posortowanych chronologicznie.
     */
    @Builder.Default
    private Map<GridPosition, List<MeasurementSegment>> detectedSegmentsMap = new HashMap<>();

    /**
     * Mapa statystyk warunkowych (STEADY_STATE only) per pozycja.
     * Wypełniana przez {@code RevalidationReportPdfRenderer} przed renderowaniem.
     * {@code null} wartości gdy feature flag wyłączony lub brak danych STEADY_STATE.
     */
    @Builder.Default
    private Map<GridPosition, ConditionalStatsDTO> conditionalStatsMap = new HashMap<>();

    /**
     * Obliczony rozstęp przestrzenny i wyniki testów jednorodności pionowej poziomów.
     */
    private SpatialStatsResult spatialStats;

    /**
     * Enum reprezentujący 8 fizycznych narożników (pozycji) komory chłodniczej.
     */
    public enum GridPosition {
        TOP_FRONT_LEFT("Góra - Przód-Lewy"),
        TOP_FRONT_RIGHT("Góra - Przód-Prawy"),
        TOP_BACK_LEFT("Góra - Tył-Lewy"),
        TOP_BACK_RIGHT("Góra - Tył-Prawy"),
        BOTTOM_FRONT_LEFT("Dół - Przód-Lewy"),
        BOTTOM_FRONT_RIGHT("Dół - Przód-Prawy"),
        BOTTOM_BACK_LEFT("Dół - Tył-Lewy"),
        BOTTOM_BACK_RIGHT("Dół - Tył-Prawy");

        private final String label;

        GridPosition(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public boolean isDeviceChannelUsed(String serialNumber, int channelNumber) {
        return assignedPositions.values().stream()
                .anyMatch(pd -> serialNumber.equals(pd.getSerialNumber()) && 
                                pd.getChannelNumber() != null && 
                                pd.getChannelNumber() == channelNumber);
    }

    /**
     * Kontener na dane przypisane do konkretnej pozycji (narożnika) na siatce komory.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionData {
        private String serialNumber;
        private ThermoRecorderModel model;
        @Builder.Default
        private Integer channelNumber = 1;
        private ThermoRecorder recorder; // Rekord z ewidencji w bazie danych
        private Calibration latestCalibration; // Świadectwo wzorcowania z bazy (najnowsze, aktywne)
        private ThermoMeasurementSeries series; // Pełna seria pomiarowa zawierająca punkty (np. 40 próbek)
    }
}
