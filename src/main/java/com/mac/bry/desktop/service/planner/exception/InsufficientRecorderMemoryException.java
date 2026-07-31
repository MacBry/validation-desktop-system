package com.mac.bry.desktop.service.planner.exception;

import com.mac.bry.desktop.model.TaskResourceStatus;

/**
 * W4b — pamięć rejestratora nie pomieści próbek wymaganych przez procedurę.
 * <p>
 * Celowo <b>nie</b> jest to {@link InsufficientRecorderCapacityException}: tamta
 * opisuje niedobór sztuk sprzętu w oknie czasowym (W2) i niesie propozycję
 * kolejnego wolnego okna. Tutaj czekanie nic nie da — pojemność bufora jest
 * cechą modelu, więc naprawą jest rozrzedzenie interwału albo inny rejestrator.
 */
public class InsufficientRecorderMemoryException extends RecorderAllocationException {

    private final int requiredSamples;
    private final int availableSamples;

    public InsufficientRecorderMemoryException(String message, int requiredSamples, int availableSamples) {
        super(message, TaskResourceStatus.HARDWARE_LIMITS_EXCEEDED);
        this.requiredSamples = requiredSamples;
        this.availableSamples = availableSamples;
    }

    public int getRequiredSamples() {
        return requiredSamples;
    }

    /** Pojemność przypadająca na kanał, a nie katalogowa pojemność modelu. */
    public int getAvailableSamples() {
        return availableSamples;
    }
}