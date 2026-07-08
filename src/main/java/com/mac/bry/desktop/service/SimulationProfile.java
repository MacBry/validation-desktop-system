package com.mac.bry.desktop.service;

/**
 * Profile symulacji metrologicznej do testowania detektorów SPC,
 * Nelson Rules oraz cykli defrostu bez fizycznych rejestratorów.
 */
public enum SimulationProfile {
    STABLE("Standardowa (stabilna)"),
    DRIFT("Z dryftem temperaturowym"),
    SPIKES("Z cyklami defrostu (spiki)"),
    DRIFT_AND_SPIKES("Skumulowana (dryft + defrost)");

    private final String displayName;

    SimulationProfile(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
