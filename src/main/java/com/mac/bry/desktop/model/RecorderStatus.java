package com.mac.bry.desktop.model;

import lombok.Getter;

@Getter
public enum RecorderStatus {
    ACTIVE("Aktywny"),
    INACTIVE("Nieaktywny"),
    UNDER_CALIBRATION("Wysłano do wzorcowania"),
    DECOMMISSIONED("Wyłączone z użytku");

    private final String displayName;

    RecorderStatus(String displayName) {
        this.displayName = displayName;
    }
}
