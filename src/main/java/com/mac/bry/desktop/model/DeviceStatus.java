package com.mac.bry.desktop.model;

import lombok.Getter;

/**
 * Status urządzenia chłodniczego.
 * Zgodny z wymaganiami GxP dotyczącymi ewidencji aktywów metrologicznych.
 */
@Getter
public enum DeviceStatus {
    ACTIVE("Aktywne"),
    INACTIVE("Nieaktywne"),
    DECOMMISSIONED("Wyłączone z użytku");

    private final String displayName;

    DeviceStatus(String displayName) {
        this.displayName = displayName;
    }
}
