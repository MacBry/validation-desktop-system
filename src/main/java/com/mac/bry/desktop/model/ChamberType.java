package com.mac.bry.desktop.model;

/**
 * Typ komory urządzenia chłodniczego.
 */
public enum ChamberType {
    FRIDGE("Lodówka"),
    FREEZER("Zamrażarka"),
    LOW_TEMP_FREEZER("Zamrażarka niskotemperaturowa"),
    COLD_ROOM("Chłodnia"),
    FREEZE_ROOM("Mroźnia");

    private final String displayName;

    ChamberType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
