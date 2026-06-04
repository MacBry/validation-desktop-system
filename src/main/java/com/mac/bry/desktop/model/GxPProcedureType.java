package com.mac.bry.desktop.model;

/**
 * Typ procedury GxP.
 */
public enum GxPProcedureType {
    MAPPING("Mapowanie 5-letnie"),
    PERIODIC_REVALIDATION("Rewalidacja okresowa");

    private final String displayName;

    GxPProcedureType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
