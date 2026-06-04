package com.mac.bry.desktop.model;

/**
 * Klasyfikacja kubatury urządzeń chłodniczych według PDA TR-64 i praktyki WHO.
 * Determinuje minimalne wymagania dotyczące liczby punktów pomiarowych w walidacji przestrzennej.
 */
public enum VolumeCategory {
    SMALL("Klasa S (≤ 2 m³)", "Lodówki apteczne, laboratoryjne, małe inkubatory", 2.0, 9),
    MEDIUM("Klasa M (2–20 m³)", "Szafy chłodnicze walk-in, duże lodówki przemysłowe", 20.0, 15),
    LARGE("Klasa L (> 20 m³)", "Komory chłodnicze, magazyny, chłodnie, kontenery", Double.MAX_VALUE, 27);

    private final String displayName;
    private final String description;
    private final double maxVolume;
    private final int minMeasurementPoints;

    VolumeCategory(String displayName, String description, double maxVolume, int minMeasurementPoints) {
        this.displayName = displayName;
        this.description = description;
        this.maxVolume = maxVolume;
        this.minMeasurementPoints = minMeasurementPoints;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public double getMaxVolume() {
        return maxVolume;
    }

    public int getMinMeasurementPoints() {
        return minMeasurementPoints;
    }

    public static VolumeCategory fromVolume(double volume) {
        if (volume <= SMALL.maxVolume) {
            return SMALL;
        } else if (volume <= MEDIUM.maxVolume) {
            return MEDIUM;
        } else {
            return LARGE;
        }
    }

    public boolean isValidMeasurementPoints(int measurementPoints) {
        return measurementPoints >= this.minMeasurementPoints;
    }

    public String getValidationRequirements() {
        return String.format(
            "%s: %s (min. %d punktów pomiarowych)",
            displayName, description, minMeasurementPoints
        );
    }
}
