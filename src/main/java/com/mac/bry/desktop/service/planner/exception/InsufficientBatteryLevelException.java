package com.mac.bry.desktop.service.planner.exception;

import com.mac.bry.desktop.model.TaskResourceStatus;

/**
 * W4c — pozostała energia baterii nie pokrywa pełnego czasu misji.
 * <p>
 * Porównanie jest czasowe, nie procentowe: katalogowy budżet dni (skalowany
 * cyklem pomiarowym i stanem naładowania) zestawiamy z czasem od umieszczenia
 * rejestratora w komorze do terminu odczytu, z zapasem bezpieczeństwa.
 */
public class InsufficientBatteryLevelException extends RecorderAllocationException {

    private final double missionDays;
    private final double availableDays;

    public InsufficientBatteryLevelException(String message, double missionDays, double availableDays) {
        super(message, TaskResourceStatus.HARDWARE_LIMITS_EXCEEDED);
        this.missionDays = missionDays;
        this.availableDays = availableDays;
    }

    public double getMissionDays() {
        return missionDays;
    }

    public double getAvailableDays() {
        return availableDays;
    }
}