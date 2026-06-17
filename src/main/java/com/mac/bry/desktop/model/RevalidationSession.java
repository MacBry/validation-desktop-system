package com.mac.bry.desktop.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
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
